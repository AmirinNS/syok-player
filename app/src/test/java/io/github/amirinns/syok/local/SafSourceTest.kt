package io.github.amirinns.syok.local

import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.amirinns.syok.data.FolderEntity
import io.github.amirinns.syok.data.LibraryDatabase
import io.github.amirinns.syok.data.TrackEntity
import io.github.amirinns.syok.playback.BrowseTree
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafSourceTest {
    private lateinit var database: LibraryDatabase
    private val tree = Uri.parse("content://test.documents/tree/primary%3AMusic")
    private val folder get() = FolderEntity(tree.toString(), "Music")
    private var granted = true
    private var mounted = true
    private var corrupt = false
    private var cancelTags = false
    private var tagsRead = 0
    private lateinit var source: SafSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries().build()
        source = SafSource(
            context, database.libraryDao(), ArtworkLoader(context), Dispatchers.Unconfined,
            grantOverride = { granted },
            queryOverride = { uri, projection ->
                if (!mounted) throw FileNotFoundException("Volume missing")
                MatrixCursor(projection).apply {
                    if (uri.toString().endsWith("/children")) {
                        addRow(arrayOf("primary:Music/good.mp3", "audio/mpeg", "good.mp3"))
                        addRow(arrayOf("primary:Music/bad.mp3", "audio/mpeg", "bad.mp3"))
                    } else {
                        addRow(arrayOf("primary:Music", DocumentsContract.Document.MIME_TYPE_DIR, "Music"))
                    }
                }
            },
            tagsOverride = { uri ->
                tagsRead++
                if (cancelTags) throw CancellationException("Cancelled")
                if (corrupt && uri.toString().contains("bad.mp3")) throw IllegalArgumentException("Corrupt tags")
                AudioTags()
            }
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun seed() {
        database.libraryDao().upsertFolder(folder)
        database.libraryDao().upsertTrack(TrackEntity(
            "saf:existing", "content://test.documents/existing", "Kept", "Artist", "Album",
            sourceId = tree.toString()
        ))
    }

    @Test
    fun revokedGrant_retainsRowsButHidesTracksFromLibraryAndBrowse() = runBlocking {
        seed()
        granted = false
        assertTrue(source.scanFolder(tree).isFailure)
        assertFalse(database.libraryDao().getTrackById("saf:existing")!!.isAvailable)
        assertEquals(FolderEntity.ACCESS_REVOKED, database.libraryDao().getFolder(tree.toString())!!.status)
        assertTrue(database.libraryDao().getAllTracksSync().isEmpty())
        assertTrue(BrowseTree(database.libraryDao()).children(BrowseTree.LOCAL_ID)!!.isEmpty())
    }

    @Test
    fun unavailableVolume_preservesIdentityAndCanRetry() = runBlocking {
        seed()
        mounted = false
        assertTrue(source.scanFolder(tree).isFailure)
        assertNotNull(database.libraryDao().getTrackById("saf:existing"))
        assertEquals(FolderEntity.UNAVAILABLE, database.libraryDao().getFolder(tree.toString())!!.status)
        mounted = true
        assertEquals(2, source.scanFolder(tree).getOrThrow())
        assertEquals(FolderEntity.AVAILABLE, database.libraryDao().getFolder(tree.toString())!!.status)
    }

    @Test
    fun remountRefresh_reindexesReachableFoldersWithStableIds() = runBlocking {
        database.libraryDao().upsertFolder(folder)
        assertEquals(2, source.scanFolder(tree).getOrThrow())
        val originalIds = database.libraryDao().getAllTracksSync().map { it.id }
        source.refreshAvailability(rescanReachable = true)
        assertEquals(2, tagsRead)
        mounted = false
        source.refreshAvailability()
        assertTrue(database.libraryDao().getAllTracksSync().isEmpty())
        mounted = true
        source.refreshAvailability()
        assertTrue(database.libraryDao().getAllTracksSync().isEmpty())
        source.refreshAvailability(rescanReachable = true)
        assertEquals(originalIds, database.libraryDao().getAllTracksSync().map { it.id })
        assertEquals(4, tagsRead)
        assertEquals(FolderEntity.AVAILABLE, database.libraryDao().getFolder(tree.toString())!!.status)
    }

    @Test
    fun mountRefresh_keepsRevokedAndMissingFoldersUnavailable() = runBlocking {
        seed()
        mounted = false
        source.refreshAvailability(rescanReachable = true)
        assertEquals(FolderEntity.UNAVAILABLE, database.libraryDao().getFolder(tree.toString())!!.status)
        assertFalse(database.libraryDao().getTrackById("saf:existing")!!.isAvailable)
        mounted = true
        granted = false
        source.refreshAvailability(rescanReachable = true)
        assertEquals(FolderEntity.ACCESS_REVOKED, database.libraryDao().getFolder(tree.toString())!!.status)
        assertFalse(database.libraryDao().getTrackById("saf:existing")!!.isAvailable)
    }

    @Test
    fun corruptFile_doesNotStopScan_andIdsAndFilenameFallbackSurviveRescan() = runBlocking {
        database.libraryDao().upsertFolder(folder)
        corrupt = true
        assertEquals(1, source.scanFolder(tree).getOrThrow())
        val first = database.libraryDao().getAllTracksSync().single()
        assertTrue(first.id.startsWith("saf:"))
        assertTrue(first.contentUri.startsWith("content://test.documents/tree/"))
        assertEquals("good", first.title)
        assertEquals("Unknown Artist", first.artist)
        assertEquals(1, source.scanFolder(tree).getOrThrow())
        assertEquals(first.id, database.libraryDao().getAllTracksSync().single().id)
    }

    @Test
    fun removingFolder_keepsRowsUnavailable() = runBlocking {
        seed()
        source.removeFolder(tree)
        assertNull(database.libraryDao().getFolder(tree.toString()))
        assertFalse(database.libraryDao().getTrackById("saf:existing")!!.isAvailable)
    }

    @Test
    fun availabilityRefresh_doesNotHideOtherSources() = runBlocking {
        seed()
        database.libraryDao().upsertTrack(TrackEntity(
            "mediastore:external:7", "content://media/7", "Device", "Artist", "Album"
        ))
        granted = false
        source.refreshAvailability()
        assertFalse(database.libraryDao().getTrackById("saf:existing")!!.isAvailable)
        assertTrue(database.libraryDao().getTrackById("mediastore:external:7")!!.isAvailable)
    }

    @Test
    fun nestedFolderAndMultipleBatches_indexWithoutDuplicates() = runBlocking {
        database.libraryDao().upsertFolder(folder)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val batchSource = SafSource(
            context, database.libraryDao(), ArtworkLoader(context), Dispatchers.Unconfined,
            grantOverride = { true },
            queryOverride = { uri, projection ->
                MatrixCursor(projection).apply {
                    when {
                        !uri.toString().endsWith("/children") ->
                            addRow(arrayOf("primary:Music", DocumentsContract.Document.MIME_TYPE_DIR, "Music"))
                        !uri.toString().contains("nested") ->
                            addRow(arrayOf("primary:Music/nested", DocumentsContract.Document.MIME_TYPE_DIR, "Nested"))
                        else -> repeat(130) { index ->
                            addRow(arrayOf("primary:Music/nested/$index.mp3", "audio/mpeg", "$index.mp3"))
                        }
                    }
                }
            },
            tagsOverride = { AudioTags(title = "Tagged", artist = "Artist", album = "Nested album", trackNumber = 2, discNumber = 3, durationMs = 1234) }
        )
        assertEquals(130, batchSource.scanFolder(tree).getOrThrow())
        val tracks = database.libraryDao().getAllTracksSync()
        assertEquals(130, tracks.size)
        assertEquals(3, tracks.first().discNumber)
        assertEquals(2, tracks.first().trackNumber)
        assertEquals(1234L, tracks.first().durationMs)
        assertEquals(130, batchSource.scanFolder(tree).getOrThrow())
        assertEquals(tracks.map { it.id }, database.libraryDao().getAllTracksSync().map { it.id })
    }

    @Test
    fun cancellation_isNotReportedAsSuccessfulOrSwallowed() = runBlocking {
        database.libraryDao().upsertFolder(folder)
        cancelTags = true
        try {
            source.scanFolder(tree)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(database.libraryDao().getAllTracksSync().isEmpty())
        }
    }
}
