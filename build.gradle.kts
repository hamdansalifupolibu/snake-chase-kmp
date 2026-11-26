plugins {
    alias(libs.plugins.androidApplication).apply(false)
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.composeMultiplatform).apply(false)
    alias(libs.plugins.composeCompiler).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    alias(libs.plugins.kotlinSerialization).apply(false)
}

// Optional, per machine: put build output outside a cloud-synced folder (OneDrive, Dropbox…),
// whose syncing and file locks slow builds down and break them. Set in ~/.gradle/gradle.properties:
//   snakechase.buildRoot=C:/gbuild/snakechase
// Without it, everything builds into the usual build/ folders.
providers.gradleProperty("snakechase.buildRoot").orNull?.let { root ->
    allprojects {
        layout.buildDirectory.set(file("$root/${path.replace(':', '_').ifEmpty { "_root" }}"))
    }
}
