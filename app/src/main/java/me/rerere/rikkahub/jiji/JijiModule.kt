package me.rerere.rikkahub.jiji

import me.rerere.rikkahub.data.ai.tools.WeatherFetcher
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.perception.PerceptionStore
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

    single { PerceptionStore(get()) }

    single {
        PerceptionManager(
            weatherFetcher = get(),
            locationProvider = get(),
            perceptionStore = get(),
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
