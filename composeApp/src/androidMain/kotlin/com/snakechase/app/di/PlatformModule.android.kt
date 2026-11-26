package com.snakechase.app.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import com.snakechase.app.platform.AudioManager
import com.snakechase.app.platform.HapticsManager
import com.snakechase.app.platform.ShareService
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<Settings> {
        val sharedPrefs = androidContext().getSharedPreferences("snake_chase_prefs", Context.MODE_PRIVATE)
        SharedPreferencesSettings(sharedPrefs)
    }
    single { AudioManager(androidContext()) }
    single { HapticsManager(androidContext()) }
    single { ShareService(androidContext()) }
}
