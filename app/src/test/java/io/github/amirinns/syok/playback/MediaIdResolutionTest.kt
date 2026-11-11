package io.github.amirinns.syok.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaIdResolutionTest {

    private lateinit var context: Context
    private lateinit var database: LibraryDatabase
    private lateinit var libraryDao: LibraryDao
    private lateinit var libraryCallback: LibraryCallback
    private lateinit var testPlayer: ExoPlayer
    private lateinit var testSession: MediaSession
    private lateinit var testController: MediaSession.ControllerInfo

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        libraryDao = database.libraryDao()

        // Seed test tracks
        val availableTrack = TrackEntity(
            id = "mediastore:external:100",
            contentUri = "content://media/external/audio/media/100",
            title = "Jalan Telawi Echoes",
            artist = "Klang Valley Trio",
            album = "Midnight Drive",
            trackNumber = 1,
            discNumber = 1,
            durationMs = 185000L,
            isAvailable = true
        )
        val unavailableTrack = TrackEntity(
            id = "mediastore:external:101",
            contentUri = "content://media/external/audio/media/101",
            title = "Deleted Song",
            artist = "Unknown",
            album = "Midnight Drive",
            trackNumber = 2,
            discNumber = 1,
            durationMs = 120000L,
            isAvailable = false
        )
        libraryDao.upsertTracks(listOf(availableTrack, unavailableTrack))

        libraryCallback = LibraryCallback(libraryDao, Dispatchers.Unconfined)
        testPlayer = ExoPlayer.Builder(context).build()
        testSession = MediaSession.Builder(context, testPlayer).build()
        testController = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
            "io.github.amirinns.syok.test",
            1000,
            1000,
            1,
            1,
            false,
            Bundle.EMPTY,
            false
        )
    }

    @After
    fun tearDown() {
        testSession.release()
        testPlayer.release()
        database.close()
    }

    @Test
    fun onAddMediaItems_resolvesKnownLocalTrack_toPrivateContentUri() = runBlocking {
        // Request item with known ID and caller-supplied dummy URI
        val requestItem = MediaItem.Builder()
            .setMediaId("mediastore:external:100")
            .setUri("https://arbitrary-untrusted.com/audio.mp3")
            .build()

        val future = libraryCallback.onAddMediaItems(
            mediaSession = testSession,
            controller = testController,
            mediaItems = listOf(requestItem)
        )

        val resolved = future.get()
        assertEquals(1, resolved.size)
        val item = resolved[0]
        assertEquals("mediastore:external:100", item.mediaId)
        // Must resolve to private content URI from DB, rejecting caller-supplied URI
        assertEquals("content://media/external/audio/media/100", item.localConfiguration?.uri.toString())
        assertEquals("Jalan Telawi Echoes", item.mediaMetadata.title.toString())
        assertEquals("Klang Valley Trio", item.mediaMetadata.artist.toString())
        assertEquals("Midnight Drive", item.mediaMetadata.albumTitle.toString())
    }

    @Test
    fun onAddMediaItems_rejectsUnknownIds_andArbitraryUris() = runBlocking {
        val unknownIdItem = MediaItem.Builder()
            .setMediaId("mediastore:external:9999")
            .build()
        val blankIdItemWithArbitraryUri = MediaItem.Builder()
            .setUri("https://untrusted-host.com/stream.mp3")
            .build()
        val unavailableItem = MediaItem.Builder()
            .setMediaId("mediastore:external:101")
            .build()

        val future = libraryCallback.onAddMediaItems(
            mediaSession = testSession,
            controller = testController,
            mediaItems = listOf(unknownIdItem, blankIdItemWithArbitraryUri, unavailableItem)
        )

        val resolved = future.get()
        // All unknown, unavailable, or untrusted items must be rejected
        assertTrue(resolved.isEmpty())
    }
}
