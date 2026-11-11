package io.github.amirinns.syok.ui

import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.amirinns.syok.MainActivity
import io.github.amirinns.syok.SyokApplication
import io.github.amirinns.syok.data.TrackEntity
import io.github.amirinns.syok.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LocalPlaybackTest {

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

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(*permissionsToGrant)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var app: SyokApplication
    private val testFiles = mutableListOf<File>()

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext<SyokApplication>()
        val targetContext = app.applicationContext
        val testContext = InstrumentationRegistry.getInstrumentation().context

        // Reset player if running
        try {
            val token = SessionToken(targetContext, ComponentName(targetContext, PlaybackService::class.java))
            val future = MediaController.Builder(targetContext, token).buildAsync()
            val controller = future.get(3, TimeUnit.SECONDS)
            withContext(Dispatchers.Main) {
                controller.stop()
                controller.clearMediaItems()
            }
            controller.release()
        } catch (_: Exception) {
        }

        // Copy test audio assets from test APK to target app files dir
        val file1 = copyAssetToFile(testContext, targetContext, "audio/track1.mp3", "test_track1.mp3")
        val file2 = copyAssetToFile(testContext, targetContext, "audio/track2.mp3", "test_track2.mp3")
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
    }

    @After
    fun tearDown() = runBlocking {
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

    @Test
    fun testAlbumSelection_startsPlayback() {
        composeTestRule.waitForIdle()

        // Wait for album to be visible in the Library grid
        composeTestRule.waitUntil(timeoutMillis = 10000L) {
            try {
                composeTestRule.onAllNodesWithText("Midnight Drive").onFirst().assertIsDisplayed()
                true
            } catch (_: Throwable) {
                false
            }
        }

        // Tap the album card to start playback and transition to Now Playing
        composeTestRule.onAllNodesWithText("Midnight Drive").onFirst().performClick()
        composeTestRule.waitForIdle()

        // Wait for Now Playing screen to show track metadata
        composeTestRule.waitUntil(timeoutMillis = 10000L) {
            try {
                composeTestRule.onNodeWithText("Jalan Telawi Echoes").assertIsDisplayed()
                true
            } catch (_: Throwable) {
                false
            }
        }

        composeTestRule.onNodeWithText("Jalan Telawi Echoes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Klang Valley Trio").assertIsDisplayed()
    }

    @Test
    fun testPlayPauseSemantics_andReconnectionOnActivityRecreation() {
        composeTestRule.waitForIdle()

        // Wait for album to be visible in the Library grid
        composeTestRule.waitUntil(timeoutMillis = 10000L) {
            try {
                composeTestRule.onAllNodesWithText("Midnight Drive").onFirst().assertIsDisplayed()
                true
            } catch (_: Throwable) {
                false
            }
        }

        // Start playback by clicking the album
        composeTestRule.onAllNodesWithText("Midnight Drive").onFirst().performClick()
        composeTestRule.waitForIdle()

        // Wait for player to enter playing state and show pause button ("❚❚")
        composeTestRule.waitUntil(timeoutMillis = 10000L) {
            try {
                composeTestRule.onAllNodesWithText("❚❚").onFirst().assertIsDisplayed()
                true
            } catch (_: Throwable) {
                false
            }
        }

        // Tap Play/Pause button ("❚❚")
        composeTestRule.onAllNodesWithText("❚❚").onFirst().performClick()
        composeTestRule.waitForIdle()

        // Now button should show "▶"
        composeTestRule.waitUntil(timeoutMillis = 10000L) {
            try {
                composeTestRule.onAllNodesWithText("▶").onFirst().assertIsDisplayed()
                true
            } catch (_: Throwable) {
                false
            }
        }
        composeTestRule.onAllNodesWithText("▶").onFirst().assertIsDisplayed()

        // Reconnect test: recreate activity
        val scenario = composeTestRule.activityRule.scenario
        scenario.recreate()
        composeTestRule.waitForIdle()

        // Confirm activity reconnects to the same player and session state is preserved
        composeTestRule.waitUntil(timeoutMillis = 10000L) {
            try {
                composeTestRule.onNodeWithText("Jalan Telawi Echoes").assertIsDisplayed()
                true
            } catch (_: Throwable) {
                false
            }
        }
        composeTestRule.onNodeWithText("Jalan Telawi Echoes").assertIsDisplayed()
    }
}
