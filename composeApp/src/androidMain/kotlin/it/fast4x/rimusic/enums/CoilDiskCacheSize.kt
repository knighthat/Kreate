package it.fast4x.rimusic.enums

import androidx.annotation.Size
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.kreate.compose.R
import me.knighthat.enums.TextView

enum class CoilDiskCacheMaxSize(
    @field:Size val megabytes: Int
): TextView {
    `32MB`( 32 ),
    `64MB`( 64 ),
    `128MB`( 128 ),
    `256MB`( 256 ),
    `512MB`( 512 ),
    `1GB`( 1024 ),
    `2GB`( 2048 ),
    `4GB`( 4096 ),
    `8GB`( 8192 ),
    Custom( 1_000_000 );

    val bytes: Long = megabytes.times( 1000L ).times( 1000 )

    override val text: String
        @Composable
        get() = when ( this ) {
            Custom -> stringResource( R.string.custom )
            else -> this.name
        }
}
