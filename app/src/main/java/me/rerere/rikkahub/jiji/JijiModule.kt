package me.rerere.rikkahub.jiji

import me.rerere.rikkahub.data.datastore.SettingsStore
import org.koin.dsl.module

/**
 * 唧唧的依赖注入模块
 */
val jijiModule = module {
    single { JijiConfigStore(get()) }

    single { WeatherFetcher() }

    single { BaselineManager() }

    single {
        DeviationDetector(
            baselineManager = get(),
        )
    }

    single {
        JijiLocationProvider(
            context = get(),
            getAmapApiKey = { get<SettingsStore>().settingsFlow.value.amapApiKey }
        )
    }

    single {
        PerceptionManager(
            weatherFetcher = get(),
            locationProvider = get(),
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
