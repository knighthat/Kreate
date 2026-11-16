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
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries.android)
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

        // Translations are merged into src/crowdin/res/ before each release (crowdin.yml), a
        // resource dir of foss and github only. This only counts the supported languages: English
        // and every folder with texts, "values-pt-rBR" as "pt-BR", "values-b+sr+Latn" as "sr-Latn"
        val languages = file("src/crowdin/res")
            .listFiles { dir ->
                dir.name.startsWith("values-")
                        && dir.listFiles { f -> f.name.endsWith(".strings.xml") }.orEmpty()
                    .isNotEmpty()
            }
            .orEmpty()
            //noinspection WrongGradleMethod
            .map { dir ->
                val qualifier = dir.name.removePrefix("values-")
                if (qualifier.startsWith("b+"))
                    qualifier.removePrefix("b+").replace('+', '-')
                else
                    qualifier.replace("-r", "-")
            }
        buildConfigField(
            "String",
            "LANGUAGES",
            "\"${(listOf("en") + languages.sorted()).joinToString(",")}\""
        )

        // Kreate's app on Discord's developer portal, a public client: the id isn't a secret.
        // The redirect is set on the portal too, and in the manifest's DiscordCallbackActivity
        buildConfigField("String", "DISCORD_APP_ID", "\"1370148610158759966\"")
        buildConfigField("String", "DISCORD_REDIRECT_URI", "\"kreate3://discord/callback\"")
        // Where Discord's "Get Kreate" button leads, the Patreon build has its own
        buildConfigField("String", "HOMEPAGE_URL", "\"https://github.com/knighthat/Kreate\"")
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
            //noinspection WrongGradleMethod
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
            proguardFiles("proguard-dontobfuscate.pro")
            versionNameSuffix = "+$name"
            buildConfigField("boolean", "NON_FREE_NET", "true")
        }
        // F-Droid standards: no telemetry, no proprietary libraries (see verifyFossDependencies),
        // no non-free network services (Discord, Shazam: src/nonfree stays out)
        create("foss") {
            dimension = "platform"
            proguardFiles("proguard-dontobfuscate.pro")
            versionNameSuffix = "+$name"
            buildConfigField("boolean", "NON_FREE_NET", "false")
        }
        // Obfuscated, the release pipeline ships R8's mapping with the APKs to read stack traces
        create("patreon") {
            dimension = "platform"
            versionNameSuffix = "+$name"
            buildConfigField("String", "HOMEPAGE_URL", "\"https://patreon.com/knighthat\"")
            buildConfigField("boolean", "NON_FREE_NET", "true")
            // Has no translations in its APK (src/crowdin stays out), its others come from Crowdin's
            // distribution (src/patreon's TranslationUpdates)
            buildConfigField("String", "LANGUAGES", "\"en\"")
            // The Crowdin distribution those come from: the environment's CROWDIN_DISTRIBUTION_HASH
            // (CI), else the same key in local.properties (local builds); a build with neither has
            // English only
            val localProperties = rootProject.layout.projectDirectory.file("local.properties")
            val crowdinHash = providers.environmentVariable("CROWDIN_DISTRIBUTION_HASH")
                .orElse(
                    providers.fileContents(localProperties)
                        .asText
                        .map { text ->
                            Properties()
                                .apply { load(text.reader()) }
                                .getProperty("CROWDIN_DISTRIBUTION_HASH")
                                .orEmpty()
                        }
                )
                .orNull
                .orEmpty()
                .trim()
            require(crowdinHash.matches(Regex("[0-9a-z]*"))) {
                "CROWDIN_DISTRIBUTION_HASH isn't a hash"
            }
            buildConfigField("String", "CROWDIN_DISTRIBUTION_HASH", "\"$crowdinHash\"")
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

    // What talks to non-free network services (Discord's presence, Shazam's recognition), in every
    // platform but foss, which has its own src/foss in their place. Crowdin's texts translated after
    // the build are patreon's own (src/patreon). Stays after
    // productFlavors:
    // a flavor's source set exists only once the flavor does ("AndroidSourceSet with name … not
    // found" otherwise)
    sourceSets {
        listOf("github", "patreon").forEach {
            getByName(it).kotlin.directories.add("src/nonfree/kotlin")
        }
        // The translations Crowdin merges, with a TranslationUpdates that keeps them; patreon ships
        // none and gets its texts from the Crowdin SDK instead (src/patreon)
        listOf("foss", "github").forEach {
            getByName(it).res.directories.add("src/crowdin/res")
            getByName(it).kotlin.directories.add("src/crowdin/kotlin")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    // F-Droid rejects the signed dependency blob AGP adds to APKs by default
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Translations come from Crowdin partly done, Android shows the English text in place of a
    // missing one
    lint.warning += "MissingTranslation"
    // The system's per-app language settings list the same translations (needs res/resources.properties)
    androidResources.generateLocaleConfig = true
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

// Groups that are closed source or phone home, none may reach the foss flavor
val proprietaryGroups = listOf(
    "com.google.android.gms",
    "com.google.android.play",
    "com.google.firebase",
    "com.google.mlkit",
    "com.google.android.datatransport",
    "com.android.billingclient",
    "com.google.android.ump",
    "com.crashlytics",
    "io.sentry",
    "com.facebook",
    "com.appsflyer",
    "com.amplitude",
    "com.mixpanel",
    "com.bugsnag",
    "com.microsoft.appcenter",
)

androidComponents {
    // Flavors only change packaging, so tests run on one of them
    beforeVariants { variant ->
        if (variant.productFlavors.toMap() != mapOf("abi" to "universal", "platform" to "github")) {
            variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
            variant.deviceTests[DeviceTestBuilder.ANDROID_TEST_TYPE]?.enable = false
        }
    }

    onVariants { variant ->
        if (variant.productFlavors.toMap()["platform"] == "foss") {
            val runtime = variant.runtimeConfiguration
            val verify =
                tasks.register("verify${variant.name.replaceFirstChar { it.uppercase() }}Dependencies") {
                    group = "verification"
                    description = "Fails when ${variant.name} pulls in a proprietary library"
                    doLast {
                        val found = runtime.incoming.resolutionResult.allComponents
                            .mapNotNull { it.moduleVersion }
                            .filter { m -> proprietaryGroups.any { m.group.startsWith(it) } }
                            .map { "${it.group}:${it.name}:${it.version}" }
                        check(found.isEmpty()) { "foss must not depend on: $found" }
                    }
                }
            tasks.matching { it.name == "pre${variant.name.replaceFirstChar { c -> c.uppercase() }}Build" }
                .configureEach { dependsOn(verify) }
        }
        val abi = variant.productFlavors.toMap().getValue("abi")
        // The public API has no way to rename an APK yet
        variant.outputs.forEach { (it as VariantOutputImpl).outputFileName.set("kreate-$abi.apk") }
    }
}

aboutLibraries {
    // Fixes for libraries whose POM lacks details, InnerTubeX's (JitPack) names no SPDX license
    collect.configPath = file("aboutlibraries")
    // Only what the open source licenses page shows goes in the APK
    export.excludeFields.addAll("description", "funding", "scm")
}

dependencies {
    implementation(project(":model"))
    implementation(project(":database"))
    implementation(project(":preferences"))
    implementation(project(":provider"))
    implementation(project(":lyrics"))
    // Non-free network services, see src/nonfree
    "githubImplementation"(project(":discord"))
    "githubImplementation"(project(":shazam"))
    "patreonImplementation"(project(":discord"))
    "patreonImplementation"(project(":shazam"))
    // Texts translated after the build, see src/patreon's TranslationUpdates
    "patreonImplementation"(libs.crowdin.sdk)

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
    implementation(libs.androidx.compose.material3.navigation.suite)
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
    implementation(libs.vico.compose)
    implementation(libs.aboutlibraries.core)

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
    implementation(libs.kermit)
    implementation(libs.kotlin.csv)
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
