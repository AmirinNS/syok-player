package io.github.amirinns.syok.local

/** Metadata extracted with the same platform retriever used for embedded artwork. */
data class AudioTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val trackNumber: Int = 0,
    val discNumber: Int = 1,
    val durationMs: Long = 0L
)
