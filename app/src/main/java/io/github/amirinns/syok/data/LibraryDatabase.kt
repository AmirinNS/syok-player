package io.github.amirinns.syok.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TrackEntity::class], version = 1, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val DATABASE_NAME = "syok_library.db"

        fun buildDatabase(context: Context): LibraryDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                LibraryDatabase::class.java,
                DATABASE_NAME
            ).build()
        }
    }
}
