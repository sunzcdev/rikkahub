package me.rerere.rikkahub.data.ai.tools

import me.rerere.common.android.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.HardwareKeyConfig
import me.rerere.rikkahub.data.model.findHardwareKey
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * OpenWeatherMap 天气获取
 *
 * 可被 Jiji 感知层和 AI 聊天 Tool 复用
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
     * 获取指定坐标的天气（推荐方式，city name 方式已弃用）
     * @param lat 纬度
     * @param lon 经度
     * @param apiKey OpenWeatherMap API Key
     */
    suspend fun fetchWeather(lat: Double, lon: Double, apiKey: String): WeatherResult? {
        return try {
            val url = "$BASE_URL?lat=$lat&lon=$lon&appid=$apiKey&units=metric&lang=zh_cn"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "no body"
                throw Exception("OpenWeather API ${response.code}: $errBody")
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
            Logging.e(TAG, "Failed to fetch weather by coords", e)
            throw Exception("天气API请求异常: ${e.message}")
        }
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

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (!response.isSuccessful) {
                Logging.w(TAG, "Weather API error: ${response.code} ${response.message}")
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
            Logging.e(TAG, "Failed to fetch weather", e)
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

/**
 * 天气工具类——提供给 AI 聊天的 get_weather Tool
 */
class WeatherTool(
    private val weatherFetcher: WeatherFetcher,
    private val getHardwareKeys: () -> List<HardwareKeyConfig>,
) {
    val tool: Tool by lazy {
        Tool(
            name = "get_weather",
            description = "Get current weather conditions for a specified city using OpenWeatherMap. Returns temperature, condition (sunny/rainy/cloudy), humidity, and description in Chinese.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("city", buildJsonObject {
                            put("type", "string")
                            put("description", "City name in Chinese or English (e.g. '北京', 'Shanghai')")
                        })
                    },
                    required = listOf("city")
                )
            },
            execute = { args ->
                val params = args.jsonObject
                val cityElement = params["city"]?.jsonPrimitive
                val city = cityElement?.content
                if (city.isNullOrBlank()) {
                    return@Tool listOf(UIMessagePart.Text(
                        buildJsonObject {
                            put("error", true)
                            put("message", "City name is required")
                        }.toString()
                    ))
                }

                val apiKey = getHardwareKeys().findHardwareKey<HardwareKeyConfig.OpenWeather>()?.apiKey
                if (apiKey.isNullOrBlank()) {
                    return@Tool listOf(UIMessagePart.Text(
                        buildJsonObject {
                            put("error", true)
                            put("message", "OpenWeatherMap API Key is not configured")
                        }.toString()
                    ))
                }

                val result = weatherFetcher.fetchWeather(city, apiKey)
                if (result == null) {
                    return@Tool listOf(UIMessagePart.Text(
                        buildJsonObject {
                            put("error", true)
                            put("message", "Failed to fetch weather for '$city'. Check city name and try again.")
                        }.toString()
                    ))
                }

                listOf(UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("city", result.cityName)
                        put("condition", result.condition)
                        put("description", result.description)
                        put("temperature", result.temperature)
                        put("humidity", result.humidity)
                    }.toString()
                ))
            }
        )
    }
}
