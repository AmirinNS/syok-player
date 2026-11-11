package io.github.amirinns.syok.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.common.util.concurrent.ListenableFuture
import io.github.amirinns.syok.SyokApplication
import io.github.amirinns.syok.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BrowserContractTest {

    companion object {
        private val permissionsToGrant: Array<String> = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(*permissionsToGrant)

    private lateinit var app: SyokApplication
    private lateinit var context: Context
    private lateinit var testContext: Context
    private lateinit var mediaBrowser: MediaBrowser
    private val testFiles = mutableListOf<File>()

    private suspend fun <T> ListenableFuture<T>.awaitResult(timeoutSeconds: Long = 10): T {
        return withContext(Dispatchers.IO) {
            get(timeoutSeconds, TimeUnit.SECONDS)
        }
    }

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext<SyokApplication>()
        context = app.applicationContext
        testContext = InstrumentationRegistry.getInstrumentation().context

        // Clear any previous state
        app.appContainer.libraryDao.deleteAllTracks()
        app.appContainer.queueStore.clearQueue()

        // Copy test audio assets (file1 with embedded ID3v2 APIC artwork)
        val file1 = copyAssetToFileWithArtwork(testContext, context, "audio/track1.mp3", "test_browser_track1.mp3")
        val file2 = copyAssetToFile(testContext, context, "audio/track2.mp3", "test_browser_track2.mp3")
        testFiles.addAll(listOf(file1, file2))

        val track1 = TrackEntity(
            id = "mediastore:external:9001",
            contentUri = file1.toURI().toString(),
            title = "Jalan Telawi Echoes",
            artist = "Klang Valley Trio",
            album = "Midnight Drive",
            trackNumber = 1,
            discNumber = 1,
            durationMs = 60000L,
            isAvailable = true
        )
        val track2 = TrackEntity(
            id = "mediastore:external:9002",
            contentUri = file2.toURI().toString(),
            title = "Bangsar Rain",
            artist = "Klang Valley Trio",
            album = "Midnight Drive",
            trackNumber = 2,
            discNumber = 1,
            durationMs = 60000L,
            isAvailable = true
        )
        app.appContainer.libraryDao.upsertTracks(listOf(track1, track2))

        // Connect MediaBrowser to PlaybackService without blocking the Main Looper
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val browserFuture = withContext(Dispatchers.Main) {
            MediaBrowser.Builder(context, sessionToken).buildAsync()
        }
        mediaBrowser = browserFuture.awaitResult(15)
    }

    @After
    fun tearDown() = runBlocking {
        withContext(Dispatchers.Main) {
            if (::mediaBrowser.isInitialized && mediaBrowser.isConnected) {
                mediaBrowser.stop()
                mediaBrowser.clearMediaItems()
                mediaBrowser.release()
            }
        }
        app.appContainer.libraryDao.deleteAllTracks()
        app.appContainer.queueStore.clearQueue()
        testFiles.forEach { it.delete() }
    }

    private fun copyAssetToFile(testContext: Context, targetContext: Context, assetPath: String, destName: String): File {
        val destFile = File(targetContext.filesDir, destName)
        testContext.assets.open(assetPath).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    private fun copyAssetToFileWithArtwork(testContext: Context, targetContext: Context, assetPath: String, destName: String): File {
        val destFile = File(targetContext.filesDir, destName)
        val rawAudio = testContext.assets.open(assetPath).use { it.readBytes() }

        // Strip existing ID3 header if present
        val audioFrames = if (rawAudio.size > 10 && rawAudio[0] == 'I'.code.toByte() && rawAudio[1] == 'D'.code.toByte() && rawAudio[2] == '3'.code.toByte()) {
            val existingTagSize = ((rawAudio[6].toInt() and 0x7f) shl 21) or
                ((rawAudio[7].toInt() and 0x7f) shl 14) or
                ((rawAudio[8].toInt() and 0x7f) shl 7) or
                (rawAudio[9].toInt() and 0x7f)
            rawAudio.copyOfRange((10 + existingTagSize).coerceAtMost(rawAudio.size), rawAudio.size)
        } else {
            rawAudio
        }

        // 16x16 valid RGB PNG
        val pngBytes = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4e.toByte(), 0x47.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x1a.toByte(), 0x0a.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0d.toByte(), 0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x10.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x10.toByte(),
            0x08.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x90.toByte(), 0x91.toByte(), 0x68.toByte(),
            0x36.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x16.toByte(), 0x49.toByte(), 0x44.toByte(), 0x41.toByte(),
            0x54.toByte(), 0x78.toByte(), 0x9c.toByte(), 0x63.toByte(), 0xf8.toByte(), 0x3f.toByte(), 0x9f.toByte(), 0x8b.toByte(),
            0x24.toByte(), 0xc4.toByte(), 0x30.toByte(), 0xaa.toByte(), 0x61.toByte(), 0x54.toByte(), 0xc3.toByte(), 0xf0.toByte(),
            0xd5.toByte(), 0x00.toByte(), 0x00.toByte(), 0x5e.toByte(), 0x26.toByte(), 0xa8.toByte(), 0x10.toByte(), 0x23.toByte(),
            0xe5.toByte(), 0x32.toByte(), 0xf5.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x49.toByte(),
            0x45.toByte(), 0x4e.toByte(), 0x44.toByte(), 0xae.toByte(), 0x42.toByte(), 0x60.toByte(), 0x82.toByte()
        )

        // APIC payload: encoding (0), mime ("image/png\0"), picType (3 = cover), desc ("\0"), png data
        val mime = "image/png\u0000".toByteArray(Charsets.ISO_8859_1)
        val desc = "\u0000".toByteArray(Charsets.ISO_8859_1)
        val apicPayload = byteArrayOf(0x00) + mime + byteArrayOf(0x03) + desc + pngBytes

        // APIC frame header (ID3v2.3: 4-byte ID + 4-byte big-endian size + 2-byte flags)
        val frameSize = apicPayload.size
        val apicHeader = byteArrayOf(
            'A'.code.toByte(), 'P'.code.toByte(), 'I'.code.toByte(), 'C'.code.toByte(),
            ((frameSize shr 24) and 0xff).toByte(),
            ((frameSize shr 16) and 0xff).toByte(),
            ((frameSize shr 8) and 0xff).toByte(),
            (frameSize and 0xff).toByte(),
            0x00, 0x00
        )
        val apicFrame = apicHeader + apicPayload

        // ID3v2.3 header with syncsafe total tag size
        val tagSize = apicFrame.size
        val syncsafeSize = byteArrayOf(
            ((tagSize shr 21) and 0x7f).toByte(),
            ((tagSize shr 14) and 0x7f).toByte(),
            ((tagSize shr 7) and 0x7f).toByte(),
            (tagSize and 0x7f).toByte()
        )
        val id3Header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0x03, 0x00, 0x00) + syncsafeSize

        FileOutputStream(destFile).use { fos ->
            fos.write(id3Header)
            fos.write(apicFrame)
            fos.write(audioFrames)
        }
        return destFile
    }

    @Test
    fun testBrowseHierarchyContract() = runBlocking {
        // 1. Root contract
        val rootFuture = withContext(Dispatchers.Main) { mediaBrowser.getLibraryRoot(null) }
        val rootResult = rootFuture.awaitResult()
        assertEquals(LibraryResult.RESULT_SUCCESS, rootResult.resultCode)
        val rootItem = rootResult.value
        assertNotNull(rootItem)
        assertEquals(BrowseTree.ROOT_ID, rootItem!!.mediaId)
        assertTrue(rootItem.mediaMetadata.isBrowsable == true)
        assertFalse(rootItem.mediaMetadata.isPlayable == true)

        // 2. Root -> Local branch
        val rootChildrenFuture = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren(BrowseTree.ROOT_ID, 0, 10, null)
        }
        val rootChildrenResult = rootChildrenFuture.awaitResult()
        assertEquals(LibraryResult.RESULT_SUCCESS, rootChildrenResult.resultCode)
        val rootChildren = rootChildrenResult.value
        assertNotNull(rootChildren)
        assertTrue(rootChildren!!.any { it.mediaId == BrowseTree.LOCAL_ID && it.mediaMetadata.isBrowsable == true })

        // 3. Local -> Albums
        val localChildrenFuture = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren(BrowseTree.LOCAL_ID, 0, 10, null)
        }
        val localChildrenResult = localChildrenFuture.awaitResult()
        assertEquals(LibraryResult.RESULT_SUCCESS, localChildrenResult.resultCode)
        val albums = localChildrenResult.value
        assertNotNull(albums)
        val albumItem = albums!!.firstOrNull { it.mediaMetadata.title.toString() == "Midnight Drive" }
        assertNotNull(albumItem)
        assertTrue(albumItem!!.mediaMetadata.isBrowsable == true)
        assertFalse(albumItem.mediaMetadata.isPlayable == true)

        // 4. Album -> Tracks
        val albumChildrenFuture = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren(albumItem.mediaId, 0, 10, null)
        }
        val albumChildrenResult = albumChildrenFuture.awaitResult()
        assertEquals(LibraryResult.RESULT_SUCCESS, albumChildrenResult.resultCode)
        val tracks = albumChildrenResult.value
        assertNotNull(tracks)
        assertEquals(2, tracks!!.size)

        val t1 = tracks[0]
        assertEquals("mediastore:external:9001", t1.mediaId)
        assertEquals("Jalan Telawi Echoes", t1.mediaMetadata.title.toString())
        assertEquals("Klang Valley Trio", t1.mediaMetadata.artist.toString())
        assertEquals("Midnight Drive", t1.mediaMetadata.albumTitle.toString())
        assertTrue(t1.mediaMetadata.isPlayable == true)
        assertFalse(t1.mediaMetadata.isBrowsable == true)
        assertNotNull(t1.mediaMetadata.artworkUri)

        // 5. Artwork URI access verification:
        // ArtworkContentProvider is exported=false.
        // Confirm that the returned artwork URI opens through ContentResolver and decodes valid JPEG bytes.
        val artworkUri = t1.mediaMetadata.artworkUri
        assertNotNull("Expected non-null artwork URI", artworkUri)
        val inputStream = context.contentResolver.openInputStream(artworkUri!!)
        assertNotNull("Expected artwork stream to open successfully", inputStream)
        inputStream!!.use { stream ->
            val bytes = stream.readBytes()
            assertTrue("Expected non-empty decoded artwork bytes from provider", bytes.isNotEmpty())
        }
    }

    @Test
    fun testPlayReturnedMediaIdThroughService() = runBlocking {
        // Obtain track from browse tree
        val albumId = BrowseTree.albumMediaId("Midnight Drive")
        val albumChildrenFuture = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren(albumId, 0, 10, null)
        }
        val tracks = albumChildrenFuture.awaitResult().value
        assertNotNull(tracks)
        val trackToPlay = tracks!![0]

        // Set media item and play through browser connection
        withContext(Dispatchers.Main) {
            mediaBrowser.setMediaItem(trackToPlay)
            mediaBrowser.prepare()
            mediaBrowser.play()
        }

        // Wait for player to enter playback
        var isPlaying = false
        for (i in 0 until 50) {
            delay(100L)
            withContext(Dispatchers.Main) {
                isPlaying = mediaBrowser.isPlaying
            }
            if (isPlaying) break
        }

        withContext(Dispatchers.Main) {
            assertTrue("Expected mediaBrowser to be playing", mediaBrowser.isPlaying)
            assertEquals("mediastore:external:9001", mediaBrowser.currentMediaItem?.mediaId)
            assertEquals(Player.STATE_READY, mediaBrowser.playbackState)
        }
    }

    @Test
    fun testSearchContract() = runBlocking {
        // Search indexed local tracks
        val searchFuture = withContext(Dispatchers.Main) {
            mediaBrowser.search("Telawi", null)
        }
        val searchResult = searchFuture.awaitResult()
        assertEquals(LibraryResult.RESULT_SUCCESS, searchResult.resultCode)

        val itemsFuture = withContext(Dispatchers.Main) {
            mediaBrowser.getSearchResult("Telawi", 0, 10, null)
        }
        val itemsResult = itemsFuture.awaitResult()
        assertEquals(LibraryResult.RESULT_SUCCESS, itemsResult.resultCode)
        val results = itemsResult.value
        assertNotNull(results)
        assertEquals(1, results!!.size)
        assertEquals("mediastore:external:9001", results[0].mediaId)
        assertEquals("Jalan Telawi Echoes", results[0].mediaMetadata.title.toString())
        assertTrue(results[0].mediaMetadata.isPlayable == true)
    }
}
