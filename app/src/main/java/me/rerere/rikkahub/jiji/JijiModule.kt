package me.rerere.rikkahub.jiji

import me.rerere.rikkahub.data.ai.tools.WeatherFetcher
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.koin.dsl.module

/**
 * 唧唧的依赖注入模块
 */
val jijiModule = module {
    single { JijiConfigStore(get()) }

    single { BaselineManager() }

    single {
        DeviationDetector(
            baselineManager = get(),
        )
    }

    single {
        JijiLocationProvider(
            context = get(),
            getHardwareKeys = { get<SettingsStore>().settingsFlow.value.hardwareKeys },
        )
    }

    single {
        PerceptionManager(
            weatherFetcher = get(),
            locationProvider = get(),
            getHardwareKeys = { get<SettingsStore>().settingsFlow.value.hardwareKeys },
        )
    }

    single {
        JijiNotificationManager(
            context = get(),
        )
    }

    single {
        JijiIntegration(
            context = get(),
            settingsStore = get(),
            jijiConfigStore = get(),
            jijiNotificationManager = get(),
        )
    }
}
