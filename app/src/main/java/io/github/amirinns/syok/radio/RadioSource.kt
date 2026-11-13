package io.github.amirinns.syok.radio

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import io.github.amirinns.syok.data.LibraryDao
import kotlinx.coroutines.flow.Flow

/**
 * Manages internet radio stations and live MediaItem creation.
 * Enforces live semantics:
 * - Station items are flagged live (LiveConfiguration)
 * - Media types set to MEDIA_TYPE_RADIO_STATION
 * - No seek / download commands offered
 * - Resolves to stored URLs only, ignoring external/caller-supplied URIs
 */
class RadioSource(
    private val libraryDao: LibraryDao
) {
    fun observeStations(): Flow<List<StationEntity>> = libraryDao.observeStations()

    suspend fun getStations(): List<StationEntity> = libraryDao.getStations()

    suspend fun getStation(id: Long): StationEntity? = libraryDao.getStation(id)

    suspend fun addStation(name: String, url: String): StationValidator.Result {
        val validation = StationValidator.validate(name, url)
        if (validation !is StationValidator.Result.Valid) {
            return validation
        }
        libraryDao.upsertStation(StationEntity(name = name.trim(), url = url.trim()))
        return StationValidator.Result.Valid
    }

    suspend fun updateStation(id: Long, name: String, url: String): StationValidator.Result {
        val validation = StationValidator.validate(name, url)
        if (validation !is StationValidator.Result.Valid) {
            return validation
        }
        libraryDao.upsertStation(StationEntity(id = id, name = name.trim(), url = url.trim()))
        return StationValidator.Result.Valid
    }

    suspend fun deleteStation(station: StationEntity) {
        libraryDao.deleteStation(station)
    }

    suspend fun deleteStationById(id: Long) {
        libraryDao.deleteStationById(id)
    }

    suspend fun resolveMediaItem(mediaId: String): MediaItem? {
        val stationId = parseStationId(mediaId) ?: return null
        val station = libraryDao.getStation(stationId) ?: return null
        return createStationPlayableItem(station)
    }

    companion object {
        const val STATION_MEDIA_ID_PREFIX = "station:"

        fun stationMediaId(id: Long): String = "$STATION_MEDIA_ID_PREFIX$id"

        fun isStationMediaId(mediaId: String?): Boolean {
            return mediaId != null && mediaId.startsWith(STATION_MEDIA_ID_PREFIX)
        }

        fun parseStationId(mediaId: String?): Long? {
            if (mediaId == null || !mediaId.startsWith(STATION_MEDIA_ID_PREFIX)) return null
            return mediaId.removePrefix(STATION_MEDIA_ID_PREFIX).toLongOrNull()
        }

        fun createStationBrowseItem(station: StationEntity): MediaItem {
            return MediaItem.Builder()
                .setMediaId(stationMediaId(station.id))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(station.name)
                        .setArtist("Internet Radio")
                        .setAlbumTitle("Radio")
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                        .build()
                )
                .build()
        }

        fun createStationPlayableItem(station: StationEntity): MediaItem {
            val builder = MediaItem.Builder()
                .setMediaId(stationMediaId(station.id))
                .setUri(Uri.parse(station.url))
                .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(station.name)
                        .setArtist("Internet Radio")
                        .setAlbumTitle("Radio")
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                        .build()
                )

            val lowerUrl = station.url.lowercase()
            if (lowerUrl.contains(".m3u8")) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }

            return builder.build()
        }
    }
}
