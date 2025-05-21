package me.knighthat.utils

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography

object PathUtils {

    fun findCommonPath( paths: Collection<String> ): String {
        if( paths.isEmpty() ) return ""

        val splitPaths = paths.map { it.split( "/" ) } // Split each path by '/'
        val commonParts = mutableListOf<String>()

        for (i in 0 until splitPaths[0].size) {
            val segment = splitPaths[0][i]
            if (splitPaths.all { it.size > i && it[i] == segment }) {
                commonParts.add(segment)
            } else {
                break
            }
        }

        return commonParts.joinToString( "/" )
    }

    fun getAvailablePaths( paths: Collection<String>, currentPath: String ): List<String> {
        val normalizedCurrentPath = if ( currentPath.endsWith("/") ) currentPath else "$currentPath/"
        val currentDepth = normalizedCurrentPath.split( "/" )
                                                     .filter { it.isNotEmpty() }
                                                     .size

        return paths.mapNotNull { path ->
                        if (path.startsWith(normalizedCurrentPath)) {
                            val parts = path.split("/").filter { it.isNotEmpty() }
                            if (parts.size > currentDepth) parts[currentDepth] else null
                        } else null
                    }
                    .distinct() // Keep unique values
                    .sorted()
    }

    /**
     * Draw an interactive address bar starting with home ([Icons.Default.Home]) icon.
     * Between folders there's [Icons.AutoMirrored.Filled.KeyboardArrowRight].
     *
     * Each path is clickable. When clicked, open folder at that exact location.
     *
     * @param paths all paths of local songs
     * @param currentPath where user is at
     * @param onSpecificAddressClick action happens when user clicks 1 of the path
     */
    @Composable
    fun AddressBar(
        paths: Collection<String>,
        currentPath: String,
        onSpecificAddressClick: (String) -> Unit
    ) = Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding( bottom = 10.dp )
                           .padding( horizontal = 20.dp )
    ) {

        Icon(
            imageVector = Icons.Default.Home,
            tint = colorPalette().textDisabled,
            contentDescription = null,
            modifier = Modifier.size( typography().xs.fontSize.value.dp )
                               .clickable {
                                   onSpecificAddressClick( findCommonPath( paths ) )
                               }
        )

        val totalPaths = currentPath.split( "/" ).filter( String::isNotEmpty )
        var fullPath = ""
        // Older versions of Android display full path,
        // this makes the address bar go overflow.
        totalPaths.takeLast( 3 ).fastForEach { path ->
            fullPath += "/$path"

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                tint = colorPalette().accent,
                contentDescription = null
            )

            val capturedCurrentPath = remember {
                // Remove prefix "/"
                fullPath.drop(1)
            }
            BasicText(
                text = path,
                style = typography().xs.copy(
                    color = colorPalette().text,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.clickable { onSpecificAddressClick( capturedCurrentPath ) }
            )
        }
    }
}