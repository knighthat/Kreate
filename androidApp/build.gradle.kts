import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date


val APP_NAME = "Kreate"

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
    // Android
    alias( libs.plugins.android.application )

    // Compose
    alias( libs.plugins.kotlin.compose )
    alias( libs.plugins.jetbrains.compose )
}

kotlin {
    target {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }

    dependencies {
        implementation( projects.composeApp )

        implementation( libs.media3.exoplayer )
        implementation( libs.koin.android )
        implementation( libs.compose.kmp.resources )
        // Coil3
        implementation( libs.coil3.compose )
        implementation( libs.coil3.network.ktor )
        // Monet compat
        implementation( libs.androidx.appcompat )
        implementation( libs.monetcompat )
        implementation( libs.androidmaterial )
        // Logging
        implementation( libs.kermit )
        implementation( libs.timber )
        // Compose
        implementation( libs.bundles.compose.kmp )
        implementation( libs.androidx.activity.compose )
        // Lifecycle
        implementation( libs.androidx.lifecycle.runtime )
        implementation( libs.androidx.lifecycle.process )

        coreLibraryDesugaring( libs.desugaring.nio )
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach {
            val flavorName = variant.flavorName!!
            val buildType = variant.buildType!!

            val suffix = if( "izzy" in flavorName )
                "izzy"
            else if( "Nightly" in flavorName )
                "nightly"
            // The next 4 conditions set the APK name to the architect
            // if it's intended for release build
            else if( "Arm64" in flavorName && buildType == "release" )
                "arm64-v8a"
            else if( "Arm32" in flavorName && buildType == "release" )
                "armeabi-v7a"
            else if( "X86_64" in flavorName && buildType == "release" )
                "x86_64"
            else if( "X86" in flavorName && buildType == "release" )
                "x86"
            // Or just append build type at the end of the APK file name
            else
                buildType

            it.outputFileName = "$APP_NAME-${suffix}.apk"
        }

        val excludedBuildTypes = setOf("Debug")
        // tasks known to consume src/androidMain/res
        val resConsumingTaskPrefixes = listOf("package", "generate", "lint", "merge", "bundle")
        val resConsumingTaskSuffixes = listOf("Resources", "LintVitalModel", "LintModel", "Bundle")

        afterEvaluate {
            tasks.matching { task ->
                resConsumingTaskPrefixes.any { task.name.startsWith(it) } &&
                        resConsumingTaskSuffixes.any { task.name.endsWith(it) } &&
                        excludedBuildTypes.none { task.name.contains(it) }
            }.configureEach {
                dependsOn(":androidApp:copyReleaseNote")
            }
        }
    }
}

extensions.configure<ApplicationExtension> {
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "me.knighthat.kreate"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
    }

    namespace = "app.kreate.android"

    signingConfigs {
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
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appName"] = "$APP_NAME-debug"
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
        val vCode = libs.versions.versionCode.get().toInt()

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
        create("arm32") {
            dimension = "arch"

            // App's properties
            versionCode = (vCode * 10) + 1

            // Build architecture
            ndk { abiFilters += "armeabi-v7a" }
        }
        create("arm64") {
            dimension = "arch"

            // App's properties
            versionCode = (vCode * 10) + 2

            // Build architecture
            ndk { abiFilters += "arm64-v8a" }
        }
        create("x86") {
            dimension = "arch"

            // App's properties
            versionCode = (vCode * 10) + 3

            // Build architecture
            ndk { abiFilters += "x86" }
        }
        create("x86_64") {
            dimension = "arch"

            // App's properties
            versionCode = (vCode * 10) + 4

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
            versionName = longFormat.format (Date() )
            manifestPlaceholders["appName"] = "Nightly"
            // The idea is to combine build date and current version code together
            versionCode = "${shortFormat.format( Date() )}$vCode".toInt()
        }
        create( "prod" ) {
            dimension = "env"

            isDefault = true

            if( isOfficialBuildEnv )
            // Singing config
                signingConfig = signingConfigs.getByName( "production" )

            // App's properties
            versionName = libs.versions.versionName.get()
            manifestPlaceholders["appName"] = APP_NAME
            versionCode = vCode
        }
        //</editor-fold>
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets["main"].apply {
        assets.directories += "$rootDir/modules/metrolist/app/src/main/assets"
    }
}

val copyReleaseNote = tasks.register<Copy>("copyReleaseNote" ) {
    description = "Copy release note that matches current versionCode to raw folder"
    group = "build"

    from( "$rootDir/fastlane/metadata/android/en-US/changelogs" )

    val fileName = "${libs.versions.versionCode.get()}.txt"
    setIncludes( listOf(fileName) )

    val dest = layout.buildDirectory.dir( "generated/releaseNoteRes/raw" )
    into( dest )

    rename {
        if( it == fileName ) "release_notes.txt" else it
    }
}
