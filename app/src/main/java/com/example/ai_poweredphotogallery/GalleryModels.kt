package com.example.ai_poweredphotogallery

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

val Accent = Color(0xFFE7B900)
val SoftLine = Color(0xFFECECEC)
val Ink = Color(0xFF202020)
val Muted = Color(0xFF9A9A9A)
const val AllAlbumName = "\u5168\u90e8\u9879\u76ee"

enum class AppDestination(val label: String, val icon: ImageVector) {
    Photos("\u7167\u7247", Icons.Default.Home),
    Albums("\u76f8\u518c", Icons.Default.Favorite),
    Ai("AI", Icons.Default.AccountBox),
}

enum class PhotoDensity(val columns: Int) {
    Normal(4), Dense(7), Year(18);
    fun zoomIn() = when (this) { Normal -> Normal; Dense -> Normal; Year -> Dense }
    fun zoomOut() = when (this) { Normal -> Dense; Dense -> Year; Year -> Year }
}

enum class AlbumCreateResult { Created, AlreadyExists, Failed }
enum class MediaType { Image, Video }

data class GalleryData(
    val photos: List<PhotoItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val deletedPhotos: List<PhotoItem> = emptyList(),
)
data class PhotoItem(
    val id: String,
    val uri: Uri? = null,
    val dateMillis: Long = 0L,
    val name: String = "",
    val albumName: String = "",
    val color: Color = Color.LightGray,
    val label: String = "",
    val relativePath: String = "",
    val originalRelativePath: String = "",
    val type: MediaType = MediaType.Image,
    val durationMillis: Long = 0L,
)
val PhotoItem.isVideo: Boolean get() = type == MediaType.Video
data class PhotoSection(val title: String, val photos: List<PhotoItem>)
data class AlbumItem(val name: String, val count: Int, val cover: PhotoItem? = null, val colors: List<Color> = emptyList())
data class RestoreResult(val restored: Int)
data class ImportResult(val imported: Int, val skipped: Int = 0, val duplicateSkipped: Int = 0)
data class DeleteRequest(val localMoved: Int = 0, val skipped: Int = 0)
data class DeleteAlbumResult(val moved: Int, val removedFolders: Int)

val palette = listOf(
    Color(0xFFC45A8B), Color(0xFFE6C66D), Color(0xFF6F8FC9), Color(0xFF2F3845),
    Color(0xFFB74949), Color(0xFF9A9A9A), Color(0xFFF1A7B7), Color(0xFF52A7D8),
    Color(0xFF222222), Color(0xFF7FC68A), Color(0xFF466B8C), Color(0xFFE58E5E),
    Color(0xFFB6B6B6), Color(0xFFE08383), Color(0xFF7C72B8), Color(0xFF111111),
)
