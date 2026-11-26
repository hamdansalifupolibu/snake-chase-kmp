package com.snakechase.app

import android.app.Application
import com.snakechase.app.di.appModule
import com.snakechase.app.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SnakeChaseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SnakeChaseApp)
            modules(appModule, platformModule)
        }
    }
}
