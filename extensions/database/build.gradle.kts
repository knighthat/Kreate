plugins {
    // Multiplatform
    alias( libs.plugins.kotlin.multiplatform )
    alias( libs.plugins.room )

    // Android
    alias( libs.plugins.android.kotlin.multiplatform.library )
    alias( libs.plugins.android.lint )
}

kotlin {

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidLibrary {
        namespace = "app.kreate.database"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        androidResources.enable = true

        withHostTestBuilder {
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    jvm()

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        commonMain.dependencies {
            implementation( projects.resources )
            implementation( projects.preferences )

            implementation( libs.kotlin.stdlib )
            implementation( libs.koin.core )
            implementation( libs.kermit )
            // Room
            implementation( libs.room.runtime )
            implementation( libs.sqlite.bundled )
            // Compose
            implementation( libs.compose.kmp.runtime )
        }
        commonTest.dependencies {
            implementation( libs.kotlin.test )
        }
        androidMain.dependencies {
        }
        getByName( "androidDeviceTest" ).dependencies {
            implementation( libs.androidx.junit )
            implementation( libs.androidx.runner )
            implementation( libs.androidx.test )
        }
    }
}

room {
    schemaDirectory( "$projectDir/schemas" )
}