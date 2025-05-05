import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val APP_NAME = "Kreate"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.room)
    alias(libs.plugins.hilt)
}

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xcontext-receivers")
        }
    }

    jvm("desktop")



    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
            }
        }

        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.components.resources)
            implementation(compose.desktop.currentOs)

            implementation(libs.material.icon.desktop)
            implementation(libs.vlcj)

            implementation(libs.coil.network.okhttp)
            runtimeOnly(libs.kotlinx.coroutines.swing)

            /*
            // Uncomment only for build jvm desktop version
            // Comment before build android version
            configurations.commonMainApi {
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
            }
            */
        }

        androidMain.dependencies {
            implementation(libs.media3.session)
            implementation(libs.kotlinx.coroutines.guava)
            implementation(libs.newpipe.extractor)
            implementation(libs.nanojson)
            implementation(libs.androidx.webkit)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(projects.innertube)
            implementation(projects.piped)
            implementation(projects.invidious)

            implementation(libs.room)
            implementation(libs.room.runtime)
            implementation(libs.room.sqlite.bundled)

            implementation(libs.mediaplayer.kmp)

            implementation(libs.navigation.kmp)

            //coil3 mp
            implementation(libs.coil.compose.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.mp)

            implementation(libs.translator)

        }
    }
}

android {
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileSdk = 35

    defaultConfig {
        applicationId = "me.knighthat.kreate"
        minSdk = 21
        targetSdk = 35
        versionCode = 96
        versionName = "1.3.2"

        /*
                UNIVERSAL VARIABLES
         */
        buildConfigField( "Boolean", "IS_AUTOUPDATE", "true" )
        buildConfigField( "String", "APP_NAME", "\"$APP_NAME\"" )
    }

    splits {
        abi {
            reset()
            isUniversalApk = true
        }
    }

    namespace = "app.kreate.android"

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appName"] = "$APP_NAME-debug"

            buildConfigField( "Boolean", "IS_AUTOUPDATE", "false" )
        }

        create( "full" ) {
            // App's properties
            versionNameSuffix = "-f"
        }

        create( "minified" ) {
            // App's properties
            versionNameSuffix = "-m"

            // Package optimization
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create( "izzy" ) {
            initWith( maybeCreate("minified") )

            // App's properties
            versionNameSuffix = "-izzy"

            buildConfigField( "Boolean", "IS_AUTOUPDATE", "false" )
        }

        // Specifically tailored to F-Droid build
        // inherited from minified build type
        release {
            initWith( maybeCreate("noAutoUpdate") )

            // App's properties
            versionNameSuffix = "-fdroid"
        }

        /**
         * For convenience only.
         * "Forkers" want to change app name across builds
         * just need to change this variable
         */
        forEach {
            it.manifestPlaceholders.putIfAbsent( "appName", APP_NAME )
        }
    }

    applicationVariants.all {
        outputs.map { it as BaseVariantOutputImpl }
               .forEach { output ->
                   val typeName =
                       if( buildType.name == "noAutoUpdate" )
                           "no-autoupdate"
                       else
                           buildType.name

                   output.outputFileName = "$APP_NAME-$typeName.apk"
               }
    }

    sourceSets.all {
        kotlin.srcDir("src/$name/kotlin")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    androidResources {
        generateLocaleConfig = true
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

compose.desktop {
    application {

        mainClass = "MainKt"

        //conveyor
        version = "0.0.1"
        group = "rimusic"

        //jpackage
        nativeDistributions {
            //conveyor
            vendor = "RiMusic.DesktopApp"
            description = "RiMusic Desktop Music Player"

            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "RiMusic.DesktopApp"
            packageVersion = "0.0.1"
        }
    }
}

compose.resources {
    publicResClass = true
    generateResClass = always
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.compose.activity)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ripple)
    implementation(libs.compose.shimmer)
    implementation(libs.compose.coil)
    implementation(libs.palette)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.appcompat)
    implementation(libs.appcompat.resources)
    implementation(libs.media)
    implementation(libs.material)
    implementation(libs.material3)
    implementation(libs.compose.ui.graphics.android)
    implementation(libs.constraintlayout)
    implementation(libs.compose.runtime.livedata)
    implementation(libs.compose.animation)
    implementation(libs.kotlin.csv)
    implementation(libs.monetcompat)
    implementation(libs.androidmaterial)
    implementation(libs.timber)
    implementation(libs.crypto)
    implementation(libs.logging.interceptor)
    implementation(libs.math3)
    implementation(libs.toasty)
    implementation(libs.androidyoutubeplayer)
    implementation(libs.glance.widgets)
    implementation(libs.kizzy.rpc)
    implementation(libs.gson)
    implementation (libs.hypnoticcanvas)
    implementation (libs.hypnoticcanvas.shaders)

    implementation(libs.room)
    ksp(libs.room.compiler)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(projects.innertube)
    implementation(projects.kugou)
    implementation(projects.lrclib)
    implementation(projects.piped)

    coreLibraryDesugaring(libs.desugaring.nio)

    // Debug only
    debugImplementation(libs.ui.tooling.preview.android)
}
