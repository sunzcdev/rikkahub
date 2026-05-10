package me.rerere.rikkahub.jiji

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 唧唧的位置提供器
 *
 * 复用 PhoneBridge 中的高德地图（Amap）定位方案，
 * 返回简化的 LocationInfo，用于唧唧的感知上下文。
 *
 * 定位策略：高精度单次定位，15分钟缓存一次
 */
class JijiLocationProvider(
    private val context: Context,
    private val getAmapApiKey: () -> String?,
) {
    companion object {
        private const val TAG = "JijiLocationProvider"
        private const val LOCATION_CACHE_TTL_MS = 15L * 60 * 1000 // 15分钟
    }

    private var cachedLocation: LocationInfo? = null
    private var cachedTime: Long = 0L

    /**
     * 获取当前位置（优先返回缓存）
     */
    suspend fun getLocation(): LocationInfo? {
        val now = System.currentTimeMillis()

        // 15分钟缓存有效
        if (cachedLocation != null && (now - cachedTime) < LOCATION_CACHE_TTL_MS) {
            Log.d(TAG, "Returning cached location: ${cachedLocation?.city}")
            return cachedLocation
        }

        // 权限检查
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "ACCESS_FINE_LOCATION not granted")
            return null
        }

        // API Key 检查
        val apiKey = getAmapApiKey()
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "Amap API key not configured")
            return null
        }

        // 用高德定位
        val result = requestAmapLocation(apiKey)

        if (result != null) {
            cachedLocation = result
            cachedTime = now
        }
        return result
    }

    /**
     * 清除缓存（强制刷新）
     */
    fun clearCache() {
        cachedLocation = null
        cachedTime = 0L
    }

    private suspend fun requestAmapLocation(apiKey: String): LocationInfo? {
        return try {
            AMapLocationClient.setApiKey(apiKey)
            AMapLocationClient.updatePrivacyShow(context, true, true)
            AMapLocationClient.updatePrivacyAgree(context, true)

            suspendCancellableCoroutine { continuation ->
                val client = AMapLocationClient(context)
                val option = AMapLocationClientOption().apply {
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    isOnceLocation = true
                    isNeedAddress = true
                    httpTimeOut = 10000
                    isLocationCacheEnable = true
                }
                client.setLocationOption(option)
                client.setLocationListener { loc ->
                    val info = if (loc != null && loc.errorCode == 0) {
                        LocationInfo(
                            city = loc.city ?: loc.province ?: "未知",
                            district = loc.district,
                        )
                    } else {
                        null
                    }
                    continuation.resume(info)
                    client.stopLocation()
                    client.onDestroy()
                }
                client.startLocation()

                continuation.invokeOnCancellation {
                    client.stopLocation()
                    client.onDestroy()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Amap location failed", e)
            null
        }
    }
}
