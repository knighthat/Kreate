package app.kreate.annotations


/**
 * Denotes a getter returns a string that may be localized based on the current request context.
 * The returned value should not be assumed to be constant or language-neutral
 */
@Target(AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.SOURCE)
annotation class Localized
