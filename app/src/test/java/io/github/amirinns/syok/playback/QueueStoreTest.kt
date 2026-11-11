package io.github.amirinns.syok.playback

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.amirinns.syok.data.LibraryDao
import io.github.amirinns.syok.data.LibraryDatabase
import io.github.amirinns.syok.data.TrackEntity
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
class QueueStoreTest {

    private lateinit var context: Context
    private lateinit var database: LibraryDatabase
    private lateinit var libraryDao: LibraryDao
    private lateinit var queueStore: QueueStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        libraryDao = database.libraryDao()
        queueStore = QueueStore(context, libraryDao)
        queueStore.clearQueue()
    }

    @After
    fun tearDown() {
        queueStore.clearQueue()
        database.close()
    }

    @Test
    fun saveAndLoadQueue_restoresStatePaused() {
        val trackIds = listOf("mediastore:external:1", "mediastore:external:2", "mediastore:external:3")
        queueStore.saveQueue(trackIds = trackIds, currentIndex = 1, positionMs = 45000L)

        val loaded = queueStore.loadQueue()
        assertNotNull(loaded)
        assertEquals(trackIds, loaded!!.trackIds)
        assertEquals(1, loaded.currentIndex)
        assertEquals(45000L, loaded.positionMs)
        // Must restore paused after process death
        assertFalse(loaded.playWhenReady)
    }

    @Test
    fun loadQueue_whenEmpty_returnsNull() {
        assertNull(queueStore.loadQueue())
    }

    @Test
    fun savePosition_updatesIndexAndPosition() {
        val trackIds = listOf("id1", "id2")
        queueStore.saveQueue(trackIds, 0, 1000L)

        queueStore.savePosition(currentIndex = 1, positionMs = 25000L, force = true)

        val loaded = queueStore.loadQueue()
        assertNotNull(loaded)
        assertEquals(1, loaded!!.currentIndex)
        assertEquals(25000L, loaded.positionMs)
    }

    @Test
    fun resolveAvailableTracks_filtersMissingAndUnavailableIds() = runBlocking {
        // Seed only track 1 and track 3; track 2 is deleted/missing
        val track1 = TrackEntity("id1", "uri1", "Track 1", "Artist", "Album", 1, 1, 1000L, true)
        val track3 = TrackEntity("id3", "uri3", "Track 3", "Artist", "Album", 3, 1, 1000L, true)
        val trackUnavailable = TrackEntity("id4", "uri4", "Track 4", "Artist", "Album", 4, 1, 1000L, false)
        libraryDao.upsertTracks(listOf(track1, track3, trackUnavailable))

        // Saved queue references id1, id2 (missing), id3, id4 (unavailable)
        val saved = QueueStore.SavedQueue(
            trackIds = listOf("id1", "id2", "id3", "id4"),
            currentIndex = 2, // originally pointing to id3
            positionMs = 15000L,
            playWhenReady = false
        )

        val (available, adjustedIndex) = queueStore.resolveAvailableTracks(saved)
        assertEquals(2, available.size)
        assertEquals("id1", available[0].id)
        assertEquals("id3", available[1].id)
        // id3 is at index 1 in the filtered list
        assertEquals(1, adjustedIndex)
    }

    @Test
    fun resolveAvailableTracks_whenCurrentTrackMissing_defaultsToSafeIndex() = runBlocking {
        val track1 = TrackEntity("id1", "uri1", "Track 1", "Artist", "Album", 1, 1, 1000L, true)
        libraryDao.upsertTracks(listOf(track1))

        // Current track was id2 (missing)
        val saved = QueueStore.SavedQueue(
            trackIds = listOf("id1", "id2"),
            currentIndex = 1,
            positionMs = 10000L,
            playWhenReady = false
        )

        val (available, adjustedIndex) = queueStore.resolveAvailableTracks(saved)
        assertEquals(1, available.size)
        assertEquals("id1", available[0].id)
        assertEquals(0, adjustedIndex)
    }

    @Test
    fun resolveAvailableTracks_whenAllTracksMissing_returnsEmpty() = runBlocking {
        val saved = QueueStore.SavedQueue(
            trackIds = listOf("missing1", "missing2"),
            currentIndex = 0,
            positionMs = 0L,
            playWhenReady = false
        )

        val (available, adjustedIndex) = queueStore.resolveAvailableTracks(saved)
        assertTrue(available.isEmpty())
        assertEquals(0, adjustedIndex)
    }
}
