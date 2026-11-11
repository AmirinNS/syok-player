package io.github.amirinns.syok.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import io.github.amirinns.syok.MainActivity
import io.github.amirinns.syok.SyokApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PlaybackService owns the sole ExoPlayer and MediaLibrarySession for the application.
 * Background playback, audio focus, media keys, and system notifications converge here.
 */
class PlaybackService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var periodicSaveJob: Job? = null

    private val queueStore by lazy {
        (application as SyokApplication).appContainer.queueStore
    }

    private val libraryDao by lazy {
        (application as SyokApplication).appContainer.libraryDao
    }

    private val browseTree by lazy {
        (application as SyokApplication).appContainer.browseTree
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Build the sole ExoPlayer instance
        val exoPlayer = PlayerFactory.createPlayer(this)
        this.player = exoPlayer

        // 2. Build LibraryCallback
        val callback = LibraryCallback(this, libraryDao, browseTree)

        // 3. Session activity PendingIntent to return to MainActivity
        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 4. Build MediaLibrarySession
        mediaLibrarySession = MediaLibrarySession.Builder(this, exoPlayer, callback)
            .setSessionActivity(pendingIntent)
            .build()

        // 5. Restore previous queue paused if available
        restoreSavedQueue(exoPlayer)

        // 6. Listen to player events for queue persistence
        exoPlayer.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                persistQueueState(force = true)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                persistQueueState(force = true)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startPeriodicSave()
                } else {
                    stopPeriodicSave()
                    persistQueueState(force = false)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    persistQueueState(force = true)
                }
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopPeriodicSave()
        persistQueueState(force = true)
        serviceScope.cancel()

        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
        player = null

        super.onDestroy()
    }

    private fun persistQueueState(force: Boolean) {
        val p = player ?: return
        val count = p.mediaItemCount
        if (count == 0) return

        val trackIds = (0 until count).map { i -> p.getMediaItemAt(i).mediaId }
        val currentIndex = p.currentMediaItemIndex
        val positionMs = p.currentPosition.coerceAtLeast(0L)

        if (force) {
            queueStore.saveQueue(trackIds, currentIndex, positionMs)
        } else {
            queueStore.savePosition(currentIndex, positionMs)
        }
    }

    private fun startPeriodicSave() {
        if (periodicSaveJob?.isActive == true) return
        periodicSaveJob = serviceScope.launch {
            while (isActive) {
                delay(3000L)
                persistQueueState(force = false)
            }
        }
    }

    private fun stopPeriodicSave() {
        periodicSaveJob?.cancel()
        periodicSaveJob = null
    }

    private fun restoreSavedQueue(exoPlayer: ExoPlayer) {
        val saved = queueStore.loadQueue() ?: return
        serviceScope.launch {
            val (tracks, targetIndex) = queueStore.resolveAvailableTracks(saved)
            if (tracks.isNotEmpty()) {
                val mediaItems = tracks.map { track ->
                    MediaItem.Builder()
                        .setMediaId(track.id)
                        .setUri(Uri.parse(track.contentUri))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artist)
                                .setAlbumTitle(track.album)
                                .setTrackNumber(track.trackNumber)
                                .setDiscNumber(track.discNumber)
                                .setIsPlayable(true)
                                .build()
                        )
                        .build()
                }

                withContext(Dispatchers.Main) {
                    if (exoPlayer.mediaItemCount == 0) {
                        exoPlayer.setMediaItems(mediaItems, targetIndex, saved.positionMs)
                        exoPlayer.playWhenReady = false // Restores paused after process death
                        exoPlayer.prepare()
                    }
                }
            }
        }
    }
}
