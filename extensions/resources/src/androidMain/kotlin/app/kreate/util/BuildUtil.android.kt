package app.kreate.util


private var versionName: String? = null
private var flavorArch: String? = null
private var flavorEnv: String? = null

val VERSION_NAME: String
    get() = versionName!!
val FLAVOR_ARCH: String
    get() = flavorArch!!
val FLAVOR_ENV: String
    get() = flavorEnv!!

/**
 * This function can only be called once,
 * subsequence calls result in [IllegalStateException].
 */
fun setVersionName( name: String ) {
    check( versionName == null )
    versionName = name
}

/**
 * This function can only be called once,
 * subsequence calls result in [IllegalStateException].
 */
fun setFlavorArch( arch: String ) {
    check( flavorArch == null )
    flavorArch = arch
}

/**
 * This function can only be called once,
 * subsequence calls result in [IllegalStateException].
 */
fun setFlavorEnv( env: String ) {
    check( flavorEnv == null )
    flavorEnv = env
}
