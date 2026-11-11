package io.github.amirinns.syok.local

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import io.github.amirinns.syok.data.LibraryDao
import io.github.amirinns.syok.data.TrackEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreSource(
    private val contentResolver: ContentResolver,
    private val libraryDao: LibraryDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val queryOverride: ((Uri, Array<String>?, String?, Array<String>?, String?) -> Cursor?)? = null
) {

    private val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DISPLAY_NAME
    )

    /**
     * Scans MediaStore audio content URIs on a background thread.
     * Preserves the previous index if access fails.
     *
     * @param volumeName MediaStore volume to scan (e.g., "external" or MediaStore.VOLUME_EXTERNAL).
     * @return Result containing the count of scanned tracks, or a failure.
     */
    suspend fun scan(volumeName: String = "external"): Result<Int> = withContext(ioDispatcher) {
        val collectionUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(volumeName)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        try {
            val cursor = if (queryOverride != null) {
                queryOverride.invoke(collectionUri, projection, null, null, null)
            } else {
                contentResolver.query(
                    collectionUri,
                    projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    "${MediaStore.Audio.Media.ALBUM} ASC, ${MediaStore.Audio.Media.TRACK} ASC"
                ) ?: contentResolver.query(
                    collectionUri,
                    projection,
                    null,
                    null,
                    "${MediaStore.Audio.Media.ALBUM} ASC, ${MediaStore.Audio.Media.TRACK} ASC"
                )
            }

            if (cursor == null) {
                return@withContext Result.failure(IllegalStateException("Query to MediaStore returned null for $collectionUri"))
            }

            val tracks = mutableListOf<TrackEntity>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    tracks.add(toTrack(c, volumeName))
                }
            }

            libraryDao.upsertTracks(tracks)
            Result.success(tracks.size)
        } catch (e: Exception) {
            // Preserve previous index on access failure
            Result.failure(e)
        }
    }

    /**
     * Maps a MediaStore cursor row to a TrackEntity with volume-qualified IDs
     * and fallback values for missing tags.
     *
     * @param cursor Cursor pointing to the current audio row.
     * @param volumeName The volume name to qualify the ID (e.g., "external").
     * @return The populated TrackEntity.
     */
    fun toTrack(cursor: Cursor, volumeName: String): TrackEntity {
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
        val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val albumIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
        val trackIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
        val durationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
        val displayNameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)

        val mediaId = cursor.getLong(idIndex)
        val rawTitle = if (titleIndex >= 0 && !cursor.isNull(titleIndex)) cursor.getString(titleIndex) else null
        val rawArtist = if (artistIndex >= 0 && !cursor.isNull(artistIndex)) cursor.getString(artistIndex) else null
        val rawAlbum = if (albumIndex >= 0 && !cursor.isNull(albumIndex)) cursor.getString(albumIndex) else null
        val rawTrack = if (trackIndex >= 0 && !cursor.isNull(trackIndex)) cursor.getInt(trackIndex) else 0
        val rawDuration = if (durationIndex >= 0 && !cursor.isNull(durationIndex)) cursor.getLong(durationIndex) else 0L
        val displayName = if (displayNameIndex >= 0 && !cursor.isNull(displayNameIndex)) cursor.getString(displayNameIndex) else null

        // Volume-qualified ID (e.g. "mediastore:external:101")
        val qualifiedId = "mediastore:$volumeName:$mediaId"

        val baseUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(volumeName)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val contentUri = ContentUris.withAppendedId(baseUri, mediaId).toString()

        val title = when {
            !rawTitle.isNullOrBlank() && rawTitle != "<unknown>" -> rawTitle
            !displayName.isNullOrBlank() -> displayName.substringBeforeLast('.')
            else -> "Unknown Title"
        }

        val artist = when {
            !rawArtist.isNullOrBlank() && rawArtist != "<unknown>" -> rawArtist
            else -> "Unknown Artist"
        }

        val album = when {
            !rawAlbum.isNullOrBlank() && rawAlbum != "<unknown>" -> rawAlbum
            else -> "Unknown Album"
        }

        val (discNumber, trackNumber) = if (rawTrack >= 1000) {
            Pair(rawTrack / 1000, rawTrack % 1000)
        } else {
            Pair(1, if (rawTrack > 0) rawTrack else 0)
        }

        val durationMs = if (rawDuration > 0L) rawDuration else 0L

        return TrackEntity(
            id = qualifiedId,
            contentUri = contentUri,
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            discNumber = discNumber,
            durationMs = durationMs,
            isAvailable = true
        )
    }
}
