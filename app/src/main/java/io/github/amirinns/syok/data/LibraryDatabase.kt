package io.github.amirinns.syok.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.amirinns.syok.radio.StationEntity

@Database(entities = [TrackEntity::class, FolderEntity::class, StationEntity::class], version = 3, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val DATABASE_NAME = "syok_library.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN sourceId TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS folders (treeUri TEXT NOT NULL, name TEXT NOT NULL, status TEXT NOT NULL, detail TEXT NOT NULL, PRIMARY KEY(treeUri))")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS stations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, url TEXT NOT NULL)")
            }
        }

        fun buildDatabase(context: Context): LibraryDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                LibraryDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        }
    }
}
