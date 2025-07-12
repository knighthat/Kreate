package it.fast4x.rimusic.enums

import androidx.compose.runtime.Composable
import app.kreate.android.R
import it.fast4x.rimusic.appContext
import me.knighthat.enums.TextView
import java.util.Locale

/**
 * A list of supported languages, each [code]
 * must correspond to a valid `res/values-*` directory.
 *
 * [code] but adhere to [BCP 47](https://tools.ietf.org/html/bcp47) -
 * IETF standard for language tags. TL;DR `language[-script][-region][-variant]`
 *
 *
 * @param code 2 (or 3) letters language code
 */
enum class Languages(
    val code: String
): TextView {

    System( "system" ),
    Afrikaans( "af" ),
    Arabic( "az" ),
    Azerbaijani( "ar" ),
    Bashkir( "ba" ),
    Bengali( "bn" ),
    Catalan( "ca" ),
    ChineseSimplified( "zh-CN" ),
    ChineseTraditional( "zh-TW" ),
    Czech( "cs" ),
    Danish( "da" ),
    Dutch( "nl" ),
    English( "en" ),
    Esperanto( "eo" ),
    Estonian( "et" ),
    Filipino( "fil" ),
    Finnish( "fi" ),
    French( "fr" ),
    Galician( "gl" ),
    German( "de" ),
    Greek( "el" ),
    Hebrew( "he" ),
    Hindi( "hi" ),
    Hungarian( "hu" ),
    Indonesian( "id" ),
    Interlingua( "ia" ),
    Irish( "ga" ),
    Italian( "it" ),
    Japanese( "ja" ),
    Korean( "ko" ),
    Malayalam( "ml" ),
    Norwegian( "no" ),
    Odia( "or" ),
    Persian( "fa" ),
    Polish( "pl" ),
    Portuguese( "pt" ),
    PortugueseBrazilian( "pt-BR" ),
    Romanian( "ro" ),
    Russian( "ru" ),
    SerbianCyrillic( "sr-Cyrl" ),
    SerbianLatin( "sr-Latn" ),
    Sinhala( "si" ),
    Spanish( "es" ),
    Swedish( "sv" ),
    Tamil( "ta" ),
    Telugu( "te" ),
    Turkish( "tr" ),
    Ukrainian( "uk" ),
    Vietnamese( "vi" );

    @get:Composable
    override val text: String by lazy {
        if( this == System )
            return@lazy appContext().getString( R.string.system_language )

        val parts = code.split( "-", "_" )
        val locale = when( parts.size ) {
            1 -> Locale(parts[0])
            2 -> {
                // Both Cyrillic and Latin of Serbian are scripts, not region
                if( parts[0] == "sr" )
                    Locale.Builder()
                          .setLanguage( parts[0] )
                          .setScript( parts[1] )
                          .build()
                else
                    Locale(parts[0], parts[1])
            }
            else -> throw UnsupportedOperationException("Unsupported locale parts: $parts")
        }
        locale.getDisplayName( locale )
    }
}

enum class Countries {
    ZZ,
    AR,
    DZ,
    AU,
    AT,
    AZ,
    BH,
    BD,
    BY,
    BE,
    BO,
    BA,
    BR,
    BG,
    KH,
    CA,
    CL,
    HK,
    CO,
    CR,
    HR,
    CY,
    CZ,
    DK,
    DO,
    EC,
    EG,
    SV,
    EE,
    FI,
    FR,
    GE,
    DE,
    GH,
    GR,
    GT,
    HN,
    HU,
    IS,
    IN,
    ID,
    IQ,
    IE,
    IL,
    IT,
    JM,
    JP,
    JO,
    KZ,
    KE,
    KR,
    KW,
    LA,
    LV,
    LB,
    LY,
    LI,
    LT,
    LU,
    MK,
    MY,
    MT,
    MX,
    ME,
    MA,
    NP,
    NL,
    NZ,
    NI,
    NG,
    NO,
    OM,
    PK,
    PA,
    PG,
    PY,
    PE,
    PH,
    PL,
    PT,
    PR,
    QA,
    RO,
    RU,
    SA,
    SN,
    RS,
    SG,
    SK,
    SI,
    ZA,
    ES,
    LK,
    SE,
    CH,
    TW,
    TZ,
    TH,
    TN,
    TR,
    UG,
    UA,
    AE,
    GB,
    US,
    UY,
    VE,
    VN,
    YE,
    ZW;

    val countryName: String
        get() = when (this) {
            ZZ -> "Global"
            AR -> "Argentina"
            DZ -> "Algeria"
            AU -> "Australia"
            AT -> "Austria"
            AZ -> "Azerbaijan"
            BH -> "Bahrain"
            BD -> "Bangladesh"
            BY -> "Belarus"
            BE -> "Belgium"
            BO -> "Bolivia"
            BA -> "Bosnia and Herzegovina"
            BR -> "Brazil"
            BG -> "Bulgaria"
            KH -> "Cambodia"
            CA -> "Canada"
            CL -> "Chile"
            HK -> "Hong Kong"
            CO -> "Colombia"
            CR -> "Costa Rica"
            HR -> "Croatia"
            CY -> "Cyprus"
            CZ -> "Czech Republic"
            DK -> "Denmark"
            DO -> "Dominican Republic"
            EC -> "Ecuador"
            EG -> "Egypt"
            SV -> "El Salvador"
            EE -> "Es->nia"
            FI -> "Finland"
            FR -> "France"
            GE -> "Georgia"
            DE -> "Germany"
            GH -> "Ghana"
            GR -> "Greece"
            GT -> "Guatemala"
            HN -> "Honduras"
            HU -> "Hungary"
            IS -> "Iceland"
            IN -> "India"
            ID -> "Indonesia"
            IQ -> "Iraq"
            IE -> "Ireland"
            IL -> "Israel"
            IT -> "Italy"
            JM -> "Jamaica"
            JP -> "Japan"
            JO -> "Jordan"
            KZ -> "Kazakhstan"
            KE -> "Kenya"
            KR -> "South Korea"
            KW -> "Kuwait"
            LA -> "Lao"
            LV -> "Latvia"
            LB -> "Lebanon"
            LY -> "Libya"
            LI -> "Liechtenstein"
            LT -> "Lithuania"
            LU -> "Luxembourg"
            MK -> "Macedonia"
            MY -> "Malaysia"
            MT -> "Malta"
            MX -> "Mexico"
            ME -> "Montenegro"
            MA -> "Morocco"
            NP -> "Nepal"
            NL -> "Netherlands"
            NZ -> "New Zealand"
            NI -> "Nicaragua"
            NG -> "Nigeria"
            NO -> "Norway"
            OM -> "Oman"
            PK -> "Pakistan"
            PA -> "Panama"
            PG -> "Papua New Guinea"
            PY -> "Paraguay"
            PE -> "Peru"
            PH -> "Philippines"
            PL -> "Poland"
            PT -> "Portugal"
            PR -> "Puer-> Rico"
            QA -> "Qatar"
            RO -> "Romania"
            RU -> "Russian Federation"
            SA -> "Saudi Arabia"
            SN -> "Senegal"
            RS -> "Serbia"
            SG -> "Singapore"
            SK -> "Slovakia"
            SI -> "Slovenia"
            ZA -> "South Africa"
            ES -> "Spain"
            LK -> "Sri Lanka"
            SE -> "Sweden"
            CH -> "Switzerland"
            TW -> "Taiwan"
            TZ -> "Tanzania"
            TH -> "Thailand"
            TN -> "Tunisia"
            TR -> "Turkey"
            UG -> "Uganda"
            UA -> "Ukraine"
            AE -> "United Arab Emirates"
            GB -> "United Kingdom"
            US -> "United States"
            UY -> "Uruguay"
            VE -> "Venezuela (Bolivarian Republic)"
            VN -> "Vietnam"
            YE -> "Yemen"
            ZW -> "Zimbabwe"
        }
}