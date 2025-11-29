package me.knighthat.kreate.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale


object TimeDateUtils {

    fun logFileName(): DateFormat =
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
}