package io.github.amirinns.syok.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.amirinns.syok.SyokApplication
import io.github.amirinns.syok.data.TrackEntity
import io.github.amirinns.syok.playback.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AlbumUiModel(
    val title: String,
    val artist: String,
    val tracksCount: Int,
    val firstContentUri: String,
    val tracks: List<TrackEntity>
)

data class PlaybackUiState(
    val isConnected: Boolean = false,
    val currentMediaItem: MediaItem? = null,
    val currentTitle: String = "No track playing",
    val currentArtist: String = "Select an album to start",
    val currentAlbum: String = "",
    val currentContentUri: String = "",
    val currentTrackIndex: Int = 0,
    val totalTracksInQueue: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<MediaItem> = emptyList(),
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val errorMessage: String? = null,
    val nextTrackTitle: String? = null
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContainer = (application as SyokApplication).appContainer
    private val libraryDao = appContainer.libraryDao
    val mediaStoreSource = appContainer.mediaStoreSource
    val artworkLoader = appContainer.artworkLoader

    private var mediaController: MediaController? = null
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val trackCountFlow = libraryDao.getTrackCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val albumsFlow: StateFlow<List<AlbumUiModel>> = libraryDao.getAllTracks().map { tracks ->
        tracks.groupBy { it.album }.map { (albumName, albumTracks) ->
            val first = albumTracks.firstOrNull()
            AlbumUiModel(
                title = albumName,
                artist = first?.artist ?: "Unknown Artist",
                tracksCount = albumTracks.size,
                firstContentUri = first?.contentUri ?: "",
                tracks = albumTracks
            )
        }.sortedBy { it.title.lowercase() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        connectController()
    }

    private fun connectController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                this.mediaController = controller
                controller.addListener(playerListener)
                updateStateFromPlayer(controller)
            } catch (e: Exception) {
                _playbackState.value = _playbackState.value.copy(
                    errorMessage = "Failed to connect to player: ${e.message}"
                )
            }
        }, ContextCompat.getMainExecutor(getApplication()))
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateStateFromPlayer(player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startProgressTracker()
            } else {
                stopProgressTracker()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.value = _playbackState.value.copy(
                errorMessage = "Playback error: ${error.localizedMessage}"
            )
        }
    }

    private fun updateStateFromPlayer(player: Player) {
        val currentItem = player.currentMediaItem
        val metadata = currentItem?.mediaMetadata
        val title = metadata?.title?.toString()
            ?: currentItem?.mediaId?.substringAfterLast(':')
            ?: "No track playing"
        val artist = metadata?.artist?.toString() ?: "Unknown Artist"
        val album = metadata?.albumTitle?.toString() ?: ""
        val contentUri = currentItem?.localConfiguration?.uri?.toString() ?: ""

        val count = player.mediaItemCount
        val queue = (0 until count).map { i -> player.getMediaItemAt(i) }
        val currentIndex = player.currentMediaItemIndex

        val nextTitle = if (count > 0 && currentIndex in 0 until count) {
            val nextIdx = (currentIndex + 1) % count
            queue.getOrNull(nextIdx)?.mediaMetadata?.title?.toString()
        } else null

        val isBuffering = player.playbackState == Player.STATE_BUFFERING
        val isEnded = player.playbackState == Player.STATE_ENDED
        val duration = if (player.duration > 0L) player.duration else 0L
        val position = player.currentPosition.coerceAtLeast(0L)

        _playbackState.value = _playbackState.value.copy(
            isConnected = true,
            currentMediaItem = currentItem,
            currentTitle = title,
            currentArtist = artist,
            currentAlbum = album,
            currentContentUri = contentUri,
            currentTrackIndex = currentIndex,
            totalTracksInQueue = count,
            isPlaying = player.isPlaying,
            isBuffering = isBuffering,
            positionMs = position,
            durationMs = duration,
            queue = queue,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            errorMessage = null,
            nextTrackTitle = nextTitle
        )

        if (player.isPlaying) {
            startProgressTracker()
        } else {
            stopProgressTracker()
        }
    }

    private fun startProgressTracker() {
        if (progressJob?.isActive == true) return
        progressJob = viewModelScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _playbackState.value = _playbackState.value.copy(
                            positionMs = controller.currentPosition.coerceAtLeast(0L),
                            durationMs = if (controller.duration > 0L) controller.duration else _playbackState.value.durationMs
                        )
                    }
                }
                delay(500L)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun playAlbum(album: AlbumUiModel, startIndex: Int = 0) {
        val controller = mediaController ?: return
        val mediaItems = album.tracks.map { track ->
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
        if (mediaItems.isEmpty()) return

        val targetIndex = startIndex.coerceIn(0, mediaItems.size - 1)
        controller.setMediaItems(mediaItems, targetIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        } else if (controller.mediaItemCount > 0) {
            controller.seekTo(0, 0L)
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else if (controller.mediaItemCount > 0) {
            controller.seekTo(controller.mediaItemCount - 1, 0L)
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs.coerceIn(0L, _playbackState.value.durationMs.coerceAtLeast(0L)))
        _playbackState.value = _playbackState.value.copy(positionMs = positionMs)
    }

    fun skipToQueueIndex(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.play()
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun toggleRepeat() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
    }

    fun scanMedia(onComplete: ((Result<Int>) -> Unit)? = null) {
        viewModelScope.launch {
            _isScanning.value = true
            val res = mediaStoreSource.scan()
            _isScanning.value = false
            onComplete?.invoke(res)
        }
    }

    override fun onCleared() {
        stopProgressTracker()
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        mediaController = null
        super.onCleared()
    }
}
