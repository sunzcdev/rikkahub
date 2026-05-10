package me.rerere.rikkahub.jiji

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * OpenWeatherMap 天气获取
 *
 * 每天只调用 1 次，免费额度（100万次/天）完全够用
 */
class WeatherFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    companion object {
        private const val TAG = "WeatherFetcher"
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"
    }

    /**
     * 获取指定城市的天气
     * @param city 城市名（中文或英文）
     * @param apiKey OpenWeatherMap API Key
     */
    suspend fun fetchWeather(city: String, apiKey: String): WeatherResult? {
        return try {
            val url = "$BASE_URL?q=$city&appid=$apiKey&units=metric&lang=zh_cn"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Weather API error: ${response.code} ${response.message}")
                return null
            }

            val body = response.body?.string() ?: return null
            val dto = json.decodeFromString<OpenWeatherResponse>(body)
            WeatherResult(
                condition = dto.weather.firstOrNull()?.main ?: "Unknown",
                description = dto.weather.firstOrNull()?.description ?: "",
                temperature = (dto.main.temp).toInt(),
                humidity = dto.main.humidity,
                cityName = dto.name,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch weather", e)
            null
        }
    }
}

data class WeatherResult(
    val condition: String,
    val description: String,
    val temperature: Int,
    val humidity: Int,
    val cityName: String,
)

@Serializable
private data class OpenWeatherResponse(
    val weather: List<WeatherEntry> = emptyList(),
    val main: MainEntry,
    val name: String = "",
)

@Serializable
private data class WeatherEntry(
    val main: String = "",
    val description: String = "",
)

@Serializable
private data class MainEntry(
    val temp: Double = 0.0,
    val humidity: Int = 0,
)
