package com.snakechase.app

import androidx.compose.ui.window.ComposeUIViewController
import com.snakechase.app.di.appModule
import com.snakechase.app.di.platformModule
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

/** iOS entry point. Its name is fixed by the Swift side (`MainViewControllerKt.MainViewController()`). */
@Suppress("ktlint:standard:function-naming")
fun MainViewController(): UIViewController {
    try {
        startKoin {
            modules(appModule, platformModule)
        }
    } catch (_: Exception) {
        // Koin already started
    }

    return ComposeUIViewController {
        App()
    }
}
