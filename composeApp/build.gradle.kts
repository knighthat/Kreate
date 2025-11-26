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

    // Android
    alias( libs.plugins.application )
    alias( libs.plugins.hilt )

    // Other
    alias( libs.plugins.compose.hot.reload )
    alias( libs.plugins.compose.compiler )
    alias( libs.plugins.ksp )
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    
    jvm()
    
    sourceSets {
        androidMain.dependencies {
            // Compose
            implementation( compose.preview )
            implementation( libs.androidx.activity.compose )

            // Dependency injection
            implementation( libs.android.hilt )

            implementation( libs.androidx.appcompat )
            implementation( libs.androidx.core.ktx )
            implementation( libs.androidx.splashscreen )
        }
        jvmMain.dependencies {
            implementation( compose.desktop.currentOs )
            implementation(libs.kotlinx.coroutines.swing)
        }
        commonMain.dependencies {
            implementation( compose.runtime )
            implementation( compose.foundation )
            implementation( compose.material3 )
            implementation( compose.ui )
            implementation( compose.components.resources )
            implementation( compose.components.uiToolingPreview )
        }
        commonTest.dependencies {
            implementation( libs.kotlin.test )
            implementation( libs.kotlin.test.junit )
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
    }
}

dependencies {
    debugImplementation( compose.uiTooling )

    add( "kspAndroid", libs.android.hilt.compiler )
}

compose.desktop {
    application {
        mainClass = "me.knighthat.kreate.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "me.knighthat.kreate"
            packageVersion = "1.0.0"
        }
    }
}
