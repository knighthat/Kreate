import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.ExcludeTransitiveDependenciesFilter
import com.github.jk1.license.render.JsonReportRenderer
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date

val APP_NAME = "Kreate"
val VERSION_CODE = 124

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
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)

    // Android
    alias(libs.plugins.android.application)
    alias(libs.plugins.room)

    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias( libs.plugins.license.report )
    alias( libs.plugins.hilt )
}

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }

    compilerOptions {
        freeCompilerArgs.add( "-Xexpect-actual-classes" )
    }

    jvm()

    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
            }
        }

        jvmMain.dependencies {
            implementation(compose.components.resources)
            implementation(compose.desktop.currentOs)

            implementation(libs.material.icons.desktop.ext)
            implementation(libs.vlcj)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.guava)
            implementation(libs.newpipe.extractor)
            implementation(libs.nanojson)
            implementation(libs.androidx.webkit)

            implementation( libs.androidx.glance.widgets )
            implementation( libs.androidx.constraintlayout )

            implementation( libs.androidx.appcompat )
            implementation( libs.androidx.appcompat.resources )
            implementation( libs.androidx.palette )

            implementation( libs.monetcompat )
            implementation(libs.androidmaterial)

            // Player implementations
            implementation( libs.media3.exoplayer )
            implementation(libs.media3.session)
            implementation( libs.media3.datasource.okhttp )
            implementation( libs.androidyoutubeplayer )

            implementation( libs.timber )

            implementation( libs.toasty )

            // Dependency injection
            implementation( libs.android.hilt )
        }
        androidUnitTest.dependencies {
            implementation( libs.junit4 )
            implementation( libs.robolectric )
            implementation( libs.androidx.test )
        }
        commonMain.dependencies {
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(projects.innertube)
            implementation(projects.oldtube)
            implementation(projects.kugou)
            implementation(projects.lrclib)
            implementation( projects.discord )

            // Room KMP
            implementation( libs.room.runtime )

            implementation(libs.navigation.kmp)

            //coil3 mp
            implementation(libs.coil3.compose.core)
            implementation( libs.coil3.network.ktor )

            implementation(libs.translator)

            implementation( libs.bundles.compose.kmp )

            implementation ( libs.hypnoticcanvas )
            implementation ( libs.hypnoticcanvas.shaders )

            implementation( libs.kotlin.csv )

            implementation( libs.bundles.ktor )
            implementation( libs.okhttp3.logging.interceptor )

            implementation( libs.math3 )

            implementation( libs.material.icons.kmp )
        }
        commonTest.dependencies {
            implementation( libs.kotlin.test )
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileSdk = 36

    defaultConfig {
        applicationId = "me.knighthat.kreate"
        minSdk = 21
        targetSdk = 36

        /*
                UNIVERSAL VARIABLES
         */
        buildConfigField( "String", "APP_NAME", "\"$APP_NAME\"" )
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

        // To test compatibility after minification process
        create( "debugR8" ) {
            initWith( maybeCreate( "debug" ) )

            // Package optimization
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-defaults.txt"),
                "debug-proguard-rules.pro"
            )
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

            // Build architecture
            buildConfigField( "String", "ARCH", "\"$name\"" )
        }
        create("arm64") {
            dimension = "arch"

            // Build architecture
            ndk { abiFilters += "arm64-v8a" }
            buildConfigField( "String", "ARCH", "\"$name\"" )
        }
        create("arm32") {
            dimension = "arch"
            ndk { abiFilters += "armeabi-v7a" }
            buildConfigField( "String", "ARCH", "\"$name\"" )
        }
        create("x86") {
            dimension = "arch"

            ndk { abiFilters += "x86" }
            buildConfigField( "String", "ARCH", "\"$name\"" )
        }
        create("x86_64") {
            dimension = "arch"

            // Build architecture
            ndk { abiFilters += "x86_64" }
            buildConfigField( "String", "ARCH", "\"$name\" ")
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
            manifestPlaceholders["appName"] = APP_NAME
            versionCode = VERSION_CODE
        }
        //</editor-fold>
    }

    applicationVariants.all {
        outputs.map { it as BaseVariantOutputImpl }
               .forEach {
                   val suffix = if( "izzy" in flavorName )
                       "izzy"
                   else if( "Nightly" in flavorName )
                       "nightly"
                   // The next 4 conditions set the APK name to the architect
                   // if it's intended for release build
                   else if( "Arm64" in flavorName && buildType.name == "release" )
                       "arm64-v8a"
                   else if( "Arm32" in flavorName && buildType.name == "release" )
                       "armeabi-v7a"
                   else if( "X86_64" in flavorName && buildType.name == "release" )
                       "x86_64"
                   else if( "X86" in flavorName && buildType.name == "release" )
                       "x86"
                   // Or just append build type at the end of the APK file name
                   else
                       buildType.name

                   it.outputFileName = "$APP_NAME-${suffix}.apk"
               }

        if( buildType.name != "debug" ) {
            preBuildProvider.get().dependsOn( copyReleaseNote )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
        group = "me.knighthat.kreate"

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
    add( "kspAndroid", libs.room.compiler )
    add( "kspAndroid", libs.android.hilt.compiler )

    coreLibraryDesugaring(libs.desugaring.nio)
}

// Use `gradlew dependencies` to get report in composeApp/build/reports/dependency-license
licenseReport {
    // Select projects to examine for dependencies.
    // Defaults to current project and all its subprojects
    projects = arrayOf( project )

    // Adjust the configurations to fetch dependencies. Default is 'runtimeClasspath'
    // For Android projects use 'releaseRuntimeClasspath' or 'yourFlavorNameReleaseRuntimeClasspath'
    // Use 'ALL' to dynamically resolve all configurations:
    // configurations = ALL
    configurations = arrayOf( "githubUniversalProdUncompressedRuntimeClasspath" )

    // Don't include artifacts of project's own group into the report
    excludeOwnGroup = true

    // Don't exclude bom dependencies.
    // If set to true, then all BOMs will be excluded from the report
    excludeBoms = true

    // Set custom report renderer, implementing ReportRenderer.
    // Yes, you can write your own to support any format necessary.
    renderers = arrayOf( JsonReportRenderer() )

    filters = arrayOf<DependencyFilter>( ExcludeTransitiveDependenciesFilter() )
}

val copyReleaseNote = tasks.register<Copy>("copyReleaseNote" ) {
    from( "$rootDir/fastlane/metadata/android/en-US/changelogs" )

    val fileName = "$VERSION_CODE.txt"
    setIncludes( listOf( fileName ) )

    into( "$rootDir/composeApp/src/androidMain/res/raw" )

    rename {
        if( it == fileName ) "release_notes.txt" else it
    }
}
