package app.kreate.util


fun durationToMillis(duration: String): Long {
    if( duration.isBlank() || !duration.contains( ":" ) )
        return 0

    val parts = duration.split(":")
    if (parts.size == 3){
        val hours = parts[0].toLong()
        val minutes = parts[1].toLong()
        val seconds = parts[2].toLong()
        return hours * 3600000 + minutes * 60000 + seconds * 1000
    } else {
        val minutes = parts[0].toLong()
        val seconds = parts[1].toLong()
        return minutes * 60000 + seconds * 1000
    }
}

fun durationTextToMillis( duration: String ): Long {
    return try {
        durationToMillis( duration )
    } catch ( _: Exception ) {
        0L
    }
}