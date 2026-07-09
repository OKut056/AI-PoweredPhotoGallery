package com.example.ai_poweredphotogallery

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.example.ai_poweredphotogallery.ui.theme.AIPoweredPhotoGalleryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AIPoweredPhotoGalleryTheme { AIPoweredPhotoGalleryApp() } }
    }
}

private fun scanSourceText(context: android.content.Context): String =
    "\u8f6f\u4ef6\u5a92\u4f53\u5de5\u4f5c\u533a\n" + galleryRoot(context).absolutePath

@PreviewScreenSizes
@Composable
fun AIPoweredPhotoGalleryApp() {
    val context = LocalContext.current
    var destination by rememberSaveable { mutableStateOf(AppDestination.Photos) }
    var openedAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var openedPage by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerPhotoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var refreshTick by rememberSaveable { mutableStateOf(0) }
    var data by remember { mutableStateOf(GalleryData()) }
    var hasSelection by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun showImportResult(result: ImportResult) {
        refreshTick++
        val message = if (result.skipped > 0) {
            "\u5df2\u5bfc\u5165 " + result.imported + " \u4e2a\uff0c\u8df3\u8fc7 " + result.skipped + " \u4e2a"
        } else {
            "\u5df2\u5bfc\u5165 " + result.imported + " \u4e2a"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val importFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            showImportResult(withContext(Dispatchers.IO) { importMediaFolder(context, uri) })
        }
    }
    val importFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            showImportResult(withContext(Dispatchers.IO) { importMediaFiles(context, uris) })
        }
    }

    fun deletePhotos(ids: Set<Long>, closeViewer: Boolean = false) {
        val request = prepareDeleteRequest(context, data.photos, ids)
        if (request.localMoved > 0) refreshTick++
        if (closeViewer) {
            viewerPhotoId = null
            viewerAlbum = null
        }
        val message = if (request.localMoved > 0) {
            "\u5df2\u79fb\u5230\u6700\u8fd1\u5220\u9664\uff1a" + request.localMoved + " \u4e2a\u5a92\u4f53"
        } else {
            "\u9009\u4e2d\u5a92\u4f53\u6682\u4e0d\u652f\u6301\u5220\u9664"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) { ensureGalleryRoot(context) }
    LaunchedEffect(refreshTick) {
        data = withContext(Dispatchers.IO) { loadGalleryData(context) }
    }
    LaunchedEffect(destination, openedAlbum, openedPage, viewerPhotoId) { hasSelection = false }

    BackHandler(enabled = viewerPhotoId != null || openedPage != null || openedAlbum != null) {
        when {
            viewerPhotoId != null -> {
                viewerPhotoId = null
                viewerAlbum = null
            }
            openedPage != null -> openedPage = null
            openedAlbum != null -> openedAlbum = null
        }
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = { if (openedAlbum == null && openedPage == null && viewerPhotoId == null && !hasSelection) AppBottomBar(destination) { destination = it } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                viewerPhotoId != null -> PhotoViewerScreen(
                    photos = photosForAlbum(viewerAlbum ?: AllAlbumName, data.photos),
                    photoId = viewerPhotoId ?: 0L,
                    onBack = { viewerPhotoId = null; viewerAlbum = null },
                    onDeletePhoto = { id -> deletePhotos(setOf(id), closeViewer = true) }
                )
                openedPage == "\u6700\u8fd1\u5220\u9664" -> RecentDeletedScreen(
                    photos = data.deletedPhotos,
                    onBack = { openedPage = null },
                    onRestorePhotos = { ids ->
                        val result = restoreDeletedPhotos(context, data.deletedPhotos, ids)
                        refreshTick++
                        Toast.makeText(context, "\u5df2\u6062\u590d " + result.restored + " \u4e2a\u5a92\u4f53", Toast.LENGTH_SHORT).show()
                    },
                    onPermanentDelete = { ids ->
                        val deleted = permanentlyDeletePhotos(context, data.deletedPhotos, ids)
                        refreshTick++
                        Toast.makeText(context, "\u5df2\u6c38\u4e45\u5220\u9664 " + deleted + " \u4e2a\u5a92\u4f53", Toast.LENGTH_SHORT).show()
                    }
                )
                openedPage == "\u641c\u7d22" -> SearchScreen(
                    photos = data.photos,
                    onBack = { openedPage = null },
                    onOpenPhoto = { id -> viewerAlbum = AllAlbumName; viewerPhotoId = id },
                )
                openedPage == "\u8bbe\u7f6e" -> SettingsScreen(
                    scanSource = scanSourceText(context),
                    photoCount = data.photos.size,
                    albumCount = (data.albums.size - 1).coerceAtLeast(0),
                    deletedCount = data.deletedPhotos.size,
                    onBack = { openedPage = null },
                    onRefresh = {
                        refreshTick++
                        Toast.makeText(context, "\u5df2\u91cd\u65b0\u626b\u63cf", Toast.LENGTH_SHORT).show()
                    },
                    onImportFolder = { importFolderLauncher.launch(null) },
                    onImportFiles = { importFilesLauncher.launch(arrayOf("image/*", "video/*")) }
                )
                openedPage != null -> PlaceholderScreen(openedPage.orEmpty(), onBack = { openedPage = null })
                openedAlbum != null -> AlbumDetailScreen(
                    albumName = openedAlbum.orEmpty(),
                    photos = photosForAlbum(openedAlbum.orEmpty(), data.photos),
                    onBack = { openedAlbum = null },
                    onSortClick = { showSortSheet = true },
                    onOpenSettings = { openedPage = "\u8bbe\u7f6e" },
                    onSelectionActiveChange = { hasSelection = it },
                    onOpenPhoto = { id -> viewerAlbum = openedAlbum.orEmpty(); viewerPhotoId = id },
                    albums = data.albums,
                    onDeletePhotos = { ids -> deletePhotos(ids) },
                    onMovePhotos = { ids, albumName ->
                        val moved = movePhotosToAlbum(context, data.photos, ids, albumName)
                        refreshTick++
                        Toast.makeText(context, "\u5df2\u79fb\u52a8\u5230" + albumName + "\uff1a" + moved + " \u4e2a\u5a92\u4f53", Toast.LENGTH_SHORT).show()
                    }
                )
                destination == AppDestination.Photos -> PhotosScreen(
                    photos = data.photos,
                    onOpenSettings = { openedPage = "\u8bbe\u7f6e" },
                    onSearch = { openedPage = "\u641c\u7d22" },
                    onImportFolder = { importFolderLauncher.launch(null) },
                    onImportFiles = { importFilesLauncher.launch(arrayOf("image/*", "video/*")) },
                    onSelectionActiveChange = { hasSelection = it },
                    onOpenPhoto = { id -> viewerAlbum = AllAlbumName; viewerPhotoId = id },
                    albums = data.albums,
                    onDeletePhotos = { ids -> deletePhotos(ids) },
                    onMovePhotos = { ids, albumName ->
                        val moved = movePhotosToAlbum(context, data.photos, ids, albumName)
                        refreshTick++
                        Toast.makeText(context, "\u5df2\u79fb\u52a8\u5230" + albumName + "\uff1a" + moved + " \u4e2a\u5a92\u4f53", Toast.LENGTH_SHORT).show()
                    }
                )
                destination == AppDestination.Albums -> AlbumsScreen(
                    data = data,
                    onAlbumClick = { openedAlbum = it },
                    onOpenSettings = { openedPage = "\u8bbe\u7f6e" },
                    onSearch = { openedPage = "\u641c\u7d22" },
                    onOpenCleanup = { openedPage = "\u6e05\u7406\u5efa\u8bae" },
                    onOpenDeleted = { openedPage = "\u6700\u8fd1\u5220\u9664" },
                    onSelectionActiveChange = { hasSelection = it },
                    onDeleteAlbums = { names ->
                        val photoIds = data.photos.filter { it.albumName in names }.map { it.id }.toSet()
                        if (photoIds.isNotEmpty()) deletePhotos(photoIds)
                        val removedFolders = deleteEmptyAlbumFolders(context, names)
                        if (removedFolders > 0) refreshTick++
                        if (photoIds.isEmpty()) {
                            val message = if (removedFolders > 0) "\u5df2\u5220\u9664\u7a7a\u76f8\u518c" else "\u9009\u4e2d\u76f8\u518c\u6682\u65e0\u53ef\u5220\u9664\u5a92\u4f53"
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCreateAlbum = { name ->
                        when (createAlbumFolder(context, name)) {
                            AlbumCreateResult.Created -> {
                                refreshTick++
                                Toast.makeText(context, "\u5df2\u521b\u5efa\u76f8\u518c\uff1a" + name, Toast.LENGTH_SHORT).show()
                            }
                            AlbumCreateResult.AlreadyExists -> Toast.makeText(context, "\u76f8\u518c\u5df2\u5b58\u5728\uff1a" + name, Toast.LENGTH_SHORT).show()
                            AlbumCreateResult.Failed -> Toast.makeText(context, "\u521b\u5efa\u5931\u8d25", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                destination == AppDestination.Ai -> AiScreen()
            }
        }
    }

    if (showSortSheet) SortSheet(onDismiss = { showSortSheet = false })
}
