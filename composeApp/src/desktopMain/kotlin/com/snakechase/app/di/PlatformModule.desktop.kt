package com.snakechase.app.di

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import com.snakechase.app.platform.AudioManager
import com.snakechase.app.platform.HapticsManager
import com.snakechase.app.platform.ShareService
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.prefs.Preferences

actual val platformModule: Module = module {
    single<Settings> {
        PreferencesSettings(Preferences.userRoot().node("snake_chase"))
    }
    single { AudioManager() }
    single { HapticsManager() }
    single { ShareService() }
}
