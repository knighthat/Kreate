package app.kreate.android.utils

import android.content.ContentResolver
import android.net.Uri


/**
 * Verifies that this [Uri] instance is
 * pointing to a local file by checking
 * its scheme.
 */
fun Uri.isLocalFile() =
    scheme.equals( ContentResolver.SCHEME_CONTENT, true )
            || scheme.equals( ContentResolver.SCHEME_FILE, true )