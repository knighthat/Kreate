import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.ExcludeTransitiveDependenciesFilter
import com.github.jk1.license.render.JsonReportRenderer
import org.gradle.internal.extensions.stdlib.capitalized
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val APP_NAME = "Kreate"

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

            implementation(libs.material.icon.desktop)
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
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
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

    compileSdk = 36

    defaultConfig {
        applicationId = "me.knighthat.kreate"
        minSdk = 21
        targetSdk = 36
        versionCode = 122
        versionName = "1.8.2"

        /*
                UNIVERSAL VARIABLES
         */
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

        /**
         * For convenience only.
         * "Forkers" want to change app name across builds
         * just need to change this variable
         */
        forEach {
            it.manifestPlaceholders.putIfAbsent( "appName", APP_NAME )
        }
    }

    flavorDimensions += listOf( "prod" )
    productFlavors {
        create("github") {
            dimension = "prod"

            isDefault = true
        }

        create( "fdroid" ) {
            dimension = "prod"

            // App's properties
            versionNameSuffix = "-fdroid"
        }

        create( "izzy" ) {
            dimension = "prod"

            // App's properties
            versionNameSuffix = "-izzy"
        }
    }

    applicationVariants.all {
        outputs.map { it as BaseVariantOutputImpl }
               .forEach {
                   val suffix = if( flavorName == "izzy" ) "izzy" else buildType.name
                   it.outputFileName = "$APP_NAME-${suffix}.apk"
               }

        if( buildType.name != "debug" ) {
            val capitalizedFlavorName = "${flavorName.capitalized()}${buildType.name.capitalized()}"

            tasks.register<Copy>("copyReleaseNoteTo${capitalizedFlavorName}Res" ) {
                from( "$rootDir/fastlane/metadata/android/en-US/changelogs" )
                val fileName = "${android.defaultConfig.versionCode!!}.txt"
                setIncludes( listOf( fileName ) )

                into( "$rootDir/composeApp/src/android$capitalizedFlavorName/res/raw" )

                rename {
                    if( it == fileName ) "release_notes.txt" else it
                }
            }

            preBuildProvider.get().dependsOn( "copyReleaseNoteTo${capitalizedFlavorName}Res" )
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
    configurations = arrayOf( "githubUncompressedRuntimeClasspath" )

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