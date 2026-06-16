plugins {
    // Multiplatform
    alias( libs.plugins.kotlin.multiplatform )

    // Android
    alias( libs.plugins.android.kotlin.multiplatform.library )
    alias( libs.plugins.android.lint )

    // Compose
    alias( libs.plugins.jetbrains.compose )
    alias( libs.plugins.kotlin.compose )
}

kotlin {
    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidLibrary {
        namespace = "app.kreate.preferences"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

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

            // Expose Translator classes
            api( libs.translator )
            implementation( libs.kermit )
            implementation( libs.koin.core )
            implementation( libs.kotlinx.coroutines )
            // Compose
            implementation( libs.compose.kmp.ui )
            implementation( libs.compose.kmp.resources )
            implementation( libs.compose.kmp.foundation )
            implementation( libs.compose.kmp.material3 )
            // Datastore
            implementation( libs.datastore )
            implementation( libs.datastore.preferences )
        }
        commonTest.dependencies {
            implementation( libs.kotlin.test )
        }
        androidMain.dependencies {
            implementation( libs.media3.ktx )
            implementation( libs.androidx.core.ktx )
        }
        getByName( "androidDeviceTest" ).dependencies {
            implementation( libs.androidx.junit )
            implementation( libs.androidx.runner )
            implementation( libs.androidx.test )
        }
    }
}