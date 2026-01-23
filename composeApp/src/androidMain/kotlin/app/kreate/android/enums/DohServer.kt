package app.kreate.android.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull


enum class DohServer(
    @field:StringRes override val textId: Int,
    val url: HttpUrl?,
    vararg val address: String
) : TextView {

    NONE(R.string.doh_none, "".toHttpUrlOrNull()),

    CLOUDFLARE(
        R.string.doh_cloudflare,
        "https://cloudflare-dns.com/dns-query".toHttpUrl(),
        "1.1.1.1", "1.0.0.1",
        "2606:4700:4700::1111", "2606:4700:4700::1001"
    ),

    CLOUDFLARE_FAMILY(
        R.string.doh_cloudflare_family,
        "https://family.cloudflare-dns.com/dns-query".toHttpUrl(),
        "1.1.1.3", "1.0.0.3",
        "2606:4700:4700::1113", "2606:4700:4700::1003"
    ),

    QUAD9(
        R.string.doh_quad9,
        "https://dns.quad9.net/dns-query".toHttpUrl(),
        "9.9.9.9", "149.112.112.112",
        "2620:fe::fe", "2620:fe::9"
    ),

    QUAD9_SECURED(
        R.string.doh_quad9_secured,
        "https://dns11.quad9.net/dns-query".toHttpUrl(),
        "9.9.9.11", "149.112.112.11",
        "2620:fe::11", "2620:fe::fe:11"
    ),

    MULLVAD(
        R.string.doh_mullvad,
        "https://dns.mullvad.net/dns-query".toHttpUrl(),
        "194.242.2.2",
        "2a07:e340::2"
    ),

    MULLVAD_FAMILY(
        R.string.doh_mullvad_family,
        "https://family.dns.mullvad.net/dns-query".toHttpUrl(),
        "194.242.2.6",
        "2a07:e340::6"
    );
}