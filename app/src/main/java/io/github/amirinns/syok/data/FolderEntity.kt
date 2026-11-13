package io.github.amirinns.syok.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val treeUri: String,
    val name: String,
    val status: String = UNAVAILABLE,
    val detail: String = "Retry indexing to find music."
) {
    companion object {
        const val AVAILABLE = "available"
        const val UNAVAILABLE = "unavailable"
        const val ACCESS_REVOKED = "access_revoked"
    }
}
