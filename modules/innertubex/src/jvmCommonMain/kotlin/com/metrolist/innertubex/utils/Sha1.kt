package com.metrolist.innertubex.utils

import java.security.MessageDigest

public actual fun sha1(input: String): String = MessageDigest.getInstance("SHA-1").digest(input.toByteArray()).toHexString()
