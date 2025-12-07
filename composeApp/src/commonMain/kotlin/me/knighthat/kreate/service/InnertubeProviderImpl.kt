package me.knighthat.kreate.service

import io.ktor.client.HttpClient
import me.knighthat.innertube.Constants
import me.knighthat.innertube.Innertube
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


class InnertubeProviderImpl: Innertube.KtorProvider, KoinComponent {

    override val client: HttpClient by inject<HttpClient>()
    override val cookies: String = ""
    override val dataSyncId: String? = null
    override val visitorData: String = Constants.CHROME_WINDOWS_VISITOR_DATA
}