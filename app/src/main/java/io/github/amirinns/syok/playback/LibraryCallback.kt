package io.github.amirinns.syok.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.amirinns.syok.data.LibraryDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * MediaLibrarySession callback responsible for secure media item resolution,
 * browse tree navigation, search, and external controller command limitation.
 *
 * Resolves known source-qualified track IDs into playable content URIs from the local database.
 * Rejects unknown IDs and caller-supplied arbitrary URIs.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LibraryCallback(
    private val context: Context?,
    private val libraryDao: LibraryDao,
    private val browseTree: BrowseTree = BrowseTree(libraryDao),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())
) : MediaLibrarySession.Callback {

    constructor(
        libraryDao: LibraryDao,
        browseTree: BrowseTree = BrowseTree(libraryDao),
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        scope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())
    ) : this(
        context = null,
        libraryDao = libraryDao,
        browseTree = browseTree,
        ioDispatcher = ioDispatcher,
        scope = scope
    )

    constructor(
        libraryDao: LibraryDao,
        ioDispatcher: CoroutineDispatcher
    ) : this(
        context = null,
        libraryDao = libraryDao,
        browseTree = BrowseTree(libraryDao),
        ioDispatcher = ioDispatcher,
        scope = CoroutineScope(ioDispatcher + SupervisorJob())
    )

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        // Limit connected controllers: expose standard session & library commands, but do not expose settings, downloads, or unapproved operations
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .build()
        val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
            .build()

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(playerCommands)
            .build()
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        val future = SettableFuture.create<List<MediaItem>>()

        scope.launch {
            try {
                val resolvedItems = mutableListOf<MediaItem>()
                for (item in mediaItems) {
                    val mediaId = item.mediaId
                    // Reject empty IDs or items where caller supplied arbitrary external URIs without a known ID
                    if (mediaId.isBlank()) continue

                    val track = libraryDao.getTrackById(mediaId)
                    if (track != null && track.isAvailable) {
                        val resolved = MediaItemMapper.mapTrackToPlayableItem(track)
                        resolvedItems.add(resolved)
                    }
                    // Unknown or unavailable IDs are omitted/rejected
                }
                future.set(resolvedItems)
            } catch (t: Throwable) {
                future.setException(t)
            }
        }

        return future
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val rootItem = MediaItemMapper.createRootItem()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    private fun grantArtworkPermission(browser: MediaSession.ControllerInfo, items: List<MediaItem>) {
        val ctx = context ?: return
        val targetPackage = browser.packageName
        if (targetPackage.isBlank() || targetPackage == ctx.packageName) {
            return
        }
        for (item in items) {
            val artworkUri = item.mediaMetadata.artworkUri ?: continue
            try {
                ctx.grantUriPermission(
                    targetPackage,
                    artworkUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
        }
    }

    private fun grantArtworkPermission(browser: MediaSession.ControllerInfo, item: MediaItem) {
        grantArtworkPermission(browser, listOf(item))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            try {
                val children = browseTree.children(parentId, page, pageSize)
                if (children != null) {
                    grantArtworkPermission(browser, children)
                    future.set(LibraryResult.ofItemList(children, params))
                } else {
                    future.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE, params))
                }
            } catch (t: Throwable) {
                future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN, params))
            }
        }
        return future
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val future = SettableFuture.create<LibraryResult<MediaItem>>()
        scope.launch {
            try {
                val item = browseTree.getItem(mediaId)
                if (item != null) {
                    grantArtworkPermission(browser, item)
                    future.set(LibraryResult.ofItem(item, null))
                } else {
                    future.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                }
            } catch (t: Throwable) {
                future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        val future = SettableFuture.create<LibraryResult<Void>>()
        scope.launch {
            try {
                val count = browseTree.getSearchResultCount(query)
                session.notifySearchResultChanged(browser, query, count, params)
                future.set(LibraryResult.ofVoid(params))
            } catch (t: Throwable) {
                future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN, params))
            }
        }
        return future
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            try {
                val results = browseTree.search(query, page, pageSize)
                grantArtworkPermission(browser, results)
                future.set(LibraryResult.ofItemList(results, params))
            } catch (t: Throwable) {
                future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN, params))
            }
        }
        return future
    }
}
