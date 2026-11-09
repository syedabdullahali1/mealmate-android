package com.example.mealmate

import android.content.Context
import com.example.mealmate.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

object TokenStore {
    private const val PREFS = "mealmate_session"
    private const val TOKEN = "access_token"
    fun get(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN, null)
    fun save(context: Context, token: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TOKEN, token).apply()
    fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TOKEN).apply()
}

data class Credentials(val email: String, val password: String, val display_name: String? = null)
data class AuthResponse(val access_token: String, val token_type: String = "bearer", val display_name: String = "")
data class MealCreate(val name: String, val calories: Int, val protein: Int, val carbs: Int, val fat: Int, val meal_type: String)
data class MealItem(
    val id: Int, val name: String, val calories: Int, val protein: Int,
    val carbs: Int, val fat: Int, val meal_type: String, val created_at: String
)
data class DailySummary(val date: String, val calories: Int, val protein: Int, val carbs: Int, val fat: Int, val meal_count: Int)

interface MealMateService {
    @POST("auth/register") suspend fun register(@Body body: Credentials): AuthResponse
    @POST("auth/login") suspend fun login(@Body body: Credentials): AuthResponse
    @GET("meals") suspend fun meals(): List<MealItem>
    @POST("meals") suspend fun addMeal(@Body body: MealCreate): MealItem
    @DELETE("meals/{id}") suspend fun deleteMeal(@Path("id") id: Int)
    @GET("summary") suspend fun summary(): DailySummary
}

class MealMateApi(context: Context) {
    private val appContext = context.applicationContext
    val service: MealMateService = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    TokenStore.get(appContext)?.let { header("Authorization", "Bearer $it") }
                }.build()
                chain.proceed(request)
            })
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MealMateService::class.java)
}
