package io.github.amirinns.syok.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/**
 * Factory for creating the single application-wide ExoPlayer instance.
 * Configured with media audio attributes, automatic audio-focus handling,
 * and noisy-output pause. Uses Media3 defaults before tuning buffer sizes.
 */
object PlayerFactory {
    fun createPlayer(context: Context): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
}
