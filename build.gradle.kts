// this is necessary to avoid the plugins to be loaded multiple times
// in each subproject's classloader
plugins {
    // Multiplatform
    alias( libs.plugins.compose.multiplatform ) apply false
    alias( libs.plugins.kotlin.multiplatform ) apply false
    alias( libs.plugins.room.kmp ) apply false

    // Android
    alias( libs.plugins.application ) apply false
    alias( libs.plugins.library ) apply false

    // Desktop
    alias( libs.plugins.buildconfig ) apply false

    // Other
    alias( libs.plugins.compose.hot.reload ) apply false
    alias( libs.plugins.compose.compiler ) apply false
    alias( libs.plugins.ksp ) apply false
    alias( libs.plugins.kotlin.serialization ) apply false
    alias( libs.plugins.kotlin.jvm ) apply false
}