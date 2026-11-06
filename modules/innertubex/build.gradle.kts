// Vendored from https://github.com/MetrolistGroup/innertubex (see VENDORED.md).
// Build script rewritten for Kreate: Kreate's catalog/plugins, AGP 8.x, Android + JVM targets only.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "com.metrolist.innertubex"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }

    jvm("desktop") {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
        }
        commonMain.dependencies {
            api(libs.ktor.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines)
            implementation(libs.quickjs)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }
        val jvmCommonMain = create("jvmCommonMain") { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmCommonMain)
        getByName("desktopMain").dependsOn(jvmCommonMain)
        getByName("desktopTest").dependencies {
            implementation(libs.junit4)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
