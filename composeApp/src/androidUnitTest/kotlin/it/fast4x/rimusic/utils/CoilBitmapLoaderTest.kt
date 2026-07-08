package it.fast4x.rimusic.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CoilBitmapLoaderTest {

    private val uri = Uri.parse( "https://example.com/artwork.png" )

    @Test
    fun copyForMediaSessionReturnsIndependentBitmap() {
        val source = Bitmap.createBitmap( 2, 2, Bitmap.Config.ARGB_8888 )
        source.eraseColor( Color.RED )

        val actual = source.copyForMediaSession( uri )

        assertNotSame( source, actual )
        assertFalse( actual.isRecycled )
        assertEquals( source.width, actual.width )
        assertEquals( source.height, actual.height )
        assertEquals( source.getPixel( 0, 0 ), actual.getPixel( 0, 0 ) )

        source.recycle()
        assertFalse( actual.isRecycled )

        actual.recycle()
    }

    @Test
    fun copyForMediaSessionFailsForRecycledSource() {
        val source = Bitmap.createBitmap( 1, 1, Bitmap.Config.ARGB_8888 )
        source.recycle()

        assertFailsWith<IllegalStateException> {
            source.copyForMediaSession( uri )
        }
    }

    @Test
    fun copyForMediaSessionFallsBackWhenCopyReturnsNull() {
        val source = Bitmap.createBitmap( 1, 1, Bitmap.Config.ARGB_8888 )

        val actual = source.copyForMediaSession( uri ) { null }

        assertSame( source, actual )
        assertFalse( actual.isRecycled )

        source.recycle()
    }

    @Test
    fun copyForMediaSessionFallsBackWhenCopyRunsOutOfMemory() {
        val source = Bitmap.createBitmap( 1, 1, Bitmap.Config.ARGB_8888 )

        val actual = source.copyForMediaSession( uri ) { throw OutOfMemoryError( "test" ) }

        assertSame( source, actual )
        assertFalse( actual.isRecycled )

        source.recycle()
    }

    @Test
    fun copyForMediaSessionFailsWhenImageIsMissing() {
        val source: Bitmap? = null

        assertFailsWith<IllegalStateException> {
            source.copyForMediaSession( uri )
        }
    }

    @Test
    fun decodeBitmapDecodesByteArray() {
        val source = Bitmap.createBitmap( 2, 2, Bitmap.Config.ARGB_8888 )
        source.eraseColor( Color.BLUE )
        val data = ByteArrayOutputStream().use { outputStream ->
            assertTrue( source.compress( Bitmap.CompressFormat.PNG, 100, outputStream ) )
            outputStream.toByteArray()
        }

        val scope = CoroutineScope( SupervisorJob() + Dispatchers.Unconfined )
        val actual = CoilBitmapLoader( scope ).decodeBitmap( data ).get( 5, TimeUnit.SECONDS )

        assertNotSame( source, actual )
        assertFalse( actual.isRecycled )
        assertEquals( source.width, actual.width )
        assertEquals( source.height, actual.height )
        assertEquals( source.getPixel( 0, 0 ), actual.getPixel( 0, 0 ) )

        source.recycle()
        actual.recycle()
        scope.cancel()
    }
}
