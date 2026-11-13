package io.github.amirinns.syok.radio

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a user-saved internet radio station.
 * Persisted in Room version 3 additive 'stations' table.
 *
 * Station media IDs use the stable prefix "station:<id>".
 */
@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String
)
