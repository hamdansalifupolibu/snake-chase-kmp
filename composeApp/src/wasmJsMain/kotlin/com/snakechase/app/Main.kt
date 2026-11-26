package com.snakechase.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.snakechase.app.di.appModule
import com.snakechase.app.di.platformModule
import kotlinx.browser.document
import org.koin.core.context.startKoin

/** Web entry point: the same [App] as Android, desktop and iOS, rendered into the page. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin { modules(appModule, platformModule) }
    ComposeViewport(document.body!!) {
        App()
    }
}
