plugins {
    // Multiplatform
    alias( libs.plugins.kotlin.multiplatform )

    // Android
    alias( libs.plugins.android.kotlin.multiplatform.library )
    alias( libs.plugins.android.lint )

    // Compose
    alias( libs.plugins.kotlin.compose )
    alias( libs.plugins.jetbrains.compose )
}

kotlin {
    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidLibrary {
        namespace = "app.kreate.resources"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        // Expose resources to other modules.
        // Required! Without this option, final APK won't include this module's resources
        androidResources.enable = true

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compilerOptions {
            enableCoreLibraryDesugaring = true
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
            // Expose Path interface for subscribing modules
            api( libs.okio )
            implementation( libs.koin.core )
            implementation( libs.kermit )
            // Compose
            implementation( libs.compose.kmp.runtime )
            implementation( libs.compose.kmp.ui )
            implementation( libs.compose.kmp.resources )
            implementation( libs.compose.kmp.foundation )
        }
        commonTest.dependencies {
            implementation( libs.kotlin.test )
        }
        androidMain.dependencies {
            // Expose some Palette interface
            api( libs.androidx.palette )
            implementation( libs.androidx.core.ktx )
        }
        getByName("androidDeviceTest"). dependencies {
            implementation( libs.androidx.junit )
            implementation( libs.androidx.runner )
            implementation( libs.androidx.test )
        }
    }

    compilerOptions {
        freeCompilerArgs.add( "-Xexpect-actual-classes" )
    }
}

dependencies {
    coreLibraryDesugaring( libs.desugaring.nio )
}

compose {
    // Make Res class importable from other places
    resources.publicResClass = true
}