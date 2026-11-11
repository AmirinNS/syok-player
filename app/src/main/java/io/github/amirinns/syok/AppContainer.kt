package io.github.amirinns.syok

import android.content.Context
import io.github.amirinns.syok.data.LibraryDao
import io.github.amirinns.syok.data.LibraryDatabase
import io.github.amirinns.syok.local.ArtworkLoader
import io.github.amirinns.syok.local.MediaStoreSource
import io.github.amirinns.syok.playback.BrowseTree
import io.github.amirinns.syok.playback.QueueStore

/**
 * Manual dependency container constructed once per application.
 */
class AppContainer(val context: Context) {
    val database: LibraryDatabase by lazy {
        LibraryDatabase.buildDatabase(context)
    }

    val libraryDao: LibraryDao by lazy {
        database.libraryDao()
    }

    val mediaStoreSource: MediaStoreSource by lazy {
        MediaStoreSource(context.contentResolver, libraryDao)
    }

    val artworkLoader: ArtworkLoader by lazy {
        ArtworkLoader(context)
    }

    val queueStore: QueueStore by lazy {
        QueueStore(context, libraryDao)
    }

    val browseTree: BrowseTree by lazy {
        BrowseTree(libraryDao)
    }
}
