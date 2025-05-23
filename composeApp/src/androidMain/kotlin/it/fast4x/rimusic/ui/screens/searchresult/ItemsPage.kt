package it.fast4x.rimusic.ui.screens.searchresult
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.fast4x.compose.persist.persist
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.utils.plus
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.ui.components.ShimmerHost
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.secondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import it.fast4x.rimusic.ui.items.AlbumPlaceholder
import it.fast4x.rimusic.ui.items.SongItemPlaceholder

@ExperimentalAnimationApi
@Composable
inline fun <T : Innertube.Item> ItemsPage(
    tag: String,
    crossinline headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = "No items found",
    noinline itemsPageProvider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)? = null,
) {
    val updatedItemsPageProvider by rememberUpdatedState(itemsPageProvider)

    val lazyListState = rememberLazyListState()

    var itemsPage by persist<Innertube.ItemsPage<T>?>(tag)
    var hasScrolledToTop by remember { mutableStateOf(false) }
    var isInitialLoad by remember { mutableStateOf(true) }

    LaunchedEffect(lazyListState, updatedItemsPageProvider) {
        val currentItemsPageProvider = updatedItemsPageProvider ?: return@LaunchedEffect

        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" } }
            .collect { shouldLoadMore ->
                if (!shouldLoadMore) return@collect

                withContext(Dispatchers.IO) {
                    currentItemsPageProvider(itemsPage?.continuation)
                }?.onSuccess {
                    if (it == null) {
                        if (itemsPage == null) {
                            itemsPage = Innertube.ItemsPage(null, null)
                        }
                    } else {
                        itemsPage += it
                    }
                }?.exceptionOrNull()?.printStackTrace()
            }
    }

    LaunchedEffect(itemsPage, updatedItemsPageProvider) {
        if (itemsPage == null && updatedItemsPageProvider != null) {
            withContext(Dispatchers.IO) {
                updatedItemsPageProvider?.invoke(null)
            }?.onSuccess {
                if (it == null) {
                    itemsPage = Innertube.ItemsPage(null, null)
                } else {
                    itemsPage = it
                }
            }?.exceptionOrNull()?.printStackTrace()
        }
    }

    LaunchedEffect(itemsPage?.items?.isNotEmpty()) {
        if (itemsPage?.items?.isNotEmpty() == true && !hasScrolledToTop) {
            lazyListState.scrollToItem(0)
            hasScrolledToTop = true
        }
    }

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if( NavigationBarPosition.Right.isCurrent() )
                    Dimensions.contentWidthRightBar
                else
                    1f
            )
    ) {
        if (itemsPage == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    headerContent(null)
                }
                items(initialPlaceholderCount) {
                    itemPlaceholderContent()
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = modifier
                    .fillMaxSize()
            ) {
                item(
                    key = "header",
                    contentType = "header",
                ) {
                    headerContent(null)
                }

                items(
                    items = itemsPage?.items ?: emptyList(),
                    key = Innertube.Item::key,
                    itemContent = itemContent
                )

                if (itemsPage != null && itemsPage?.items.isNullOrEmpty()) {
                    item(key = "empty") {
                        BasicText(
                            text = emptyItemsText,
                            style = typography().xs.secondary.center,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 32.dp)
                                .fillMaxWidth()
                        )
                    }
                }

                if (!(itemsPage != null && itemsPage?.continuation == null)) {
                    item(key = "loading") {
                        val isFirstLoad = itemsPage?.items.isNullOrEmpty()
                        Column {
                            repeat(if (isFirstLoad) initialPlaceholderCount else continuationPlaceholderCount) {
                                itemPlaceholderContent()
                            }
                        }
                    }
                }

                item(
                    key = "footer",
                    contentType = 0,
                ) {
                    Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}

@ExperimentalAnimationApi
@Composable
inline fun <T : Innertube.Item> ItemsGridPage(
    tag: String,
    crossinline headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    noinline itemContent: @Composable androidx.compose.foundation.lazy.grid.LazyGridItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = "No items found",
    noinline itemsPageProvider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)? = null,
    thumbnailSizeDp: androidx.compose.ui.unit.Dp
) {
    val updatedItemsPageProvider by rememberUpdatedState(itemsPageProvider)
    val lazyGridState = rememberLazyGridState()
    var itemsPage by persist<Innertube.ItemsPage<T>?>(tag)
    var hasScrolledToTop by remember { mutableStateOf(false) }

    LaunchedEffect(lazyGridState, updatedItemsPageProvider) {
        val currentItemsPageProvider = updatedItemsPageProvider ?: return@LaunchedEffect
        snapshotFlow { lazyGridState.layoutInfo.visibleItemsInfo.any { it.key == "loading" } }
            .collect { shouldLoadMore ->
                if (!shouldLoadMore) return@collect
                withContext(Dispatchers.IO) {
                    currentItemsPageProvider(itemsPage?.continuation)
                }?.onSuccess {
                    if (it == null) {
                        if (itemsPage == null) {
                            itemsPage = Innertube.ItemsPage(null, null)
                        }
                    } else {
                        itemsPage += it
                    }
                }?.exceptionOrNull()?.printStackTrace()
            }
    }

    LaunchedEffect(itemsPage, updatedItemsPageProvider) {
        if (itemsPage == null && updatedItemsPageProvider != null) {
            withContext(Dispatchers.IO) {
                updatedItemsPageProvider?.invoke(null)
            }?.onSuccess {
                if (it == null) {
                    itemsPage = Innertube.ItemsPage(null, null)
                } else {
                    itemsPage = it
                }
            }?.exceptionOrNull()?.printStackTrace()
        }
    }

    LaunchedEffect(itemsPage?.items?.isNotEmpty()) {
        if (itemsPage?.items?.isNotEmpty() == true && !hasScrolledToTop) {
            lazyGridState.scrollToItem(0)
            hasScrolledToTop = true
        }
    }

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if (NavigationBarPosition.Right.isCurrent())
                    Dimensions.contentWidthRightBar
                else
                    1f
            )
    ) {
        if (itemsPage == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    headerContent(null)
                }
                items(initialPlaceholderCount) {
                    itemPlaceholderContent()
                }
            }
        } else {
            LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Adaptive(thumbnailSizeDp),
                contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
                modifier = modifier
                    .background(colorPalette().background0)
                    .fillMaxSize()
            ) {
                item(
                    key = "header",
                    contentType = 0,
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    headerContent(null)
                }

                items(
                    itemsPage?.items ?: emptyList(),
                    key = { it.key },
                    itemContent = itemContent
                )

                if (itemsPage != null && itemsPage?.items.isNullOrEmpty()) {
                    item(
                        key = "empty",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        BasicText(
                            text = emptyItemsText,
                            style = typography().xs.secondary.center,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 32.dp)
                                .fillMaxWidth()
                        )
                    }
                }

                if (!(itemsPage != null && itemsPage?.continuation == null)) {
                    item(
                        key = "loading",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        val isFirstLoad = itemsPage?.items.isNullOrEmpty()
                        Column {
                            repeat(if (isFirstLoad) initialPlaceholderCount else continuationPlaceholderCount) {
                                itemPlaceholderContent()
                            }
                        }
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyGridState = lazyGridState)
    }
}
