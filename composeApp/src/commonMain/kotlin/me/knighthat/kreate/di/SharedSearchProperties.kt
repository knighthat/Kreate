package me.knighthat.kreate.di

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import me.knighthat.kreate.constant.SearchTab

class SharedSearchProperties {

    val input = MutableStateFlow(TextFieldValue())
    val tab = MutableStateFlow(SearchTab.SONGS)
}