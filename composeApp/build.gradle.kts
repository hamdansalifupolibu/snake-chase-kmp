import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktlint)
}

// Kotlin coding conventions, enforced: `./gradlew ktlintCheck` (CI) / `./gradlew ktlintFormat` (fix).
// Rules come from /.editorconfig.
ktlint {
    version.set("1.4.1")
    filter {
        exclude { it.file.path.contains("generated") || it.file.path.contains("${File.separator}build${File.separator}") }
    }
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm("desktop")

    // Web: Compose for Kotlin/Wasm. The browser plays online (Firebase) rooms, quick match and
    // ghost duels with the same shared code as the phone and desktop apps.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    // Extends the default hierarchy with a source set shared by the two JVM-based
    // targets, so code that needs java.net (the embedded duel server) is written once.
    // Resulting tree: commonMain → { jvmAndAndroidMain → {androidMain, desktopMain}, iosMain → ios*, wasmJsMain }
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Serialization & DateTime
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            // Koin Dependency Injection
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Ktor Client
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)

            // Multiplatform Settings
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)

            // Lifecycle
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.java)
            // MP3 decoding for Java Sound (desktop plays the same audio files as the other platforms).
            implementation(libs.mp3spi)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }

        // Headless Compose UI tests: drive the real screens with clicks and typing.
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "com.snakechase.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.snakechase.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

compose.desktop {
    application {
        mainClass = "com.snakechase.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Snake Chase"
            packageVersion = "1.0.0"
            description = "A 5-level survival game with intelligent AI predators"
            copyright = "© 2026 Snake Chase"

            windows {
                menuGroup = "Snake Chase"
                upgradeUuid = "8A2B3C4D-5E6F-7081-92A3-B4C5D6E7F801"
                dirChooser = true
            }
        }
    }
}
