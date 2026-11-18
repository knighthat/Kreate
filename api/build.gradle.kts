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

import java.util.Properties

// Requests to the project's own API: signing in with Patreon and keeping the user signed in.
// Knows nothing of the app, which hands it a client and keeps the tokens
plugins {
    alias(libs.plugins.android.library)
}

// Where the API is: the environment's KREATE_API_URL (CI), else the same key in local.properties
// (local builds). A build with neither has no API, its requests fail as if offline
val apiUrl: String = run {
    val localProperties = rootProject.layout.projectDirectory.file("local.properties")
    providers.environmentVariable("KREATE_API_URL")
        .orElse(
            providers.fileContents(localProperties)
                .asText
                .map { text ->
                    Properties()
                        .apply { load(text.reader()) }
                        .getProperty("KREATE_API_URL")
                        .orEmpty()
                }
        )
        .orNull
        .orEmpty()
        .trim()
        .trimEnd('/')
}
require(apiUrl.isEmpty() || apiUrl.matches(Regex("https://[A-Za-z0-9.-]+(:[0-9]+)?(/[A-Za-z0-9._~/-]*)?"))) {
    "KREATE_API_URL must be an https:// address"
}

android {
    namespace = "me.knighthat.kreate3.api"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        buildConfigField("String", "API_URL", "\"$apiUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Log calls in tests do nothing instead of throwing
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    implementation(libs.kermit)
    api(libs.kotlinx.coroutines.core)
    // The app hands its client over, so its proxy and timeouts apply
    api(libs.ktor.client.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
}
