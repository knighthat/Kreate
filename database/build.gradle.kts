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
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "me.knighthat.kreate.database"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions.unitTests.all {
        // Robolectric sets up Android's file descriptors through JDK internals
        it.jvmArgs(
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
        )
    }
}

room {
    // Exported schemas are the base for writing and testing migrations, commit them
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    // Repositories return PagingData, so callers need the types
    api(libs.androidx.paging.common)

    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    // Room runs on the JVM through Robolectric, with the framework driver in place of the bundled one
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.paging.testing)
}
