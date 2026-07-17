package app.kreate.android.themed.common.screens.settings.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.kreate.compose.R
import app.kreate.utils.Toaster
import it.fast4x.rimusic.ui.components.Skeleton
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonArray


@OptIn(ExperimentalSerializationApi::class)
@Composable
fun Licenses(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {}
) {
    Skeleton(
        navController = navController,
        miniPlayer = miniPlayer,
        navBarContent = { item ->
            // Won't be shown
            item(0, stringResource(R.string.songs), R.drawable.musical_notes)
        }
    ) {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val (colorPalette, typography) = LocalAppearance.current
        val parser = Json {
            ignoreUnknownKeys = true
        }

        val licenseEntries = runCatching {
            context.resources
                   .openRawResource( R.raw.licenses )
                   .use { inStream ->
                       parser.decodeFromStream<JsonObject>( inStream )["dependencies"]
                             ?.jsonArray
                             ?.let {
                                 parser.decodeFromJsonElement<List<Dependency>>( it )
                             }
                             .orEmpty()
                   }
        }.onFailure { err ->
            err.printStackTrace()
            err.message?.also( Toaster::e )
        }.getOrDefault( emptyList() )

        LazyColumn(
            contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer ),
            verticalArrangement = Arrangement.spacedBy( 5.dp ),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = licenseEntries,
                key = System::identityHashCode,
            ) { dependency ->
                dependency.Draw( colorPalette, typography, uriHandler )
            }
        }
    }
}