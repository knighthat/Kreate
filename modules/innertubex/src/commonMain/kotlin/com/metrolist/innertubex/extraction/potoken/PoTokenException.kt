package com.metrolist.innertubex.extraction.potoken

/** Controlled failure while parsing or preparing a page-bound PO token request. */
public class PoTokenException(
    message: String,
) : IllegalStateException(message)
