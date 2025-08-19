package me.knighthat.updater

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kreate.android.BuildConfig
import app.kreate.android.R
import app.kreate.android.themed.common.component.dialog.Dialog
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.bold
import kotlinx.coroutines.launch

open class ChangelogsDialog(context: Context): Dialog() {

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.update_changelogs, BuildConfig.VERSION_NAME )

    private lateinit var pagerState: PagerState
    private var sections: SnapshotStateList<Section> = mutableStateListOf()
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
//        if( BuildConfig.DEBUG ) return

        // Initialize this ASAP
        if( !::pagerState.isInitialized )
            this.pagerState = rememberPagerState { sections.size }

        super.Render()
    }

    @Composable
    override fun DialogHeader() {
        val scope = rememberCoroutineScope()
        val (colorPalette, typography) = LocalAppearance.current

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // "vX.X.X changelogs" title
            BasicText(
                text = dialogTitle,
                style = typography.m.bold,
                modifier = Modifier.padding( bottom = VERTICAL_PADDING.dp )
                                   .fillMaxWidth( .9f )
            )

            TabRow(
                selectedTabIndex = pagerState.targetPage,
                containerColor = colorPalette.background0,
                contentColor = colorPalette.text,
                indicator = { tabPositions ->
                    val selectedPosition = tabPositions[pagerState.targetPage]
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset( selectedPosition ),
                        color = colorPalette.accent,
                        width = selectedPosition.width
                    )
                }
            ) {
                sections.forEachIndexed { index, section ->
                    val isSelected = index == pagerState.targetPage
                    Tab(
                        selected = isSelected,
                        onClick = {
                            scope.launch {
                                this@ChangelogsDialog.pagerState.animateScrollToPage( index )
                            }
                        }
                    ) {
                        Text(
                            text = section.title,
                            style = typography.m,
                            color = if( isSelected ) colorPalette.text else colorPalette.textSecondary,
                            fontWeight = if( isSelected ) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun DialogBody() {
        val sectionTextStyle = LocalAppearance.current.typography.xs

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = sections.size,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth( .9f )
        ) { selectedTab ->
            Column {
                sections[selectedTab].changes.forEach { line ->
                    BasicText(
                        text = line,
                        style = sectionTextStyle
                    )
                }
            }
        }
    }

    private data class Section( val title: String, val changes: List<String> )
}