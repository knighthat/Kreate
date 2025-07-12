package app.kreate.android.utils

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.utils.align
import it.fast4x.rimusic.utils.secondary
import it.fast4x.rimusic.utils.semiBold

fun LazyListScope.renderDescription( description: String ) = item( "description" ) {
    Row(
        Modifier.padding( vertical = 16.dp, horizontal = 8.dp )
    ) {
        BasicText(
            text = "“",
            style = typography().xxl.semiBold,
            modifier = Modifier.offset( y = (-8).dp )
                               .align( Alignment.Top )
        )

        BasicText(
            text = description,
            style = typography().xxs
                                .secondary
                                .align( TextAlign.Justify ),
            modifier = Modifier.padding( horizontal = 8.dp )
                               .weight( 1f )
        )

        BasicText(
            text = "„",
            style = typography().xxl.semiBold,
            modifier = Modifier.offset( y = 4.dp )
                               .align( Alignment.Bottom )
        )
    }
}