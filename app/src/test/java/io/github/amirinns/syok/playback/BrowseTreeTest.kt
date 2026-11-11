package io.github.amirinns.syok.playback

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.amirinns.syok.data.LibraryDao
import io.github.amirinns.syok.data.LibraryDatabase
import io.github.amirinns.syok.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowseTreeTest {

    private lateinit var context: Context
    private lateinit var database: LibraryDatabase
    private lateinit var libraryDao: LibraryDao
    private lateinit var browseTree: BrowseTree

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        libraryDao = database.libraryDao()
        browseTree = BrowseTree(libraryDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testStableRootAndLocalIds() = runBlocking {
        // Root children must expose the stable Local branch
        val rootChildren = browseTree.children(BrowseTree.ROOT_ID)
        assertNotNull(rootChildren)
        assertEquals(1, rootChildren!!.size)

        val localNode = rootChildren[0]
        assertEquals(BrowseTree.LOCAL_ID, localNode.mediaId)
        assertEquals("Local", localNode.mediaMetadata.title.toString())
        assertTrue(localNode.mediaMetadata.isBrowsable == true)
        assertTrue(localNode.mediaMetadata.isPlayable == false)
    }

    @Test
    fun testStableAlbumAndTrackIds() = runBlocking {
        val track1 = TrackEntity(
            id = "mediastore:external:100",
            contentUri = "content://media/external/audio/media/100",
            title = "Jalan Telawi Echoes",
            artist = "Klang Valley Trio",
            album = "Midnight Drive",
            trackNumber = 1,
            discNumber = 1,
            durationMs = 180000L,
            isAvailable = true
        )
        val track2 = TrackEntity(
            id = "mediastore:external:101",
            contentUri = "content://media/external/audio/media/101",
            title = "Bangsar Rain",
            artist = "Klang Valley Trio",
            album = "Midnight Drive",
            trackNumber = 2,
            discNumber = 1,
            durationMs = 210000L,
            isAvailable = true
        )
        libraryDao.upsertTracks(listOf(track1, track2))

        // Level 1 -> Level 2: Local children are the albums
        val localChildren = browseTree.children(BrowseTree.LOCAL_ID)
        assertNotNull(localChildren)
        assertEquals(1, localChildren!!.size)

        val albumNode = localChildren[0]
        val expectedAlbumId = BrowseTree.albumMediaId("Midnight Drive")
        assertEquals(expectedAlbumId, albumNode.mediaId)
        assertEquals("Midnight Drive", albumNode.mediaMetadata.title.toString())
        assertTrue(albumNode.mediaMetadata.isBrowsable == true)
        assertTrue(albumNode.mediaMetadata.isPlayable == false)

        // Level 2 -> Level 3: Album children are the tracks
        val albumChildren = browseTree.children(expectedAlbumId)
        assertNotNull(albumChildren)
        assertEquals(2, albumChildren!!.size)

        val t1 = albumChildren[0]
        assertEquals("mediastore:external:100", t1.mediaId)
        assertEquals("Jalan Telawi Echoes", t1.mediaMetadata.title.toString())
        assertEquals("Klang Valley Trio", t1.mediaMetadata.artist.toString())
        assertEquals("Midnight Drive", t1.mediaMetadata.albumTitle.toString())
        assertEquals(180000L, t1.mediaMetadata.durationMs)
        assertNotNull(t1.mediaMetadata.artworkUri)
        assertTrue(t1.mediaMetadata.artworkUri.toString().startsWith("content://io.github.amirinns.syok.artwork/track/"))
        assertFalse(t1.mediaMetadata.isBrowsable == true)
        assertTrue(t1.mediaMetadata.isPlayable == true)

        val t2 = albumChildren[1]
        assertEquals("mediastore:external:101", t2.mediaId)
        assertEquals("Bangsar Rain", t2.mediaMetadata.title.toString())
        assertFalse(t2.mediaMetadata.isBrowsable == true)
        assertTrue(t2.mediaMetadata.isPlayable == true)
    }

    @Test
    fun testValidPageBoundaries() = runBlocking {
        // Seed 5 distinct albums
        val tracks = (1..5).map { idx ->
            TrackEntity(
                id = "mediastore:external:$idx",
                contentUri = "content://media/external/audio/media/$idx",
                title = "Track $idx",
                artist = "Artist",
                album = "Album $idx",
                trackNumber = 1,
                discNumber = 1,
                durationMs = 120000L,
                isAvailable = true
            )
        }
        libraryDao.upsertTracks(tracks)

        // Valid pagination: page 0, size 2 -> 2 items
        val page0 = browseTree.children(BrowseTree.LOCAL_ID, page = 0, pageSize = 2)
        assertNotNull(page0)
        assertEquals(2, page0!!.size)
        assertEquals("Album 1", page0[0].mediaMetadata.title.toString())
        assertEquals("Album 2", page0[1].mediaMetadata.title.toString())

        // page 1, size 2 -> 2 items
        val page1 = browseTree.children(BrowseTree.LOCAL_ID, page = 1, pageSize = 2)
        assertNotNull(page1)
        assertEquals(2, page1!!.size)
        assertEquals("Album 3", page1[0].mediaMetadata.title.toString())
        assertEquals("Album 4", page1[1].mediaMetadata.title.toString())

        // page 2, size 2 -> 1 item
        val page2 = browseTree.children(BrowseTree.LOCAL_ID, page = 2, pageSize = 2)
        assertNotNull(page2)
        assertEquals(1, page2!!.size)
        assertEquals("Album 5", page2[0].mediaMetadata.title.toString())

        // page 3, size 2 -> empty list (past total items)
        val page3 = browseTree.children(BrowseTree.LOCAL_ID, page = 3, pageSize = 2)
        assertNotNull(page3)
        assertTrue(page3!!.isEmpty())

        // Negative page -> empty list
        val negativePage = browseTree.children(BrowseTree.LOCAL_ID, page = -1, pageSize = 2)
        assertNotNull(negativePage)
        assertTrue(negativePage!!.isEmpty())

        // Zero pageSize -> empty list
        val zeroPageSize = browseTree.children(BrowseTree.LOCAL_ID, page = 0, pageSize = 0)
        assertNotNull(zeroPageSize)
        assertTrue(zeroPageSize!!.isEmpty())

        // Negative pageSize -> empty list
        val negativePageSize = browseTree.children(BrowseTree.LOCAL_ID, page = 0, pageSize = -5)
        assertNotNull(negativePageSize)
        assertTrue(negativePageSize!!.isEmpty())
    }

    @Test
    fun testEmptyLibraries() = runBlocking {
        // Root still shows local source category
        val rootChildren = browseTree.children(BrowseTree.ROOT_ID)
        assertNotNull(rootChildren)
        assertEquals(1, rootChildren!!.size)

        // Local branch has 0 albums in an empty library
        val localChildren = browseTree.children(BrowseTree.LOCAL_ID)
        assertNotNull(localChildren)
        assertTrue(localChildren!!.isEmpty())
    }

    @Test
    fun testUnknownIds() = runBlocking {
        // Arbitrary unknown category ID returns null
        val unknown = browseTree.children("unknown:category")
        assertNull(unknown)

        // Tracks are playable, not browsable; children() must return null
        val trackChildren = browseTree.children("mediastore:external:100")
        assertNull(trackChildren)

        // getItem on unknown ID returns null
        val unknownItem = browseTree.getItem("unknown:media:id")
        assertNull(unknownItem)
    }

    @Test
    fun testSearchAndGetItem() = runBlocking {
        val track1 = TrackEntity(
            id = "mediastore:external:201",
            contentUri = "content://media/external/audio/media/201",
            title = "Jalan Telawi Echoes",
            artist = "Klang Valley Trio",
            album = "Midnight Drive",
            trackNumber = 1,
            discNumber = 1,
            durationMs = 180000L,
            isAvailable = true
        )
        val track2 = TrackEntity(
            id = "mediastore:external:202",
            contentUri = "content://media/external/audio/media/202",
            title = "Bangsar Rain",
            artist = "Klang Valley Trio",
            album = "Midnight Drive",
            trackNumber = 2,
            discNumber = 1,
            durationMs = 210000L,
            isAvailable = true
        )
        libraryDao.upsertTracks(listOf(track1, track2))

        // Search by title match
        val titleMatch = browseTree.search("Telawi")
        assertEquals(1, titleMatch.size)
        assertEquals("mediastore:external:201", titleMatch[0].mediaId)

        // Search by artist match
        val artistMatch = browseTree.search("Klang Valley")
        assertEquals(2, artistMatch.size)

        // Search blank query returns empty list
        assertTrue(browseTree.search("").isEmpty())
        assertTrue(browseTree.search("   ").isEmpty())

        // Search count
        assertEquals(1, browseTree.getSearchResultCount("Telawi"))
        assertEquals(2, browseTree.getSearchResultCount("Klang Valley"))
        assertEquals(0, browseTree.getSearchResultCount("Nonexistent"))

        // getItem on known entities
        val rootItem = browseTree.getItem(BrowseTree.ROOT_ID)
        assertNotNull(rootItem)
        assertEquals(BrowseTree.ROOT_ID, rootItem!!.mediaId)

        val localItem = browseTree.getItem(BrowseTree.LOCAL_ID)
        assertNotNull(localItem)
        assertEquals(BrowseTree.LOCAL_ID, localItem!!.mediaId)

        val albumItem = browseTree.getItem(BrowseTree.albumMediaId("Midnight Drive"))
        assertNotNull(albumItem)
        assertEquals("Midnight Drive", albumItem!!.mediaMetadata.title.toString())

        val trackItem = browseTree.getItem("mediastore:external:201")
        assertNotNull(trackItem)
        assertEquals("Jalan Telawi Echoes", trackItem!!.mediaMetadata.title.toString())
        assertEquals(180000L, trackItem.mediaMetadata.durationMs)
    }

    @Test
    fun testLibraryCallback_grantsUriPermission_toExternalBrowser() = runBlocking {
        val track = TrackEntity(
            id = "mediastore:external:301",
            contentUri = "content://media/external/audio/media/301",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            trackNumber = 1,
            discNumber = 1,
            durationMs = 120000L,
            isAvailable = true
        )
        libraryDao.upsertTracks(listOf(track))

        val grantedPermissions = mutableListOf<Triple<String, Uri, Int>>()
        val contextWrapper = object : ContextWrapper(context) {
            override fun getPackageName(): String = "io.github.amirinns.syok"
            override fun grantUriPermission(toPackage: String, uri: Uri, modeFlags: Int) {
                grantedPermissions.add(Triple(toPackage, uri, modeFlags))
            }
        }

        val callback = LibraryCallback(contextWrapper, libraryDao, browseTree, Dispatchers.Unconfined)
        val testPlayer = ExoPlayer.Builder(context).build()
        val testSession = MediaLibrarySession.Builder(contextWrapper, testPlayer, callback).build()

        val externalBrowser = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
            "com.google.android.projection.gearhead",
            2000, 2000, 1, 1, false, Bundle.EMPTY, false
        )
        val selfBrowser = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
            "io.github.amirinns.syok",
            1000, 1000, 1, 1, false, Bundle.EMPTY, false
        )

        try {
            // onGetChildren with external browser grants permission for track artwork
            val albumId = BrowseTree.albumMediaId("Test Album")
            callback.onGetChildren(testSession, externalBrowser, albumId, 0, 10, null).get()
            assertEquals(1, grantedPermissions.size)
            assertEquals("com.google.android.projection.gearhead", grantedPermissions[0].first)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, grantedPermissions[0].third)

            // onGetChildren with self package must NOT grant permission (skipped)
            grantedPermissions.clear()
            callback.onGetChildren(testSession, selfBrowser, albumId, 0, 10, null).get()
            assertTrue(grantedPermissions.isEmpty())

            // onGetItem with external browser grants permission
            callback.onGetItem(testSession, externalBrowser, "mediastore:external:301").get()
            assertEquals(1, grantedPermissions.size)
            assertEquals("com.google.android.projection.gearhead", grantedPermissions[0].first)

            // onGetSearchResult with external browser grants permission
            grantedPermissions.clear()
            callback.onGetSearchResult(testSession, externalBrowser, "Test", 0, 10, null).get()
            assertEquals(1, grantedPermissions.size)
            assertEquals("com.google.android.projection.gearhead", grantedPermissions[0].first)
        } finally {
            testSession.release()
            testPlayer.release()
        }
    }
}
