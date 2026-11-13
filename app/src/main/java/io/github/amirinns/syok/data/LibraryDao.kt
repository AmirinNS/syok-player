package io.github.amirinns.syok.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import io.github.amirinns.syok.radio.StationEntity
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

    @Query("UPDATE tracks SET isAvailable = :available WHERE sourceId = :sourceId")
    suspend fun setSourceAvailability(sourceId: String, available: Boolean)

    @Upsert
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    suspend fun getFolders(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE treeUri = :treeUri")
    suspend fun getFolder(treeUri: String): FolderEntity?

    @Query("DELETE FROM folders WHERE treeUri = :treeUri")
    suspend fun deleteFolder(treeUri: String)

    @Upsert
    suspend fun upsertStation(station: StationEntity): Long

    @Query("SELECT * FROM stations ORDER BY name COLLATE NOCASE ASC")
    fun observeStations(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations ORDER BY name COLLATE NOCASE ASC")
    suspend fun getStations(): List<StationEntity>

    @Query("SELECT * FROM stations WHERE id = :id LIMIT 1")
    suspend fun getStation(id: Long): StationEntity?

    @Delete
    suspend fun deleteStation(station: StationEntity)

    @Query("DELETE FROM stations WHERE id = :id")
    suspend fun deleteStationById(id: Long)

    @Delete
    suspend fun deleteTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()
}
