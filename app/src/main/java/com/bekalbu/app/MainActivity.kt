package com.bekalbu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Green = Color(0xFF287A50)
private val Cream = Color(0xFFF7F8F3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = BekalBuApi(this)
        setContent { BekalBuTheme { BekalBuApp(api) } }
    }
}

@Composable
private fun BekalBuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, onPrimary = Color.White, secondary = Color(0xFFE7A94B), background = Cream, surface = Color.White), content = content)
}

@Composable
private fun BekalBuApp(api: BekalBuApi) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var token by remember { mutableStateOf(TokenStore.get(context)) }
    if (token.isNullOrBlank()) {
        AuthScreen(api) { TokenStore.save(context, it); token = it }
    } else {
        HomeScreen(api) { TokenStore.clear(context); token = null }
    }
}

@Composable
private fun AuthScreen(api: BekalBuApi, onAuthenticated: (String) -> Unit) {
    var isRegister by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🥗", fontSize = 48.sp)
            Text("BekalBu", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Green)
            Text("Eat well. Feel great.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isRegister) OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Password (8+ characters)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                error = null
                if (email.isBlank() || password.isBlank() || (isRegister && name.isBlank())) { error = "Please complete all fields."; return@Button }
                busy = true
                scope.launch {
                    try {
                        val auth = if (isRegister) api.service.register(Credentials(email.trim(), password, name.trim())) else api.service.login(Credentials(email.trim(), password))
                        onAuthenticated(auth.access_token)
                    } catch (e: Exception) { error = e.message ?: "Unable to connect. Check that the BekalBu API is running." }
                    finally { busy = false }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (isRegister) "Create account" else "Sign in")
            }
            TextButton(onClick = { isRegister = !isRegister; error = null }) {
                Text(if (isRegister) "Already have an account? Sign in" else "New to BekalBu? Create an account")
            }
        }
    }
}

@Composable
private fun HomeScreen(api: BekalBuApi, onLogout: () -> Unit) {
    var meals by remember { mutableStateOf<List<MealItem>>(emptyList()) }
    var summary by remember { mutableStateOf(DailySummary("", 0, 0, 0, 0, 0)) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true
            try { meals = api.service.meals(); summary = api.service.summary(); error = null }
            catch (e: Exception) { error = "Couldn't load your meals. Check the API connection." }
            finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Scaffold(containerColor = Cream, topBar = {
        TopAppBar(title = { Text("BekalBu", fontWeight = FontWeight.Bold, color = Green) }, actions = { TextButton(onClick = onLogout) { Text("Sign out") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Your daily dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("CALORIES TODAY", color = Color.White.copy(alpha = .8f))
                    Text("${summary.calories} kcal", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("Protein ${summary.protein}g · Carbs ${summary.carbs}g · Fat ${summary.fat}g", color = Color.White)
                }
            }
            Text("Today's meals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            else if (meals.isEmpty()) Text("No meals yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(meals, key = { it.id }) { meal ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text(meal.name, fontWeight = FontWeight.SemiBold); Text("${meal.meal_type} · ${meal.calories} kcal") }
                            TextButton(onClick = { scope.launch { try { api.service.deleteMeal(meal.id); refresh() } catch (_: Exception) { error = "Couldn't delete meal." } } }) { Text("Delete") }
                        }
                    }
                }
            }
            Text("Nutrition insight", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(NutritionAnalyzer.analyze(summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { refresh() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Refresh") }
        }
    }
}
