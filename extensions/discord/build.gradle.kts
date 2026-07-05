plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
}

kotlin {
    androidLibrary {
        namespace = "me.knighthat.discord"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }

    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation( libs.koin.core )
                implementation( libs.kermit )
                implementation( libs.bundles.ktor )
            }
            kotlin {
                // Instead of registering kizzy as a submodule, this method includes
                // the kizzy's src folders as part of its src folder.
                // This allows cleaner looking module tree, avoiding confusion
                val kizzyDir = "$rootDir/modules/kizzy"
                srcDir( "$kizzyDir/gateway/src/main/java" )
                srcDir( "$kizzyDir/domain/src/main/java/com/my/kizzy/domain/interfaces" )
            }
        }
        commonTest.dependencies {
            implementation( libs.kotlin.test )
        }
        androidMain.dependencies {
        }
    }

    compilerOptions {
        freeCompilerArgs.add( "-Xexpect-actual-classes" )
    }
}