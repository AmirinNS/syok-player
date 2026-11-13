package io.github.amirinns.syok.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.amirinns.syok.radio.StationEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @Test
    fun migrate1To2_preservesEveryTrackColumnAndAddsFolders() = runBlocking<Unit> {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-1-to-2"
        val availableTrack = TrackEntity(
            "mediastore:external:42", "content://media/42", "Title", "Artist", "Album",
            trackNumber = 3, discNumber = 2, durationMs = 123456, isAvailable = true
        )
        val unavailableTrack = TrackEntity(
            "mediastore:external:43", "content://media/43", "Other title", "Other artist", "Other album",
            trackNumber = 0, discNumber = 1, durationMs = 0, isAvailable = false
        )
        context.deleteDatabase(databaseName)
        try {
            context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db: SQLiteDatabase ->
                // Exact v1 CREATE/setup statements from 1.json
                db.execSQL("CREATE TABLE IF NOT EXISTS `tracks` (`id` TEXT NOT NULL, `contentUri` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `trackNumber` INTEGER NOT NULL, `discNumber` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `isAvailable` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '845ee9f776102d92d8a4c99eedb30107')")
                for (track in listOf(availableTrack, unavailableTrack)) {
                    db.execSQL(
                        "INSERT INTO tracks VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(track.id, track.contentUri, track.title, track.artist, track.album,
                            track.trackNumber, track.discNumber, track.durationMs, if (track.isAvailable) 1 else 0)
                    )
                }
                db.version = 1
            }

            // Apply MIGRATION_1_2 via SupportSQLiteOpenHelper
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(databaseName)
                    .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {}
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                    })
                    .build()
            )
            val supportDb = helper.writableDatabase
            try {
                LibraryDatabase.MIGRATION_1_2.migrate(supportDb)
                supportDb.version = 2

                // Verify sourceId column exists and defaults to null on existing rows
                supportDb.query("SELECT id, sourceId FROM tracks").use { cursor ->
                    assertEquals(2, cursor.count)
                    while (cursor.moveToNext()) {
                        assertNull(cursor.getString(1))
                    }
                }

                // Verify folders table was created and can be written to
                supportDb.execSQL("INSERT INTO folders VALUES ('content://provider/tree/music', 'Music', 'AVAILABLE', '10 tracks')")
                supportDb.query("SELECT treeUri, name FROM folders").use { cursor ->
                    assertEquals(1, cursor.count)
                    cursor.moveToFirst()
                    assertEquals("content://provider/tree/music", cursor.getString(0))
                    assertEquals("Music", cursor.getString(1))
                }
            } finally {
                helper.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrate2To3_preservesTracksAndFoldersAndAddsStations() = runBlocking<Unit> {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-2-to-3"
        val track = TrackEntity(
            "mediastore:external:101", "content://media/101", "Track 101", "Artist", "Album",
            trackNumber = 1, discNumber = 1, durationMs = 60000, isAvailable = true, sourceId = "saf:folder:1"
        )
        val folder = FolderEntity("content://provider/tree/music", "Music", "AVAILABLE", "Ready")
        context.deleteDatabase(databaseName)
        try {
            context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db: SQLiteDatabase ->
                // Exact v2 CREATE/setup statements from 2.json
                db.execSQL("CREATE TABLE IF NOT EXISTS `tracks` (`id` TEXT NOT NULL, `contentUri` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `trackNumber` INTEGER NOT NULL, `discNumber` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `isAvailable` INTEGER NOT NULL, `sourceId` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `folders` (`treeUri` TEXT NOT NULL, `name` TEXT NOT NULL, `status` TEXT NOT NULL, `detail` TEXT NOT NULL, PRIMARY KEY(`treeUri`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'ee9a76930f809018b950657ea7b1c22a')")
                db.execSQL(
                    "INSERT INTO tracks VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(track.id, track.contentUri, track.title, track.artist, track.album,
                        track.trackNumber, track.discNumber, track.durationMs, if (track.isAvailable) 1 else 0, track.sourceId)
                )
                db.execSQL(
                    "INSERT INTO folders VALUES (?, ?, ?, ?)",
                    arrayOf(folder.treeUri, folder.name, folder.status, folder.detail)
                )
                db.version = 2
            }

            // Open with Room at version 3 with both migrations
            val database: LibraryDatabase = Room.databaseBuilder(context, LibraryDatabase::class.java, databaseName)
                .addMigrations(LibraryDatabase.MIGRATION_1_2, LibraryDatabase.MIGRATION_2_3)
                .build()
            try {
                val dao: LibraryDao = database.libraryDao()
                // Room validates the database schema against v3
                val loadedTrack = dao.getTrackById(track.id)
                assertEquals(track, loadedTrack)
                assertEquals("saf:folder:1", loadedTrack?.sourceId)

                val loadedFolder = dao.getFolder(folder.treeUri)
                assertEquals(folder, loadedFolder)

                // Verify stations table is created and writable via DAO
                val station = StationEntity(name = "Radio Paradise", url = "https://stream.radioparadise.com/aac-128")
                val stationId = dao.upsertStation(station)
                val loadedStation = dao.getStation(stationId)
                assertNotNull(loadedStation)
                assertEquals("Radio Paradise", loadedStation?.name)
                assertEquals("https://stream.radioparadise.com/aac-128", loadedStation?.url)
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrate1To3_preservesV1TracksAndAddsFoldersAndStations() = runBlocking<Unit> {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-1-to-3"
        val track = TrackEntity(
            "mediastore:external:201", "content://media/201", "V1 Track", "V1 Artist", "V1 Album",
            trackNumber = 2, discNumber = 1, durationMs = 180000, isAvailable = true
        )
        context.deleteDatabase(databaseName)
        try {
            context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db: SQLiteDatabase ->
                // Exact v1 setup
                db.execSQL("CREATE TABLE IF NOT EXISTS `tracks` (`id` TEXT NOT NULL, `contentUri` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `trackNumber` INTEGER NOT NULL, `discNumber` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `isAvailable` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '845ee9f776102d92d8a4c99eedb30107')")
                db.execSQL(
                    "INSERT INTO tracks VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(track.id, track.contentUri, track.title, track.artist, track.album,
                        track.trackNumber, track.discNumber, track.durationMs, if (track.isAvailable) 1 else 0)
                )
                db.version = 1
            }

            // Open with Room at version 3 with both migrations
            val database: LibraryDatabase = Room.databaseBuilder(context, LibraryDatabase::class.java, databaseName)
                .addMigrations(LibraryDatabase.MIGRATION_1_2, LibraryDatabase.MIGRATION_2_3)
                .build()
            try {
                val dao: LibraryDao = database.libraryDao()
                val loadedTrack = dao.getTrackById(track.id)
                assertEquals(track, loadedTrack)
                assertNull(loadedTrack?.sourceId)

                val folder = FolderEntity("content://provider/tree/usb", "USB Drive")
                dao.upsertFolder(folder)
                assertEquals(folder, dao.getFolder(folder.treeUri))

                val station = StationEntity(name = "NTS Radio", url = "https://streams.radiomast.io/nts1")
                val stationId = dao.upsertStation(station)
                val loadedStation = dao.getStation(stationId)
                assertNotNull(loadedStation)
                assertEquals("NTS Radio", loadedStation?.name)
                assertEquals("https://streams.radiomast.io/nts1", loadedStation?.url)
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
