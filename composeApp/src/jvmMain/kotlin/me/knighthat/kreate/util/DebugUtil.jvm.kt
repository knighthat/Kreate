package me.knighthat.kreate.util


actual val isDebug: Boolean = System.getenv( "DEBUG_MODE" ).equals( "true", true )