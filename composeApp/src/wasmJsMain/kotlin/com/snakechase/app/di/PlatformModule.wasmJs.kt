package com.snakechase.app.di

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import com.snakechase.app.platform.AudioManager
import com.snakechase.app.platform.HapticsManager
import com.snakechase.app.platform.ShareService
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // Browser localStorage: settings, best scores and the anonymous Firebase session persist per site.
    single<Settings> { StorageSettings() }
    single { AudioManager() }
    single { HapticsManager() }
    single { ShareService() }
}
