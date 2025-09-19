package me.knighthat.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileOutputStream
import java.io.IOException
import kotlin.contracts.ExperimentalContracts
import kotlin.io.path.createTempFile
import kotlin.math.roundToInt

object ImageProcessor {

    private fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight: Int = srcHeight / 2
            val halfWidth: Int = srcWidth / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than or equal to the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth)
                inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun queryImageInfo( contentResolver: ContentResolver, artworkUri: Uri ): Triple<Int, Int, Long> {
        var srcWidth = 0
        var srcHeight = 0
        var srcSize = 0L

        // FIXME: Error "invalid column_size" happens a lot
        runCatching {
            contentResolver.query( artworkUri, arrayOf( OpenableColumns.SIZE ), null, null, null )
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex( OpenableColumns.SIZE )
                        if ( sizeIndex != -1 )
                            srcSize = cursor.getLong( sizeIndex )
                    }
                }
        }

        contentResolver.openInputStream( artworkUri )
            ?.use { inStream ->
                // This option should only read metadata, not the whole file
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = srcSize > 0L }
                BitmapFactory.decodeStream( inStream, null, options )

                if (options.outWidth != -1 && options.outHeight != -1) {
                    srcWidth = options.outWidth
                    srcHeight = options.outHeight
                }

                if( srcSize == 0L )
                    srcSize = inStream.readBytes().size.toLong()
            }

        return Triple(srcWidth, srcHeight, srcSize)
    }

    /**
     * Processes an image from a given Uri:
     *
     * a. Verifies if it's an image by its MIME type.
     * b. Checks if dimensions exceed MAX_DIMENSION or file size exceeds MAX_FILE_SIZE_BYTES.
     * c. If limits are exceeded, scales and compresses the image to meet specs.
     * d. If not an image, throws IllegalArgumentException.
     *
     * @param context The application context
     * @param artworkUri The Uri of the image file (must be a local path)
     * @return A Uri to the processed image (original local, compressed/scaled local, or original remote Uri)
     * @throws IllegalArgumentException if [artworkUri] is not a local path,
     * or the local Uri does not point to an image or local processing fails
     * @throws SecurityException due to lack of permission
     * @throws IOException other read/write related issues
     * @throws OutOfMemoryError when heap is overflown
     */
    @OptIn(ExperimentalContracts::class)
    @Throws(
        IllegalArgumentException::class,
        SecurityException::class,
        IOException::class,
        OutOfMemoryError::class
    )
    fun compressArtwork( context: Context, artworkUri: Uri, maxWidth: Int, maxHeight: Int, maxSize: Long ): Uri? {
        require(
            artworkUri.scheme.equals( ContentResolver.SCHEME_CONTENT, true )
                    && artworkUri.scheme.equals( ContentResolver.SCHEME_FILE, true )
        ) { "$artworkUri is NOT a local file!" }

        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType( artworkUri )
        if ( mimeType == null || !mimeType.startsWith( "image/" ) )
            throw IllegalArgumentException("The provided URI does not point to an image file. MIME type: $mimeType")

        val (originalWidth, originalHeight, originalFileSize) = queryImageInfo( contentResolver, artworkUri )
        val needsResizing = originalWidth > maxWidth || originalHeight > maxHeight
        val needsCompression = originalFileSize > maxSize
        if ( !needsResizing && !needsCompression ) return artworkUri

        // Step 5: Process and compress if local limits exceeded
        try {
            // Determine the target dimensions
            val scale = if( needsResizing )
                minOf( maxWidth.toFloat() / originalWidth, maxHeight.toFloat() / originalHeight )
            else
                1f
            val targetWidth = (originalWidth * scale).roundToInt()
            val targetHeight = (originalHeight * scale).roundToInt()

            val decodedBitmap = contentResolver.openInputStream( artworkUri )!!.use { inputStream ->
                // Calculate inSampleSize based on target dimensions for decoding
                val decodeOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inSampleSize = calculateInSampleSize(originalWidth, originalHeight, targetWidth, targetHeight)
                }

                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }
            if (decodedBitmap == null)
                throw IllegalArgumentException("Failed to decode image into Bitmap from URI: $artworkUri")

            val outputFile = createTempFile( "ImageProcessor_compressArtwork_${System.currentTimeMillis()}" ).toFile()
            outputFile.deleteOnExit()

            FileOutputStream(outputFile).use { outStream ->
                val success = decodedBitmap.compress(Bitmap.CompressFormat.PNG, 70, outStream)
                decodedBitmap.recycle() // Release memory

                if ( !success )
                    throw IOException("Failed to compress image to file: ${outputFile.absolutePath}")
            }

            return Uri.fromFile( outputFile )

        } catch (e: OutOfMemoryError) {
            System.gc()
            throw e
        }
    }
}