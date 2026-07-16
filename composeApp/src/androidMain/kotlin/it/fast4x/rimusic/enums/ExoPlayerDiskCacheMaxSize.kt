package it.fast4x.rimusic.enums

import androidx.annotation.Size
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.kreate.compose.R
import me.knighthat.enums.TextView

enum class ExoPlayerDiskCacheMaxSize(
    @field:Size val megabytes: Int
): TextView {

    `Disabled`( 1 ),
    `32MB`( 32 ),
    `512MB`( 512 ),
    `1GB`( 1024 ),
    `2GB`( 2048 ),
    `4GB`( 4096 ),
    `8GB`( 8192 ),
    Unlimited( 0 ),
    Custom( 1_000_000 );

    val bytes: Long = megabytes.times( 1000L ).times( 1000 )

    override val text: String
        @Composable
        get() = when ( this ) {
            Disabled -> stringResource( R.string.vt_disabled )
            Unlimited -> stringResource( R.string.unlimited )
            Custom -> stringResource( R.string.custom )
            else -> this.name
        }
}
