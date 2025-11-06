package app.kreate.constant

import androidx.compose.runtime.Composable
import me.knighthat.kreate.composeapp.generated.resources.Res
import me.knighthat.kreate.composeapp.generated.resources.system
import org.jetbrains.compose.resources.stringResource
import java.util.Locale
import me.bush.translator.Language as TranslatorLanguage


/**
 * List of supported languages.
 *
 * Languages follow BCP 47 standard with:
 * - 2-letter language code ISO 639-1
 * - 2-letter (uppercase) country code ISO 3166-1 alpha-2
 */
enum class Language(
    val code: String,
    val region: String
) {

    SYSTEM("", ""),

    AFRIKAANS("af", "ZA"),

    ARABIC("ar", "SA"),

    AZERBAIJANI("az", "AZ"),

    BASHKIR("ba", "RU"),

    BENGALI("bn", "BD"),

    CATALAN("ca", "ES"),

    CHINESE_SIMPLIFIED("zh", "CN"),

    CHINESE_TRADITIONAL("zh", "TW"),

    CZECH("cs", "CZ"),

    DANISH("da", "DK"),

    DUTCH("nl", "NL"),

    ENGLISH("en", "US"),        // This language doesn't have `values-en` dir because it uses default values (which is English by itself)

    ESPERANTO("eo", "UY"),

    ESTONIAN("et", "EE"),

    FILIPINO("fil", "PH"),

    FINNISH("fi", "FI"),

    FRENCH("fr", "FR"),

    GALICIAN("gl", "ES"),

    GERMAN("de", "DE"),

    GREEK("el", "GR"),

    HEBREW("iw", "IL"),

    HINDI("hi", "IN"),

    HUNGARIAN("hu", "HU"),

    INDONESIAN("in", "ID"),

    INTERLINGUA("ia", ""),      // Doesn't have default region

    IRISH("ga", "IE"),

    ITALIAN("it", "IT"),

    JAPANESE("ja", "JP"),

    KOREAN("ko", "KR"),

    MALAYALAM("ml", "IN"),

    NORWEGIAN("no", "NO"),

    ODIA("or", "IN"),

    PERSIAN("fa", "IR"),

    POLISH("pl", "PL"),

    PORTUGUESE("pt", "PT"),

    PORTUGUESE_BRAZILIAN("pt", "BR"),

    ROMANIAN("ro", "RO"),

    RUSSIAN("ru", "RU"),

    SERBIAN_CYRILLIC("sr", "SP"),

    SERBIAN_LATIN("sr", "CS"),

    SINHALA("si", "LK"),

    SPANISH("es", "ES"),

    SWEDISH("sv", "SE"),

    TAMIL("ta", "IN"),

    TELUGU("te", "IN"),

    TURKISH("tr", "TR"),

    UKRAINIAN("uk", "UA"),

    VIETNAMESE("vi", "VN");

    val languageName: String
        @Composable
        get() {
            if( this === SYSTEM )
                return stringResource( Res.string.system )

            val locale = this.toLocale()
            return locale.getDisplayLanguage( locale )
        }
    val displayName: String
        @Composable
        get() {
            if( this === SYSTEM )
                return languageName

            val locale = this.toLocale()
            return locale.getDisplayName( locale )
        }

    fun toLocale(): Locale =
        if( this !== SYSTEM ) {
            val builder = Locale.Builder()
            builder.setLanguage( this.code )

            if( this === SERBIAN_CYRILLIC )
                builder.setScript( "Cyrl" )
            else if( this === SERBIAN_LATIN )
                builder.setScript( "Latn" )
            else
                builder.setRegion( this.region )

            builder.build()
        } else
            Locale.getDefault()

    fun toTranslatorLanguage(): TranslatorLanguage =
        runCatching { TranslatorLanguage( this.code ) }
            .fold(
                onSuccess = { it },
                // TODO: Log failure reason
                onFailure = { TranslatorLanguage.ENGLISH }
            )
}