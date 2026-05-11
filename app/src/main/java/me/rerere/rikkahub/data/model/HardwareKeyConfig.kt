package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * 手机硬件桥的 API Key 配置
 *
 * 每种硬件服务对应一个 subclass，统一管理 Key。
 * 新增类型只需添加 new subclass。
 */
@Serializable
sealed class HardwareKeyConfig {
    abstract val id: Uuid
    abstract val keyName: String   // 显示名称
    abstract val apiKey: String    // API Key

    /** 找 Key 的辅助方法 */
    inline fun <reified T : HardwareKeyConfig> matches(): Boolean = this is T

    @Serializable
    @SerialName("amap")
    data class Amap(
        override val id: Uuid = Uuid.random(),
        override val keyName: String = "高德地图",
        override val apiKey: String = "",
    ) : HardwareKeyConfig()

    @Serializable
    @SerialName("openweather")
    data class OpenWeather(
        override val id: Uuid = Uuid.random(),
        override val keyName: String = "OpenWeather",
        override val apiKey: String = "",
    ) : HardwareKeyConfig()

    companion object {
        val KNOWN_TYPES = listOf(
            TypeInfo("amap", "高德地图", Amap::class),
            TypeInfo("openweather", "OpenWeather", OpenWeather::class),
        )

        data class TypeInfo(
            val typeName: String,
            val displayName: String,
            val clazz: kotlin.reflect.KClass<out HardwareKeyConfig>,
        )
    }
}

/** 从硬件 Key 列表中找指定类型的 Key */
inline fun <reified T : HardwareKeyConfig> List<HardwareKeyConfig>.findHardwareKey(): T? {
    return this.filterIsInstance<T>().firstOrNull()
}
