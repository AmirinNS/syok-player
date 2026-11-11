package io.github.amirinns.syok.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import io.github.amirinns.syok.data.LibraryDao

/**
 * Manages the media browse hierarchy for Android Auto and external MediaBrowser clients.
 * Enforces a strict, stable 4-level structure:
 * Root ([syok_root]) -> Local (syok:local) -> Albums (syok:album:<name>) -> Tracks (mediastore:external:<id>)
 *
 * Implements bounded pagination, explicit browsable/playable flags, and safe off-main-thread queries.
 */
class BrowseTree(
    private val libraryDao: LibraryDao
) {

    companion object {
        const val ROOT_ID = "[syok_root]"
        const val LOCAL_ID = "syok:local"
        const val ALBUM_PREFIX = "syok:album:"

        const val DEFAULT_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 100

        fun albumMediaId(albumName: String): String = "$ALBUM_PREFIX${Uri.encode(albumName)}"

        fun parseAlbumName(mediaId: String): String? {
            return if (mediaId.startsWith(ALBUM_PREFIX)) {
                Uri.decode(mediaId.removePrefix(ALBUM_PREFIX))
            } else {
                null
            }
        }
    }

    /**
     * Resolves the children of a browsable node with bounded pagination.
     * Returns null if parentId is unknown or points to a non-browsable node (e.g. a track).
     */
    suspend fun children(
        parentId: String,
        page: Int = 0,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<MediaItem>? {
        if (page < 0 || pageSize <= 0) {
            return emptyList()
        }

        return when {
            parentId == ROOT_ID || parentId == "syok:root" -> {
                val rootChildren = listOf(MediaItemMapper.createLocalRootItem())
                paginate(rootChildren, page, pageSize)
            }
            parentId == LOCAL_ID || parentId == "syok:local:albums" || parentId == "syok:albums" -> {
                val albums = libraryDao.getAlbumsSync()
                val albumItems = albums.map { MediaItemMapper.mapAlbumToBrowseItem(it) }
                paginate(albumItems, page, pageSize)
            }
            parentId.startsWith(ALBUM_PREFIX) -> {
                val albumName = parseAlbumName(parentId) ?: return emptyList()
                val tracks = libraryDao.getTracksByAlbumSync(albumName)
                val trackItems = tracks.map { MediaItemMapper.mapTrackToBrowseItem(it) }
                paginate(trackItems, page, pageSize)
            }
            else -> null
        }
    }

    /**
     * Retrieves a single MediaItem by its media ID. Returns null if not found.
     */
    suspend fun getItem(mediaId: String): MediaItem? {
        if (mediaId.isBlank()) return null

        if (mediaId == ROOT_ID || mediaId == "syok:root") {
            return MediaItemMapper.createRootItem()
        }
        if (mediaId == LOCAL_ID || mediaId == "syok:local:albums") {
            return MediaItemMapper.createLocalRootItem()
        }
        if (mediaId.startsWith(ALBUM_PREFIX)) {
            val albumName = parseAlbumName(mediaId) ?: return null
            val albums = libraryDao.getAlbumsSync()
            return if (albumName in albums) {
                MediaItemMapper.mapAlbumToBrowseItem(albumName)
            } else {
                null
            }
        }

        val track = libraryDao.getTrackById(mediaId)
        return if (track != null && track.isAvailable) {
            MediaItemMapper.mapTrackToBrowseItem(track)
        } else {
            null
        }
    }

    /**
     * Searches indexed local tracks matching query against title and artist.
     */
    suspend fun search(
        query: String,
        page: Int = 0,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<MediaItem> {
        val trimmed = query.trim()
        if (trimmed.isBlank() || page < 0 || pageSize <= 0) {
            return emptyList()
        }

        val matches = libraryDao.searchTracksSync(trimmed)
        val items = matches.map { MediaItemMapper.mapTrackToBrowseItem(it) }
        return paginate(items, page, pageSize)
    }

    /**
     * Returns total matching track count for the search query.
     */
    suspend fun getSearchResultCount(query: String): Int {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return 0
        return libraryDao.searchTracksSync(trimmed).size
    }

    private fun <T> paginate(items: List<T>, page: Int, pageSize: Int): List<T> {
        val boundedSize = pageSize.coerceAtMost(MAX_PAGE_SIZE)
        val fromIndex = page * boundedSize
        if (fromIndex >= items.size) {
            return emptyList()
        }
        val toIndex = (fromIndex + boundedSize).coerceAtMost(items.size)
        return items.subList(fromIndex, toIndex)
    }
}
