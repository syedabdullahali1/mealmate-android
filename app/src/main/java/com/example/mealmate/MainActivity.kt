package com.example.mealmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RestaurantMenu
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
        val api = MealMateApi(this)
        setContent { MealMateTheme { MealMateApp(api) } }
    }
}

@Composable
private fun MealMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Green, onPrimary = Color.White, secondary = Color(0xFFE7A94B), background = Cream, surface = Color.White),
        content = content
    )
}

@Composable
private fun MealMateApp(api: MealMateApi) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var token by remember { mutableStateOf(TokenStore.get(context)) }
    if (token.isNullOrBlank()) {
        AuthScreen(api = api, onAuthenticated = { TokenStore.save(context, it); token = it })
    } else {
        HomeScreen(api = api, onLogout = { TokenStore.clear(context); token = null })
    }
}

@Composable
private fun AuthScreen(api: MealMateApi, onAuthenticated: (String) -> Unit) {
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
            Spacer(Modifier.height(8.dp))
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

private enum class Tab { HOME, DIARY, TRACKER }

@Composable
private fun HomeScreen(api: MealMateApi, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var meals by remember { mutableStateOf<List<MealItem>>(emptyList()) }
    var summary by remember { mutableStateOf(DailySummary("", 0, 0, 0, 0, 0)) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
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

    Scaffold(
        containerColor = Cream,
        topBar = { TopAppBar(
            title = { Text("BekalBu", fontWeight = FontWeight.Bold, color = Green) },
            actions = { TextButton(onClick = onLogout) { Text("Sign out") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
        ) },
        bottomBar = { NavigationBar(containerColor = Color.White) {
            NavigationBarItem(selected = tab == Tab.HOME, onClick = { tab = Tab.HOME }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
            NavigationBarItem(selected = tab == Tab.DIARY, onClick = { tab = Tab.DIARY }, icon = { Icon(Icons.Default.RestaurantMenu, null) }, label = { Text("Diary") })
            NavigationBarItem(selected = tab == Tab.TRACKER, onClick = { tab = Tab.TRACKER }, icon = { Icon(Icons.Default.Analytics, null) }, label = { Text("Tracker") })
        } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            when (tab) {
                Tab.HOME -> Dashboard(summary, meals, loading, onDiary = { tab = Tab.DIARY })
                Tab.DIARY -> DiaryScreen(meals, loading, onAdd = { meal ->
                    scope.launch {
                        try { api.service.addMeal(meal); refresh() }
                        catch (e: Exception) { error = "Couldn't save meal. Check the API connection." }
                    }
                }, onDelete = { id ->
                    scope.launch { try { api.service.deleteMeal(id); refresh() } catch (_: Exception) { error = "Couldn't delete meal." } }
                })
                Tab.TRACKER -> TrackerScreen(summary)
            }
        }
    }
}

@Composable
private fun Dashboard(summary: DailySummary, meals: List<MealItem>, loading: Boolean, onDiary: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your daily dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Small choices add up to big changes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        CalorieCard(summary.calories)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MacroCard("Protein", summary.protein, "g", Modifier.weight(1f))
            MacroCard("Carbs", summary.carbs, "g", Modifier.weight(1f))
            MacroCard("Fat", summary.fat, "g", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Today's meals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDiary) { Text("View diary") }
        }
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        else if (meals.isEmpty()) Text("No meals yet. Add your first meal in the diary.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else meals.take(3).forEach { MealRow(it) }
    }
}

@Composable
private fun CalorieCard(calories: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("CALORIES TODAY", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom) { Text("$calories", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold); Text(" kcal", color = Color.White, modifier = Modifier.padding(bottom = 6.dp)) }
            LinearProgressIndicator(progress = { (calories / 2000f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Color(0xFFFFCF78), trackColor = Color.White.copy(alpha = .25f))
            Text("Daily reference: 2,000 kcal", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MacroCard(title: String, value: Int, unit: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) { Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("$value$unit", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
    }
}

@Composable
private fun DiaryScreen(meals: List<MealItem>, loading: Boolean, onAdd: (MealCreate) -> Unit, onDelete: (Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf("Lunch") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Meal diary", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add a meal", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(name, { name = it }, label = { Text("Meal name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInput("Calories", calories, { calories = it }, Modifier.weight(1f))
                    NumberInput("Protein g", protein, { protein = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInput("Carbs g", carbs, { carbs = it }, Modifier.weight(1f))
                    NumberInput("Fat g", fat, { fat = it }, Modifier.weight(1f))
                }
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text("Meal type: $mealType") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("Breakfast", "Lunch", "Dinner", "Snack").forEach { type ->
                            DropdownMenuItem(text = { Text(type) }, onClick = { mealType = type; expanded = false })
                        }
                    }
                }
                Button(onClick = {
                    if (name.isNotBlank() && (calories.toIntOrNull() ?: -1) >= 0) {
                        onAdd(MealCreate(name.trim(), calories.toInt(), protein.toIntOrNull() ?: 0, carbs.toIntOrNull() ?: 0, fat.toIntOrNull() ?: 0, mealType))
                        name = ""; calories = ""; protein = ""; carbs = ""; fat = ""
                    }
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Save meal") }
            }
        }
        Text("Today's entries", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        else if (meals.isEmpty()) Text("Your diary is empty. Add a meal above.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(meals, key = { it.id }) { meal ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(meal.name, fontWeight = FontWeight.SemiBold); Text("${meal.meal_type} · ${meal.calories} kcal · P ${meal.protein}g · C ${meal.carbs}g · F ${meal.fat}g", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = { onDelete(meal.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberInput(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value, { text -> onChange(text.filter(Char::isDigit).take(6)) }, label = { Text(label) }, singleLine = true, modifier = modifier)
}

@Composable
private fun MealRow(meal: MealItem) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text(meal.name, fontWeight = FontWeight.SemiBold); Text(meal.meal_type, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            Text("${meal.calories} kcal", color = Green, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TrackerScreen(summary: DailySummary) {
    val insight = remember(summary) { NutritionAnalyzer.analyze(summary) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Calorie & nutrition tracker", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        CalorieCard(summary.calories)
        Text("Today's nutrition", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        listOf("Protein" to summary.protein, "Carbohydrates" to summary.carbs, "Fat" to summary.fat).forEach { (label, amount) ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontWeight = FontWeight.SemiBold); Text("${amount} g") }
                    LinearProgressIndicator(progress = { (amount / 120f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3E9)), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BekalBu nutrition insight", fontWeight = FontWeight.Bold, color = Green)
                Text(insight)
                Text("General wellness guidance only; not medical advice.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
