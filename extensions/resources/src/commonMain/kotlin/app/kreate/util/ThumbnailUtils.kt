package app.kreate.util


fun String?.thumbnail(size: Int): String? =
    when {
        this?.startsWith("https://lh3.googleusercontent.com") == true -> "$this-w$size-h$size"
        this?.startsWith("https://yt3.ggpht.com") == true -> "$this-w$size-h$size-s$size"
        this?.startsWith("https://yt3.googleusercontent.com") == true -> "$this-w$size-h$size-s$size"
        else -> this
    }