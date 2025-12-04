package me.knighthat.kreate.di

import android.os.Parcel
import android.os.Parcelable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class TopLayoutConfiguration {

    val lazyListState: LazyListState = LazyListState()

    var title: String by mutableStateOf( "" )
    var background: String? by mutableStateOf( null )
    var isAppReady: Boolean by mutableStateOf( false )
        private set

    init {
        /**
         * Replaces current title with whatever key of [Title]
         * in a [androidx.compose.foundation.lazy.LazyColumn] that uses [lazyListState]
         */
        val coroutineScope = CoroutineScope(Dispatchers.Main.immediate)
        coroutineScope.launch {
            snapshotFlow { lazyListState.firstVisibleItemIndex }
                .mapNotNull { _ ->
                    lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? Title
                }
                .collect { title ->
                    this@TopLayoutConfiguration.title = title.title
                }
        }
    }

    /**
     * Mark the initialization process is over.
     * Meaning, it can now display content to user.
     */
    fun showContent() {
        isAppReady = true
    }


    class Title( val title: String ): Parcelable {

        constructor(parcel: Parcel) : this(parcel.readString()!!)

        override fun describeContents(): Int = 0
        override fun writeToParcel(dest: Parcel, flags: Int) = dest.writeString( title )

        companion object CREATOR : Parcelable.Creator<Title> {

            override fun createFromParcel(parcel: Parcel): Title = Title(parcel)
            override fun newArray(size: Int): Array<Title?> = arrayOfNulls(size)
        }
    }
}