package io.github.amirinns.syok.playback

import android.content.Context
import android.content.SharedPreferences
import io.github.amirinns.syok.data.LibraryDao
import io.github.amirinns.syok.data.TrackEntity
import org.json.JSONArray
import org.json.JSONException

/**
 * Persists playback queue IDs and position in app-private preferences.
 * Updates at bounded intervals and queue changes.
 * Restores paused after process death and handles missing IDs gracefully.
 */
class QueueStore(
    context: Context,
    private val libraryDao: LibraryDao,
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    private val minSaveIntervalMs: Long = 1000L
) {

    private var lastSaveTimeMs: Long = 0L

    data class SavedQueue(
        val trackIds: List<String>,
        val currentIndex: Int,
        val positionMs: Long,
        val playWhenReady: Boolean = false // Always false when restored after process death
    )

    /**
     * Saves the full queue of track IDs, current index, and playback position.
     */
    fun saveQueue(trackIds: List<String>, currentIndex: Int, positionMs: Long) {
        val jsonArray = JSONArray()
        trackIds.forEach { jsonArray.put(it) }

        prefs.edit()
            .putString(KEY_QUEUE_IDS, jsonArray.toString())
            .putInt(KEY_CURRENT_INDEX, currentIndex)
            .putLong(KEY_POSITION_MS, positionMs)
            .putLong(KEY_LAST_SAVED_MS, System.currentTimeMillis())
            .apply()

        lastSaveTimeMs = System.currentTimeMillis()
    }

    /**
     * Saves only the playback position and index, throttled by [minSaveIntervalMs].
     */
    fun savePosition(currentIndex: Int, positionMs: Long, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || (now - lastSaveTimeMs >= minSaveIntervalMs)) {
            prefs.edit()
                .putInt(KEY_CURRENT_INDEX, currentIndex)
                .putLong(KEY_POSITION_MS, positionMs)
                .putLong(KEY_LAST_SAVED_MS, now)
                .apply()
            lastSaveTimeMs = now
        }
    }

    /**
     * Loads the saved queue state from preferences.
     * Returns null if no queue was saved or if the saved queue is empty.
     */
    fun loadQueue(): SavedQueue? {
        val jsonString = prefs.getString(KEY_QUEUE_IDS, null) ?: return null
        if (jsonString.isBlank()) return null

        val trackIds = try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (_: JSONException) {
            return null
        }

        if (trackIds.isEmpty()) return null

        val currentIndex = prefs.getInt(KEY_CURRENT_INDEX, 0).coerceIn(0, trackIds.size - 1)
        val positionMs = prefs.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L)

        // Always restores paused after process death
        return SavedQueue(
            trackIds = trackIds,
            currentIndex = currentIndex,
            positionMs = positionMs,
            playWhenReady = false
        )
    }

    /**
     * Resolves the saved queue against the database, filtering out deleted
     * or unavailable tracks. Adjusts the current index safely.
     *
     * @param saved The loaded queue state.
     * @return Pair containing the list of available TrackEntities and the adjusted current index.
     */
    suspend fun resolveAvailableTracks(saved: SavedQueue): Pair<List<TrackEntity>, Int> {
        val currentTargetId = saved.trackIds.getOrNull(saved.currentIndex)
        val availableTracks = mutableListOf<TrackEntity>()

        for (id in saved.trackIds) {
            val track = libraryDao.getTrackById(id)
            if (track != null && track.isAvailable) {
                availableTracks.add(track)
            }
        }

        if (availableTracks.isEmpty()) {
            return Pair(emptyList(), 0)
        }

        // Find where the previously playing track ended up
        val adjustedIndex = if (currentTargetId != null) {
            val matchIndex = availableTracks.indexOfFirst { it.id == currentTargetId }
            if (matchIndex >= 0) matchIndex else 0
        } else {
            saved.currentIndex.coerceIn(0, availableTracks.size - 1)
        }

        return Pair(availableTracks, adjustedIndex)
    }

    /**
     * Clears all saved queue data.
     */
    fun clearQueue() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "syok_queue_store"
        const val KEY_QUEUE_IDS = "queue_ids"
        const val KEY_CURRENT_INDEX = "current_index"
        const val KEY_POSITION_MS = "position_ms"
        const val KEY_LAST_SAVED_MS = "last_saved_ms"
    }
}
