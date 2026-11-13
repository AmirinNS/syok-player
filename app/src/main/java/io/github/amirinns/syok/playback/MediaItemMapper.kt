package io.github.amirinns.syok.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.amirinns.syok.data.TrackEntity

/**
 * Maps database TrackEntity records and browse hierarchy nodes into Media3 MediaItem
 * objects with consistent titles, artists, album metadata, durations, and non-secret
 * content URIs for artwork.
 */
object MediaItemMapper {

    fun createRootItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(BrowseTree.ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Syok")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
    }

    fun createLocalRootItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(BrowseTree.LOCAL_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Local")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
    }

    fun createStationsRootItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(BrowseTree.STATIONS_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Stations")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .build()
            )
            .build()
    }

    fun mapAlbumToBrowseItem(albumName: String, trackCount: Int? = null): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(albumName)
            .setAlbumTitle(albumName)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkUri(ArtworkContentProvider.buildArtworkUriForAlbum(albumName))
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)

        if (trackCount != null) {
            metadataBuilder.setTotalTrackCount(trackCount)
        }

        return MediaItem.Builder()
            .setMediaId(BrowseTree.albumMediaId(albumName))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    fun mapTrackToBrowseItem(track: TrackEntity): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setTrackNumber(track.trackNumber)
                    .setDiscNumber(track.discNumber)
                    .setDurationMs(track.durationMs)
                    .setArtworkUri(ArtworkContentProvider.buildArtworkUri(track.id))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    fun mapTrackToPlayableItem(track: TrackEntity): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(track.contentUri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setTrackNumber(track.trackNumber)
                    .setDiscNumber(track.discNumber)
                    .setDurationMs(track.durationMs)
                    .setArtworkUri(ArtworkContentProvider.buildArtworkUri(track.id))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }
}
