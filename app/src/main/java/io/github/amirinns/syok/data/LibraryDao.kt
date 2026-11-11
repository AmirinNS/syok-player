package io.github.amirinns.syok.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Upsert
    suspend fun upsertTrack(track: TrackEntity)

    @Query("SELECT * FROM tracks WHERE album = :album AND isAvailable = 1 ORDER BY discNumber ASC, trackNumber ASC, title ASC")
    fun getTracksByAlbum(album: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE album = :album AND isAvailable = 1 ORDER BY discNumber ASC, trackNumber ASC, title ASC")
    suspend fun getTracksByAlbumSync(album: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 ORDER BY album ASC, discNumber ASC, trackNumber ASC, title ASC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 ORDER BY album ASC, discNumber ASC, trackNumber ASC, title ASC")
    suspend fun getAllTracksSync(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT COUNT(*) FROM tracks WHERE isAvailable = 1")
    fun getTrackCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks WHERE isAvailable = 1")
    suspend fun getTrackCountSync(): Int

    @Query("SELECT DISTINCT album FROM tracks WHERE isAvailable = 1 ORDER BY album ASC")
    fun getAlbums(): Flow<List<String>>

    @Query("SELECT DISTINCT album FROM tracks WHERE isAvailable = 1 ORDER BY album ASC")
    suspend fun getAlbumsSync(): List<String>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%') ORDER BY title ASC")
    suspend fun searchTracksSync(query: String): List<TrackEntity>

    @Delete
    suspend fun deleteTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()
}
