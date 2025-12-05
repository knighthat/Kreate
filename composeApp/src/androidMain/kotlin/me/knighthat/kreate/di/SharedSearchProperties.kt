package me.knighthat.kreate.di

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.MutableStateFlow


class SharedSearchProperties {

    val input = MutableStateFlow(TextFieldValue())
}