package app.kreate.constant

import androidx.compose.runtime.Composable
import app.kreate.util.getSystemCountryCode
import app.kreate.util.getSystemLanguageCode
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.locale_system
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
enum class Language {

    SYSTEM,
    AFRIKAANS,
    ARABIC,
    AZERBAIJANI,
    BASHKIR,
    BENGALI,
    CATALAN,
    CHINESE_SIMPLIFIED,
    CHINESE_TRADITIONAL,
    CZECH,
    DANISH,
    DUTCH,
    ENGLISH,        // This language doesn't have `values-en` dir because it uses default values (which is English by itself)
    ESPERANTO,
    ESTONIAN,
    FILIPINO,
    FINNISH,
    FRENCH,
    GALICIAN,
    GERMAN,
    GREEK,
    HEBREW,
    HINDI,
    HUNGARIAN,
    INDONESIAN,
    INTERLINGUA,
    IRISH,
    ITALIAN,
    JAPANESE,
    KOREAN,
    MALAYALAM,
    NORWEGIAN,
    ODIA,
    PERSIAN,
    POLISH,
    PORTUGUESE,
    PORTUGUESE_BRAZILIAN,
    ROMANIAN,
    RUSSIAN,
    SERBIAN_CYRILLIC,
    SERBIAN_LATIN,
    SINHALA,
    SPANISH,
    SWEDISH,
    TAMIL,
    TELUGU,
    TURKISH,
    UKRAINIAN,
    VIETNAMESE;

    val languageName: String
        @Composable
        get() {
            if( this === SYSTEM )
                return stringResource( Res.string.locale_system )

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

    /**
     * Returns the country/region code for this locale, which should either be the empty string,
     * an uppercase ISO 3166 2-letter code, or a UN M.49 3-digit code.
     */
    val code: String
        get() = when( this ) {
            SYSTEM                  -> getSystemLanguageCode()
            AFRIKAANS               -> "af"
            ARABIC                  -> "ar"
            AZERBAIJANI             -> "az"
            BASHKIR                 -> "ba"
            BENGALI                 -> "bn"
            CATALAN                 -> "ca"
            CHINESE_SIMPLIFIED      -> "zh"
            CHINESE_TRADITIONAL     -> "zh"
            CZECH                   -> "cs"
            DANISH                  -> "da"
            DUTCH                   -> "nl"
            ENGLISH                 -> "en"
            ESPERANTO               -> "eo"
            ESTONIAN                -> "et"
            FILIPINO                -> "fil"
            FINNISH                 -> "fi"
            FRENCH                  -> "fr"
            GALICIAN                -> "gl"
            GERMAN                  -> "de"
            GREEK                   -> "el"
            HEBREW                  -> "iw"
            HINDI                   -> "hi"
            HUNGARIAN               -> "hu"
            INDONESIAN              -> "in"
            INTERLINGUA             -> "ia"
            IRISH                   -> "ga"
            ITALIAN                 -> "it"
            JAPANESE                -> "ja"
            KOREAN                  -> "ko"
            MALAYALAM               -> "ml"
            NORWEGIAN               -> "no"
            ODIA                    -> "or"
            PERSIAN                 -> "fa"
            POLISH                  -> "pl"
            PORTUGUESE              -> "pt"
            PORTUGUESE_BRAZILIAN    -> "pt"
            ROMANIAN                -> "ro"
            RUSSIAN                 -> "ru"
            SERBIAN_CYRILLIC        -> "sr"
            SERBIAN_LATIN           -> "sr"
            SINHALA                 -> "si"
            SPANISH                 -> "es"
            SWEDISH                 -> "sv"
            TAMIL                   -> "ta"
            TELUGU                  -> "te"
            TURKISH                 -> "tr"
            UKRAINIAN               -> "uk"
            VIETNAMESE              -> "vi"
        }

    /**
     * Returns the language code for active locale, which should either be the empty string,
     * a lowercase ISO 639-1 2-letter code.
     *
     * Result prioritizes app's specific language, returns system's language if
     * app's language is undetermined.
     */
    val region: String
        get() = when( this ) {
            SYSTEM                  -> getSystemCountryCode()
            AFRIKAANS               -> "ZA"
            ARABIC                  -> "SA"
            AZERBAIJANI             -> "AZ"
            BASHKIR                 -> "RU"
            BENGALI                 -> "BD"
            CATALAN                 -> "ES"
            CHINESE_SIMPLIFIED      -> "CN"
            CHINESE_TRADITIONAL     -> "TW"
            CZECH                   -> "CZ"
            DANISH                  -> "DK"
            DUTCH                   -> "NL"
            ENGLISH                 -> "US"
            ESPERANTO               -> "UY"
            ESTONIAN                -> "EE"
            FILIPINO                -> "PH"
            FINNISH                 -> "FI"
            FRENCH                  -> "FR"
            GALICIAN                -> "ES"
            GERMAN                  -> "DE"
            GREEK                   -> "GR"
            HEBREW                  -> "IL"
            HINDI                   -> "IN"
            HUNGARIAN               -> "HU"
            INDONESIAN              -> "ID"
            // Doesn't have default region, use system's
            INTERLINGUA             -> getSystemCountryCode()
            IRISH                   -> "IE"
            ITALIAN                 -> "IT"
            JAPANESE                -> "JP"
            KOREAN                  -> "KR"
            MALAYALAM               -> "IN"
            NORWEGIAN               -> "NO"
            ODIA                    -> "IN"
            PERSIAN                 -> "IR"
            POLISH                  -> "PL"
            PORTUGUESE              -> "PT"
            PORTUGUESE_BRAZILIAN    -> "BR"
            ROMANIAN                -> "RO"
            RUSSIAN                 -> "RU"
            SERBIAN_CYRILLIC        -> "SP"
            SERBIAN_LATIN           -> "CS"
            SINHALA                 -> "LK"
            SPANISH                 -> "ES"
            SWEDISH                 -> "SE"
            TAMIL                   -> "IN"
            TELUGU                  -> "IN"
            TURKISH                 -> "TR"
            UKRAINIAN               -> "UA"
            VIETNAMESE              -> "VN"
        }

    fun toLocale(): Locale =
        if( this !== SYSTEM ) {
            val builder = Locale.Builder()
            builder.setLanguage( this.code )

            if( this === SERBIAN_CYRILLIC )
                builder.setScript( "Cyrl" )
            else if( this === SERBIAN_LATIN )
                builder.setScript( "Latn" )

            if( this !== SERBIAN_CYRILLIC )
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