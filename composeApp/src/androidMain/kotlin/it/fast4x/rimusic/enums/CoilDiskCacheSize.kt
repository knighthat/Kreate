package it.fast4x.rimusic.enums

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class CoilDiskCacheMaxSize: TextView {
    `32MB`,
    `64MB`,
    `128MB`,
    `256MB`,
    `512MB`,
    `1GB`,
    `2GB`,
    `4GB`,
    `8GB`,
    Custom;

    val bytes: Long
        get() = when (this) {
            `32MB` -> 32
            `64MB` -> 64
            `128MB` -> 128
            `256MB` -> 256
            `512MB` -> 512
            `1GB` -> 1024
            `2GB` -> 2048
            `4GB` -> 4096
            `8GB` -> 8192
            Custom -> 1000000
        } * 1000 * 1000L

    override val text: String
        @Composable
        get() = when ( this ) {
            Custom -> stringResource( R.string.custom )
            else -> this.name
        }
}
