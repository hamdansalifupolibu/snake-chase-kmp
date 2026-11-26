package com.snakechase.app.di

import com.snakechase.app.platform.AudioManager
import com.snakechase.app.platform.HapticsManager
import com.snakechase.app.platform.ShareService
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { AudioManager() }
    single { HapticsManager() }
    single { ShareService() }
}
