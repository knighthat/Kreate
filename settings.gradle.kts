enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Kreate"
include(":composeApp")
// Projects from extensions
include(":oldtube")
project(":oldtube").projectDir = file("extensions/innertube")
include(":kugou")
project(":kugou").projectDir = file("extensions/kugou")
include(":lrclib")
project(":lrclib").projectDir = file("extensions/lrclib")
// Submodules
include(":innertube")
project(":innertube").projectDir = file("modules/innertube")
include("discord")
project(":discord").projectDir = file("modules/discord")