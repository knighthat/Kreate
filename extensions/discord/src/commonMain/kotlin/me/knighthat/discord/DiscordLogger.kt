package me.knighthat.discord

import com.my.kizzy.domain.interfaces.Logger
import co.touchlab.kermit.Logger as Kermit


internal object DiscordLogger : Logger {

    private const val LOGGING_TAG = "Discord"

    override fun clear() = Kermit.v( NotImplementedError(), LOGGING_TAG ) { "Clear called but not implemented" }

    override fun i(tag: String, event: String) = Kermit.i( tag = "$LOGGING_TAG-$tag") { event }

    override fun e(tag: String, event: String) = Kermit.e( tag = "$LOGGING_TAG-$tag") { event }

    override fun d(tag: String, event: String) = Kermit.d( tag = "$LOGGING_TAG-$tag") { event }

    override fun w(tag: String, event: String) = Kermit.w( tag = "$LOGGING_TAG-$tag") { event }
}