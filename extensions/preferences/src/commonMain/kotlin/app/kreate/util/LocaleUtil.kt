package app.kreate.util


/**
 * Returns the country/region code for this locale, which should either be the empty string,
 * an uppercase ISO 3166 2-letter code, or a UN M.49 3-digit code.
 */
expect fun getSystemCountryCode(): String

/**
 * Returns the language code for active locale, which should either be the empty string,
 * a lowercase ISO 639-1 2-letter code.
 *
 * Result prioritizes app's specific language, returns system's language if
 * app's language is undetermined.
 */
expect fun getSystemLanguageCode(): String