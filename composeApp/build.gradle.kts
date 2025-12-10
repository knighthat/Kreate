import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date


val VERSION_CODE = 1

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance( "SHA-256" )
    val hashBytes = digest.digest( this.toByteArray() )

    return hashBytes.joinToString("") { b -> "%02x".format(b) }
}

// Please DO NOT change this, it's intended to differentiate between
// knighthat/Kreate's build env and others' build env.
// Only official build env has passwords and keystore to sign the APK
// Other build environments can have unsigned version instead
val officialBuildPhrase: String? = System.getenv( "OFFICIAL_BUILD_PASSPHRASE" )
val isOfficialBuildEnv = !officialBuildPhrase.isNullOrBlank() && officialBuildPhrase.sha256() == "b2c778240e03b2005d23899aa02e51de049223a54d549d082e89dc20e51dd545"

plugins {
    // Multiplatform
    alias( libs.plugins.compose.multiplatform )
    alias( libs.plugins.kotlin.multiplatform )
    alias( libs.plugins.room.kmp )

    // Android
    alias( libs.plugins.application )

    // Other
    alias( libs.plugins.compose.hot.reload )
    alias( libs.plugins.compose.compiler )
    alias( libs.plugins.ksp )
    alias( libs.plugins.kotlin.serialization )
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    compilerOptions {
        freeCompilerArgs.add( "-Xexpect-actual-classes" )
    }
    
    jvm()
    
    sourceSets {
        androidMain.dependencies {
            // Compose
            implementation( compose.preview )
            implementation( libs.androidx.activity.compose )

            // Dependency injection
            implementation( libs.koin.android )

            // Material
            implementation( libs.material3.android )
            implementation( libs.material.android )     // This is needed for themes.xml

            implementation( libs.androidx.core.ktx )
            implementation( libs.androidx.splashscreen )
            implementation( libs.kotlinx.coroutines.android )
            implementation( libs.ktor.okhttp )
        }
        androidUnitTest.dependencies {
            implementation( libs.robolectric )
        }
        jvmMain.dependencies {
            implementation( compose.desktop.currentOs )
            implementation( libs.kotlinx.coroutines.swing )
            implementation( libs.ktor.cio )
            implementation( libs.picocli )

            // Material
            implementation( libs.material3.desktop )
        }
        commonMain.dependencies {
            implementation( compose.components.resources )
            implementation( compose.components.uiToolingPreview )
            implementation(libs.kotlinx.coroutines)
            implementation( libs.kotlinx.serialization.json )
            implementation( libs.bundles.ktor )
            implementation( libs.bundles.coil3 )
            implementation( projects.innertube )

            // Database
            implementation( libs.room.runtime )
            implementation( libs.sqlite.bundled )

            // Material
            implementation( libs.bundles.material3 )

            // Dependency injection
            api( libs.koin.core )
            implementation( libs.koin.compose )

            // Logging
            implementation( libs.kermit )
            api( libs.kermit.io )

            // Datastore - Don't expose API because it's masked by Preferences.kt
            implementation( libs.datastore )
            implementation( libs.datastore.preferences )
        }
        commonTest.dependencies {
            implementation( libs.kotlin.test )
            implementation( libs.kotlin.test.junit )
            implementation( kotlin("reflect") )
            implementation( libs.kotlinx.coroutines.test )

            @OptIn(ExperimentalComposeLibrary::class)
            implementation( compose.uiTest )
        }
    }
}

android {
    namespace = "me.knighthat.kreate"

    defaultConfig {
        applicationId = "me.knighthat.kreate"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        compileSdk = libs.versions.targetSdk.get().toInt()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        create( "persistent-debug" ) {
            storeFile = file("$rootDir/.ignore.d/keystores/debug.jks")
            keyAlias = "debug"
            storePassword = "storePass"
            keyPassword = "keyPass"
        }
        create( "production" ) {
            storeFile = file("$rootDir/.ignore.d/keystores/production.jks")
            keyAlias = "kreate"
            storePassword = System.getenv( "STORE_PASSWORD" )
            keyPassword = System.getenv( "KEY_PASSWORD" )
        }
        create( "nightly" ) {
            storeFile = file("$rootDir/.ignore.d/keystores/nightly.jks")
            keyAlias = "nightly"
            storePassword = System.getenv( "STORE_PASSWORD" )
            keyPassword = System.getenv( "KEY_PASSWORD" )
        }
    }
    buildTypes {
        debug {
            // Signing config
            signingConfig = signingConfigs.getByName( "persistent-debug" )

            // App's properties
            applicationIdSuffix = ".debug"
        }
        release {
            isDefault = true

            // Package optimization
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create( "uncompressed" ) {
            // App's properties
            versionNameSuffix = "-f"
        }
    }
    flavorDimensions += listOf( "platform", "arch", "env" )
    //noinspection ChromeOsAbiSupport
    productFlavors {
        //<editor-fold desc="Platforms">
        create("github") {
            dimension = "platform"

            isDefault = true
        }
        create( "fdroid" ) {
            dimension = "platform"

            // App's properties
            versionNameSuffix = "-fdroid"
        }
        create( "izzy" ) {
            dimension = "platform"

            // App's properties
            versionNameSuffix = "-izzy"
        }
        //</editor-fold>
        //<editor-fold desc="Architectures">
        create("universal") {
            dimension = "arch"

            isDefault = true
        }
        create("arm64") {
            dimension = "arch"

            // App's properties
            versionCode = (1 shl 20) or VERSION_CODE

            // Build architecture
            ndk { abiFilters += "arm64-v8a" }
        }
        create("arm32") {
            dimension = "arch"

            // App's properties
            versionCode = (1 shl 19) or VERSION_CODE

            // Build architecture
            ndk { abiFilters += "armeabi-v7a" }
        }
        create("x86") {
            dimension = "arch"

            // App's properties
            versionCode = (1 shl 18) or VERSION_CODE

            // Build architecture
            ndk { abiFilters += "x86" }
        }
        create("x86_64") {
            dimension = "arch"

            // App's properties
            versionCode = (1 shl 17) or VERSION_CODE

            // Build architecture
            ndk { abiFilters += "x86_64" }
        }
        //</editor-fold>
        //<editor-fold desc="Environment">
        create( "nightly" ) {
            dimension = "env"

            // Signing config
            signingConfig = signingConfigs.getByName( "nightly" )

            val longFormat = SimpleDateFormat("yyyy.MM.dd")
            val shortFormat = SimpleDateFormat("yyMMdd")

            // App's properties
            applicationIdSuffix = ".nightly"
            versionName = longFormat.format (Date())
            manifestPlaceholders["appName"] = "Nightly"
            // The idea is to combine build date and current version code together
            versionCode = "${shortFormat.format( Date() )}$VERSION_CODE".toInt()
        }
        create( "prod" ) {
            dimension = "env"

            isDefault = true

            if( isOfficialBuildEnv )
            // Singing config
                signingConfig = signingConfigs.getByName( "production" )

            // App's properties
            versionName = "1.8.4"
            versionCode = VERSION_CODE
        }
        //</editor-fold>
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21

        isCoreLibraryDesugaringEnabled = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    debugImplementation( compose.uiTooling )

    add( "kspAndroid", libs.room.compiler )
    add( "kspJvm", libs.room.compiler )

    coreLibraryDesugaring( libs.android.desugar )
}

compose.desktop {
    application {
        mainClass = "me.knighthat.kreate.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.AppImage,
                TargetFormat.Deb,
                TargetFormat.Rpm,
                TargetFormat.Dmg,
                TargetFormat.Pkg,
                TargetFormat.Exe,
                TargetFormat.Msi
            )

            packageName = "me.knighthat.kreate"
            packageVersion = "1.0.0"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}
