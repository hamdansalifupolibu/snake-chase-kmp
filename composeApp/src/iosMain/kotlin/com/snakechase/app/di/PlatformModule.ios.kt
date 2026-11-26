package com.snakechase.app.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import com.snakechase.app.platform.AudioManager
import com.snakechase.app.platform.HapticsManager
import com.snakechase.app.platform.ShareService
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual val platformModule: Module = module {
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
    single { AudioManager() }
    single { HapticsManager() }
    single { ShareService() }
}
