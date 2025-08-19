package me.knighthat.updater

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kreate.android.BuildConfig
import app.kreate.android.R
import it.fast4x.rimusic.ui.styling.LocalAppearance
import me.knighthat.component.dialog.Dialog

open class ChangelogsDialog(context: Context): Dialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.update_changelogs, BuildConfig.VERSION_NAME )

    private var sections: SnapshotStateList<Section> = mutableStateListOf()
    private var selectedTab: Int by mutableIntStateOf( 0 )
    override var isActive: Boolean by mutableStateOf( false )

    init {
        var currentTitle: String? = null
        val currentChanges = mutableListOf<String>()

        fun packSection( title: String = currentTitle!! ) {
            // Because [currentChanges] is a mutable list, passing it here
            // will only pass the reference, any subsequent changes will be
            // updated to this list.
            sections.add( Section(title, currentChanges.toList()) )
            currentChanges.clear()
        }

        context.resources
               .openRawResource( R.raw.release_notes )
               .bufferedReader( Charsets.UTF_8 )
               .lines()
               .forEach { line ->
                   when {
                       line.endsWith( ":" ) -> {
                           // If [currentTitle] is not null, it means another section is reached.
                           // Therefore, pack last section to a [Section]
                           currentTitle?.let( ::packSection )

                           currentTitle = line.removeSuffix(":")
                       }
                       line.trim().startsWith("-") -> {
                           if( line.isNotBlank() )
                               currentChanges.add( line.trim() )
                       }
                   }
               }
        currentTitle?.let( ::packSection )
    }

    @Composable
    override fun Render() {
        if( !BuildConfig.DEBUG )
            super.Render()
    }

    @Composable
    override fun DialogBody() {
        val (colorPalette, typography) = LocalAppearance.current

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = colorPalette.background0,
                contentColor = colorPalette.text,
                indicator = { tabPositions ->
                    val selectedPosition = tabPositions[selectedTab]
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset( selectedPosition ),
                        color = colorPalette.accent,
                        width = selectedPosition.width
                    )
                }
            ) {
                sections.forEachIndexed { index, section ->
                    Tab(
                        selected = index == selectedTab,
                        onClick = { selectedTab = index }
                    ) {
                        Text(
                            text = section.title,
                            style = typography.m,
                            color = if( index == selectedTab ) colorPalette.text else colorPalette.textSecondary,
                            fontWeight = if( index == selectedTab ) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            val configuration = LocalWindowInfo.current
            val changesBoxHeight by remember {
                derivedStateOf {
                    val aThirdScreenHeight = (configuration.containerSize.height * .3f).toInt()

                    sections.maxOf { it.changes.size }
                            // Make-up number, seems to be appropriate for the height of `xs`
                            .times( 22 )
                            // Ensure box's height can't go above 30% of screen
                            .coerceAtMost( aThirdScreenHeight )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues( top = 10.dp ),
                modifier = Modifier.fillMaxWidth( .9f )
                    .requiredHeight( changesBoxHeight.dp )
            ) {
                items(
                    items = sections[selectedTab].changes,
                    key = null      // Lines aren't going to change so key should be null
                ) {
                    BasicText(
                        text = it,
                        style = typography.xs
                    )
                }
            }
        }
    }

    private data class Section( val title: String, val changes: List<String> )
}