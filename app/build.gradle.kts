/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.impl.VariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.knighthat.kreate3"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = android.namespace
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Credentials come from the environment, never from files in the repo
    val productionStorePassword = providers.environmentVariable("PRODUCTION_STORE_PASSWORD")
    val productionKeyPassword = providers.environmentVariable("PRODUCTION_KEY_PASSWORD")

    signingConfigs {
        create("production") {
            storeFile = rootProject.file(".ignore.d/keystores/production.jks")
            keyAlias = "kreate"
            storePassword = productionStorePassword.orNull
            keyPassword = productionKeyPassword.orNull
        }
    }

    buildTypes {
        release {
            // Without the credentials, release builds stay unsigned
            if (listOf(productionStorePassword, productionKeyPassword).all { it.isPresent })
                signingConfig = signingConfigs.getByName("production")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += listOf("platform", "abi")
    productFlavors {
        create("github") {
            dimension = "platform"
            isDefault = true
        }
        create("fdroid") {
            dimension = "platform"
        }
        create("patreon") {
            dimension = "platform"
        }

        // Each one ships the native libraries of a single CPU architecture. Stores like F-Droid
        // need a different version code per APK, so the last digit tells the architecture,
        // universal being 0 so an APK made for the device always wins over it
        val baseVersionCode = libs.versions.versionCode.get().toInt() * 10
        listOf(
            "armv7" to "armeabi-v7a",
            "arm64" to "arm64-v8a",
            "x86" to "x86",
            "x86_64" to "x86_64",
        ).forEachIndexed { index, (name, abi) ->
            create(name) {
                dimension = "abi"
                //noinspection ChromeOsAbiSupport
                ndk.abiFilters += abi
                versionCode = baseVersionCode + index + 1
            }
        }
        // Ships every architecture
        create("universal") {
            dimension = "abi"
            isDefault = true
            versionCode = baseVersionCode
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    packaging.resources.excludes += listOf(
        "/META-INF/*.version",
        "/META-INF/*.kotlin_module",
        "/META-INF/**/*.kotlin_builtins",
        "/META-INF/{AL2.0,LGPL2.1}",
        "/META-INF/LICENSE*",
        "/META-INF/NOTICE*",
        "/kotlin/**",
        "/DebugProbesKt.bin",
    )

    testOptions.unitTests.isIncludeAndroidResources = true
    testOptions.unitTests.all {
        // Robolectric sets up Android's file descriptors through JDK internals
        it.jvmArgs(
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
        )
    }
}

androidComponents {
    // Flavors only change packaging, so tests run on one of them
    beforeVariants { variant ->
        if (variant.productFlavors.toMap() != mapOf("abi" to "universal", "platform" to "github")) {
            variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
            variant.deviceTests[DeviceTestBuilder.ANDROID_TEST_TYPE]?.enable = false
        }
    }

    onVariants { variant ->
        val abi = variant.productFlavors.toMap().getValue("abi")
        // The public API has no way to rename an APK yet
        variant.outputs.forEach { (it as VariantOutputImpl).outputFileName.set("kreate-$abi.apk") }
    }
}

dependencies {
    implementation(project(":model"))
    implementation(project(":database"))
    implementation(project(":preferences"))
    implementation(project(":provider"))
    implementation(project(":lyrics"))

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // The proxy of the YouTube sign in page
    implementation(libs.androidx.webkit)

    // Compose
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.material3)
    implementation(libs.reorderable)

    // Navigation
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Images, remote ones go through the Ktor client of their source
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    // Playback, remote audio goes through the OkHttp client of its source
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)

    // Theming
    implementation(libs.material.kolor)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Tooling
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
