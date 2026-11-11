package io.github.amirinns.syok.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.github.amirinns.syok.SyokApplication
import io.github.amirinns.syok.data.LibraryDao
import io.github.amirinns.syok.local.ArtworkLoader
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * Narrow, read-only ContentProvider exposing small decoded audio artwork
 * to external clients (such as Android Auto) via content URIs.
 * Never leaks local file paths, internal storage boundaries, or credentials.
 */
class ArtworkContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "io.github.amirinns.syok.artwork"

        fun buildArtworkUri(trackId: String): Uri {
            return Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("track")
                .appendPath(trackId)
                .build()
        }

        fun buildArtworkUriForAlbum(albumName: String): Uri {
            return Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("album")
                .appendPath(albumName)
                .build()
        }
    }

    private fun getLibraryDao(): LibraryDao? {
        val app = context?.applicationContext as? SyokApplication
        return app?.appContainer?.libraryDao
    }

    private fun getArtworkLoader(): ArtworkLoader? {
        val app = context?.applicationContext as? SyokApplication
        return app?.appContainer?.artworkLoader
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") {
            throw SecurityException("Read-only provider")
        }

        val segments = uri.pathSegments
        if (segments.size < 2) {
            throw FileNotFoundException("Malformed artwork URI")
        }

        val type = segments[0]
        val identifier = Uri.decode(segments[1])

        val dao = getLibraryDao() ?: throw FileNotFoundException("Library unavailable")
        val loader = getArtworkLoader() ?: throw FileNotFoundException("Artwork loader unavailable")

        val bitmap: Bitmap = when (type) {
            "track" -> {
                val track = runBlocking { dao.getTrackById(identifier) }
                    ?: throw FileNotFoundException("Track not found")
                if (!track.isAvailable) {
                    throw FileNotFoundException("Track unavailable")
                }
                runBlocking { loader.loadArtwork(track.contentUri, 512, 512) }
                    ?: throw FileNotFoundException("No artwork for track")
            }
            "album" -> {
                val firstTrack = runBlocking { dao.getTracksByAlbumSync(identifier).firstOrNull() }
                    ?: throw FileNotFoundException("Album not found or empty")
                runBlocking { loader.loadArtwork(firstTrack.contentUri, 512, 512) }
                    ?: throw FileNotFoundException("No artwork for album")
            }
            else -> throw FileNotFoundException("Unsupported artwork path")
        }

        return openPipeHelper(uri, "image/jpeg", null, bitmap) { output, _, _, _, bmp ->
            try {
                FileOutputStream(output.fileDescriptor).use { fos ->
                    bmp?.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                }
            } catch (_: IOException) {
            }
        }
    }
}
