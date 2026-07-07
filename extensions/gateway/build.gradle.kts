plugins {
    // Multiplatform
    alias( libs.plugins.kotlin.multiplatform )

    // Android
    alias( libs.plugins.android.kotlin.multiplatform.library )
    alias( libs.plugins.android.lint )

    // Others
    alias( libs.plugins.kotlin.serialization )
}

kotlin {

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidLibrary {
        namespace = "me.knighthat.gateway"
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
            implementation( projects.preferences )
            implementation( libs.kotlin.stdlib )
            implementation( libs.koin.core )
            implementation( libs.kermit )
            implementation( libs.bundles.ktor )
        }
        commonTest.dependencies {
            implementation( libs.kotlin.test )
        }
        androidMain {
            dependencies {
                // Metrolist
                api( libs.metrolist.extractor )
                api( libs.nanojson )
                api( libs.timber )
                // Media3
                api( libs.media3.ktx )
            }
            kotlin {
                val metrolistDir = "$rootDir/modules/metrolist"
                srcDirs(
                    "$projectDir/composeApp/src/androidMain/kotlin",
                    "$metrolistDir/app/src/main/kotlin",
                    "$metrolistDir/innertube/src/main/kotlin/"
                )
                include(
                    "app/kreate/**",
                    "com/metrolist/music/utils/cipher/**",
                    "com/metrolist/music/utils/potoken/**",
                    "com/metrolist/music/utils/YTPlayerUtils.kt",
                    "com/metrolist/innertube/**",
                )
            }
        }
        getByName( "androidDeviceTest" ).dependencies {
            implementation( libs.androidx.junit )
            implementation( libs.androidx.runner )
            implementation( libs.androidx.test )
        }
    }
}
