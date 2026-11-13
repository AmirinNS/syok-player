package io.github.amirinns.syok.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads embedded audio artwork off the main thread with downsampling
 * and a bounded memory cache. Uses platform [MediaMetadataRetriever] and
 * closes it after extraction.
 */
class ArtworkLoader(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    maxCacheBytes: Int = ((Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtMost(32 * 1024 * 1024))
) {

    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    /**
     * Loads embedded artwork for the specified content URI.
     *
     * @param uri The content URI pointing to the audio file.
     * @param targetWidth Target width for downsampling (default 512px).
     * @param targetHeight Target height for downsampling (default 512px).
     * @return Decoded Bitmap, or null if no embedded picture or on malformed data.
     */
    suspend fun loadArtwork(
        uri: Uri,
        targetWidth: Int = 512,
        targetHeight: Int = 512
    ): Bitmap? = withContext(ioDispatcher) {
        val cacheKey = "$uri@${targetWidth}x$targetHeight"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val pictureBytes = retriever.embeddedPicture ?: return@withContext null

            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return@withContext null
            }

            options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
            options.inJustDecodeBounds = false

            val bitmap = BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, options)
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
            }
            bitmap
        } catch (_: Exception) {
            // Malformed artwork or read error: return null (caller uses placeholder)
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Loads artwork given a content URI string.
     */
    suspend fun loadArtwork(contentUriString: String, targetWidth: Int = 512, targetHeight: Int = 512): Bitmap? {
        return try {
            val uri = Uri.parse(contentUriString)
            loadArtwork(uri, targetWidth, targetHeight)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun readTags(uri: Uri): AudioTags = withContext(ioDispatcher) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun tag(key: Int): String? = retriever.extractMetadata(key)
                ?.takeIf { it.isNotBlank() && it != "<unknown>" }
            fun number(key: Int): Int? = tag(key)?.substringBefore('/')?.toIntOrNull()
            AudioTags(
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                trackNumber = number(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.coerceAtLeast(0) ?: 0,
                discNumber = number(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.coerceAtLeast(1) ?: 1,
                durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0) ?: 0
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
    }

    companion object {
        fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            val height = options.outHeight
            val width = options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2

                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            return inSampleSize.coerceAtLeast(1)
        }
    }
}
