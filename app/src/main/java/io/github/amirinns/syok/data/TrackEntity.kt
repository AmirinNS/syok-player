package io.github.amirinns.syok.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an indexed audio track.
 *
 * @property id Source-qualified unique identifier (e.g., "mediastore:external:12345").
 * @property contentUri Content URI string pointing to the playable media resource.
 * @property title Track title (with fallback if tag is absent).
 * @property artist Artist name (with fallback if tag is absent).
 * @property album Album name (with fallback if tag is absent).
 * @property trackNumber Track number on the disc/album.
 * @property discNumber Disc number in the set.
 * @property durationMs Duration of the track in milliseconds.
 * @property isAvailable Whether the media is currently accessible and available for playback.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int = 0,
    val discNumber: Int = 1,
    val durationMs: Long = 0L,
    val isAvailable: Boolean = true,
    val sourceId: String? = null
)
