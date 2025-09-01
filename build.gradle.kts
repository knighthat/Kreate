plugins {
    // Multiplatform
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.compose) apply false

    // Android
    alias(libs.plugins.android.application) apply false

    // Others
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias( libs.plugins.kotlin.jvm ) apply false
    alias( libs.plugins.hilt ) apply false
}
