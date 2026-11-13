package io.github.amirinns.syok.local

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import io.github.amirinns.syok.data.FolderEntity
import io.github.amirinns.syok.data.LibraryDao
import io.github.amirinns.syok.data.TrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.FileNotFoundException

/** Persisted grants remain Android's authority. Room stores only the chosen tree and index. */
class SafSource(
    private val context: Context,
    private val libraryDao: LibraryDao,
    private val artworkLoader: ArtworkLoader,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val grantOverride: ((Uri) -> Boolean)? = null,
    private val queryOverride: ((Uri, Array<String>) -> Cursor?)? = null,
    private val tagsOverride: (suspend (Uri) -> AudioTags)? = null
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val resolver = context.contentResolver
    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME
    )

    private fun hasReadGrant(tree: Uri): Boolean = grantOverride?.invoke(tree)
        ?: resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }

    private fun query(uri: Uri): Cursor = (queryOverride?.let { it(uri, projection) }
        ?: if (queryOverride == null) resolver.query(uri, projection, null, null, null) else null)
        ?: throw FileNotFoundException("Folder is unavailable")

    private fun rootName(tree: Uri): String {
        if (!hasReadGrant(tree)) throw SecurityException("Folder access was revoked")
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return query(root).use { cursor ->
            if (!cursor.moveToFirst() || cursor.getString(1) != DocumentsContract.Document.MIME_TYPE_DIR) {
                throw FileNotFoundException("Folder is unavailable")
            }
            cursor.getString(2)?.takeIf { it.isNotBlank() } ?: "Music folder"
        }
    }

    suspend fun addFolder(tree: Uri, flags: Int, replacing: Uri? = null): Result<Int> = withContext(ioDispatcher) {
        mutex.withLock {
            try {
                require(tree.scheme == "content" && DocumentsContract.isTreeUri(tree)) { "Choose a music folder." }
                if (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
                    throw SecurityException("The provider did not grant read access")
                }
                resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val name = try {
                    rootName(tree)
                } catch (_: Exception) {
                    "Music folder"
                }
                libraryDao.upsertFolder(FolderEntity(tree.toString(), name))
                if (replacing != null && replacing != tree) removeLocked(replacing)
                scanLocked(tree)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Result.failure(IllegalStateException("Could not keep folder access. Reconnect and choose the folder again."))
            }
        }
    }

    suspend fun scanFolder(tree: Uri): Result<Int> = withContext(ioDispatcher) {
        mutex.withLock { scanLocked(tree) }
    }

    private suspend fun scanLocked(tree: Uri): Result<Int> {
        val folder = libraryDao.getFolder(tree.toString())
            ?: return Result.failure(IllegalStateException("Choose the folder again in Sources."))
        return try {
            val name = rootName(tree)
            libraryDao.setSourceAvailability(folder.treeUri, false)
            libraryDao.upsertFolder(folder.copy(name = name, detail = "Indexing music…"))
            val directories = ArrayDeque<String>()
            val visited = mutableSetOf<String>()
            directories.add(DocumentsContract.getTreeDocumentId(tree))
            val batch = ArrayList<TrackEntity>(64)
            var indexed = 0
            var skipped = 0
            while (directories.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val directory = directories.removeLast()
                if (!visited.add(directory)) continue
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, directory)
                query(children).use { cursor ->
                    while (cursor.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val documentId = cursor.getString(0) ?: continue
                        val mimeType = cursor.getString(1).orEmpty()
                        val filename = cursor.getString(2).orEmpty()
                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            directories.add(documentId)
                        } else if (isAudio(mimeType, filename)) {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
                            try {
                                val tags = readTags(uri)
                                batch.add(TrackEntity(
                                    id = "saf:${Uri.encode(tree.toString())}:${Uri.encode(documentId)}",
                                    contentUri = uri.toString(),
                                    title = tags.title ?: filename.substringBeforeLast('.').ifBlank { "Unknown Title" },
                                    artist = tags.artist ?: "Unknown Artist",
                                    album = tags.album ?: "Unknown Album",
                                    trackNumber = tags.trackNumber,
                                    discNumber = tags.discNumber,
                                    durationMs = tags.durationMs,
                                    sourceId = tree.toString()
                                ))
                                indexed++
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                // One malformed or unreadable document cannot stop its siblings.
                                skipped++
                            }
                        }
                        if (batch.size >= 64) {
                            libraryDao.upsertTracks(batch.toList())
                            batch.clear()
                        }
                        yield()
                    }
                }
            }
            // A drive/grant can disappear after the last batch was written.
            rootName(tree)
            libraryDao.upsertTracks(batch)
            libraryDao.upsertFolder(folder.copy(
                name = name, status = FolderEntity.AVAILABLE,
                detail = "Indexed $indexed tracks." + if (skipped > 0) " Skipped $skipped unreadable files." else ""
            ))
            Result.success(indexed)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                libraryDao.setSourceAvailability(folder.treeUri, false)
                libraryDao.upsertFolder(folder.copy(
                    status = FolderEntity.UNAVAILABLE, detail = "Indexing interrupted. Retry indexing while parked."
                ))
            }
            throw cancelled
        } catch (error: Exception) {
            markUnavailable(folder, error)
            Result.failure(IllegalStateException(
                if (error is SecurityException) "Folder access revoked. Reconnect in Sources."
                else "Folder unavailable. Reconnect the drive, then retry indexing in Sources."
            ))
        }
    }

    suspend fun readTags(uri: Uri): AudioTags = tagsOverride?.invoke(uri) ?: artworkLoader.readTags(uri)

    private fun isAudio(mimeType: String, filename: String): Boolean = mimeType.startsWith("audio/") ||
        filename.substringAfterLast('.', "").lowercase() in setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")

    private suspend fun markUnavailable(folder: FolderEntity, error: Exception) {
        val revoked = error is SecurityException
        libraryDao.setSourceAvailability(folder.treeUri, false)
        libraryDao.upsertFolder(folder.copy(
            status = if (revoked) FolderEntity.ACCESS_REVOKED else FolderEntity.UNAVAILABLE,
            detail = if (revoked) "Access revoked. Reconnect and choose this folder again."
            else "Folder unavailable. Reconnect the drive and retry indexing."
        ))
    }

    suspend fun refreshAvailability(rescanReachable: Boolean = false) = withContext(ioDispatcher) {
        mutex.withLock {
            for (folder in libraryDao.getFolders()) {
                currentCoroutineContext().ensureActive()
                try {
                    val tree = Uri.parse(folder.treeUri)
                    rootName(tree)
                    if (rescanReachable && folder.status != FolderEntity.AVAILABLE) scanLocked(tree)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    markUnavailable(folder, error)
                }
            }
        }
    }

    suspend fun removeFolder(tree: Uri) = withContext(ioDispatcher) {
        mutex.withLock { removeLocked(tree) }
    }

    private suspend fun removeLocked(tree: Uri) {
        libraryDao.setSourceAvailability(tree.toString(), false)
        libraryDao.deleteFolder(tree.toString())
        try {
            resolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Already revoked by Android or the user.
        }
        artworkLoader.clearCache()
    }

    /** Application-lifetime monitoring also runs when only an Auto browser starts the service. */
    fun startMonitoring() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val mounted = intent?.action == Intent.ACTION_MEDIA_MOUNTED
                scope.launch { refreshAvailability(rescanReachable = mounted) }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch { refreshAvailability() }
    }
}
