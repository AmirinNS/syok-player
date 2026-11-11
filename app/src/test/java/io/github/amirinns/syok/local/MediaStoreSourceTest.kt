package io.github.amirinns.syok.local

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.amirinns.syok.data.LibraryDao
import io.github.amirinns.syok.data.LibraryDatabase
import io.github.amirinns.syok.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class MediaStoreSourceTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var database: LibraryDatabase
    private lateinit var libraryDao: LibraryDao
    private lateinit var mediaStoreSource: MediaStoreSource

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        contentResolver = context.contentResolver
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        libraryDao = database.libraryDao()
        mediaStoreSource = MediaStoreSource(contentResolver, libraryDao, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun insertAudioRow(
        id: Long,
        title: String?,
        artist: String?,
        album: String?,
        track: Int?,
        duration: Long?,
        displayName: String
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media._ID, id)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, album)
            put(MediaStore.Audio.Media.TRACK, track ?: 0)
            put(MediaStore.Audio.Media.DURATION, duration ?: 0L)
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
        }
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        assertNotNull(uri)
        return uri!!
    }

    @Test
    fun missingTags_usesFallbacksAndVolumeQualifiedId() = runBlocking {
        // Missing title, artist, album, duration, track
        insertAudioRow(
            id = 101L,
            title = null,
            artist = null,
            album = null,
            track = null,
            duration = null,
            displayName = "song_without_tags.mp3"
        )

        val result = mediaStoreSource.scan("external")
        assertTrue("Scan should succeed", result.isSuccess)
        assertEquals(1, result.getOrNull())

        val track = libraryDao.getTrackById("mediastore:external:101")
        assertNotNull("Track should exist with volume-qualified ID", track)
        track!!
        assertEquals("mediastore:external:101", track.id)
        assertEquals(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, 101L).toString(), track.contentUri)
        assertEquals("song_without_tags", track.title)
        assertEquals("Unknown Artist", track.artist)
        assertEquals("Unknown Album", track.album)
        assertEquals(0, track.trackNumber)
        assertEquals(1, track.discNumber)
        assertEquals(0L, track.durationMs)
        assertTrue(track.isAvailable)
    }

    @Test
    fun duplicateScans_doNotDuplicateTracks() = runBlocking {
        insertAudioRow(
            id = 201L,
            title = "Track One",
            artist = "Artist A",
            album = "Album A",
            track = 1,
            duration = 180000L,
            displayName = "track_1.mp3"
        )
        insertAudioRow(
            id = 202L,
            title = "Track Two",
            artist = "Artist A",
            album = "Album A",
            track = 2,
            duration = 200000L,
            displayName = "track_2.mp3"
        )

        // First scan
        val firstResult = mediaStoreSource.scan("external")
        assertTrue(firstResult.isSuccess)
        assertEquals(2, firstResult.getOrNull())
        assertEquals(2, libraryDao.getTrackCountSync())

        // Duplicate/rescan
        val secondResult = mediaStoreSource.scan("external")
        assertTrue(secondResult.isSuccess)
        assertEquals(2, secondResult.getOrNull())
        assertEquals(2, libraryDao.getTrackCountSync())
    }

    @Test
    fun albumTrackOrdering_ordersByDiscAndTrackNumber() = runBlocking {
        val albumName = "Masterpiece"
        // Insert tracks out of order
        // Track 2 on Disc 1
        insertAudioRow(
            id = 301L,
            title = "Disc 1 Track 2",
            artist = "Composer",
            album = albumName,
            track = 1002, // disc 1, track 2 encoded as disc * 1000 + track
            duration = 150000L,
            displayName = "d1t2.mp3"
        )
        // Track 1 on Disc 2
        insertAudioRow(
            id = 302L,
            title = "Disc 2 Track 1",
            artist = "Composer",
            album = albumName,
            track = 2001, // disc 2, track 1
            duration = 160000L,
            displayName = "d2t1.mp3"
        )
        // Track 1 on Disc 1
        insertAudioRow(
            id = 303L,
            title = "Disc 1 Track 1",
            artist = "Composer",
            album = albumName,
            track = 1001, // disc 1, track 1
            duration = 140000L,
            displayName = "d1t1.mp3"
        )

        val scanResult = mediaStoreSource.scan("external")
        assertTrue(scanResult.isSuccess)

        val orderedTracks = libraryDao.getTracksByAlbumSync(albumName)
        assertEquals(3, orderedTracks.size)
        assertEquals("Disc 1 Track 1", orderedTracks[0].title)
        assertEquals(1, orderedTracks[0].discNumber)
        assertEquals(1, orderedTracks[0].trackNumber)

        assertEquals("Disc 1 Track 2", orderedTracks[1].title)
        assertEquals(1, orderedTracks[1].discNumber)
        assertEquals(2, orderedTracks[1].trackNumber)

        assertEquals("Disc 2 Track 1", orderedTracks[2].title)
        assertEquals(2, orderedTracks[2].discNumber)
        assertEquals(1, orderedTracks[2].trackNumber)
    }

    @Test
    fun accessFailure_preservesPreviousIndex() = runBlocking {
        // Pre-populate index
        val initialTrack = TrackEntity(
            id = "mediastore:external:999",
            contentUri = "content://media/external/audio/media/999",
            title = "Preserved Track",
            artist = "Survivor",
            album = "Precious Archive",
            trackNumber = 1,
            discNumber = 1,
            durationMs = 210000L,
            isAvailable = true
        )
        libraryDao.upsertTrack(initialTrack)
        assertEquals(1, libraryDao.getTrackCountSync())

        // Create a MediaStoreSource with a failing query
        val failingSource = MediaStoreSource(
            contentResolver = contentResolver,
            libraryDao = libraryDao,
            ioDispatcher = Dispatchers.Unconfined,
            queryOverride = { _, _, _, _, _ ->
                throw SecurityException("Mock Permission Denied")
            }
        )
        val result = failingSource.scan("external")

        assertTrue("Scan must report failure on access error", result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)

        // Verify the previous index was NOT destroyed or modified
        val tracksAfterFailure = libraryDao.getAllTracksSync()
        assertEquals(1, tracksAfterFailure.size)
        assertEquals("mediastore:external:999", tracksAfterFailure[0].id)
        assertEquals("Preserved Track", tracksAfterFailure[0].title)
    }
}
