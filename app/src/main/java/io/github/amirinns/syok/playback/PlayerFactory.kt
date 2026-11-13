package io.github.amirinns.syok.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Factory for creating the single application-wide ExoPlayer instance.
 * Configured with media audio attributes, automatic audio-focus handling,
 * noisy-output pause, and strict HTTPS data source enforcing allowCrossProtocolRedirects = false.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlayerFactory {
    fun createPlayer(context: Context): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Syok/1.0 (Android Head Unit)")
            .setAllowCrossProtocolRedirects(false)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
}

