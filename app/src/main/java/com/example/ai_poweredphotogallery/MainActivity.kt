package com.example.ai_poweredphotogallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.core.content.ContextCompat
import com.example.ai_poweredphotogallery.ui.theme.AIPoweredPhotoGalleryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AIPoweredPhotoGalleryTheme { AIPoweredPhotoGalleryApp() } }
    }
}

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
    var requestedFileManagementPermission by rememberSaveable { mutableStateOf(false) }
    var pendingRestoreToRootIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var data by remember { mutableStateOf(GalleryData()) }
    var hasSelection by rememberSaveable { mutableStateOf(false) }
    var hasPhotoPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPhotoPermission = granted
        refreshTick++
    }
    val fileManagementLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshTick++
    }

    LaunchedEffect(Unit) {
        ensureGalleryRoot()
        if (!hasPhotoPermission) permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        if (!Environment.isExternalStorageManager() && !requestedFileManagementPermission) {
            requestedFileManagementPermission = true
            val appSettings = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            runCatching { fileManagementLauncher.launch(appSettings) }
                .onFailure { fileManagementLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }
    LaunchedEffect(hasPhotoPermission, refreshTick) {
        data = if (hasPhotoPermission) withContext(Dispatchers.IO) { loadGalleryData(context) } else GalleryData()
    }
    LaunchedEffect(destination, openedAlbum, openedPage, viewerPhotoId) { hasSelection = false }

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
                    onDeletePhoto = { id ->
                        val moved = movePhotosToRecentDeleted(context, data.photos, setOf(id))
                        refreshTick++
                        viewerPhotoId = null
                        viewerAlbum = null
                        Toast.makeText(context, "\u5df2\u79fb\u5230\u6700\u8fd1\u5220\u9664\uff1a" + moved + " \u5f20", Toast.LENGTH_SHORT).show()
                    }
                )
                openedPage == "\u6700\u8fd1\u5220\u9664" -> RecentDeletedScreen(
                    photos = data.deletedPhotos,
                    onBack = { openedPage = null },
                    onRestorePhotos = { ids ->
                        val result = restoreDeletedPhotos(context, data.deletedPhotos, ids)
                        refreshTick++
                        if (result.needsRootConfirmation) {
                            pendingRestoreToRootIds = ids
                        } else {
                            Toast.makeText(context, "\u5df2\u6062\u590d " + result.restored + " \u5f20\u56fe\u7247", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPermanentDelete = {
                        Toast.makeText(context, "\u6c38\u4e45\u5220\u9664\u529f\u80fd\u5360\u4f4d", Toast.LENGTH_SHORT).show()
                    }
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
                    onDeletePhotos = { ids ->
                        val moved = movePhotosToRecentDeleted(context, data.photos, ids)
                        refreshTick++
                        Toast.makeText(context, "\u5df2\u79fb\u5230\u6700\u8fd1\u5220\u9664\uff1a" + moved + " \u5f20", Toast.LENGTH_SHORT).show()
                    },
                    onMovePhotos = { ids, albumName ->
                        val moved = movePhotosToAlbum(context, data.photos, ids, albumName)
                        refreshTick++
                        Toast.makeText(context, "\u5df2\u79fb\u52a8\u5230" + albumName + "\uff1a" + moved + " \u5f20", Toast.LENGTH_SHORT).show()
                    }
                )
                destination == AppDestination.Photos -> PhotosScreen(
                    photos = data.photos,
                    hasPhotoPermission = hasPhotoPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES) },
                    onOpenSettings = { openedPage = "\u8bbe\u7f6e" },
                    onSelectionActiveChange = { hasSelection = it },
                    onOpenPhoto = { id -> viewerAlbum = AllAlbumName; viewerPhotoId = id },
                    albums = data.albums,
                    onDeletePhotos = { ids ->
                        val moved = movePhotosToRecentDeleted(context, data.photos, ids)
                        refreshTick++
                        Toast.makeText(context, "\u5df2\u79fb\u5230\u6700\u8fd1\u5220\u9664\uff1a" + moved + " \u5f20", Toast.LENGTH_SHORT).show()
                    },
                    onMovePhotos = { ids, albumName ->
                        val moved = movePhotosToAlbum(context, data.photos, ids, albumName)
                        refreshTick++
                        Toast.makeText(context, "\u5df2\u79fb\u52a8\u5230" + albumName + "\uff1a" + moved + " \u5f20", Toast.LENGTH_SHORT).show()
                    }
                )
                destination == AppDestination.Albums -> AlbumsScreen(
                    data = data,
                    onAlbumClick = { openedAlbum = it },
                    onOpenSettings = { openedPage = "\u8bbe\u7f6e" },
                    onOpenCleanup = { openedPage = "\u6e05\u7406\u5efa\u8bae" },
                    onOpenDeleted = { openedPage = "\u6700\u8fd1\u5220\u9664" },
                    onSelectionActiveChange = { hasSelection = it },
                    onDeleteAlbums = { names ->
                        val moved = deleteAlbumsToRecentDeleted(context, data.photos, names)
                        refreshTick++
                        Toast.makeText(context, "\u5df2\u5220\u9664\u76f8\u518c\uff0c\u79fb\u5165\u6700\u8fd1\u5220\u9664\uff1a" + moved + " \u5f20", Toast.LENGTH_SHORT).show()
                    },
                    onCreateAlbum = { name ->
                        when (createAlbumFolder(name)) {
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

    if (pendingRestoreToRootIds.isNotEmpty()) {
        RestoreToRootConfirmDialog(
            count = pendingRestoreToRootIds.size,
            onDismiss = { pendingRestoreToRootIds = emptySet() },
            onConfirm = {
                val result = restoreDeletedPhotos(context, data.deletedPhotos, pendingRestoreToRootIds, restoreMissingAlbumsToRoot = true)
                refreshTick++
                pendingRestoreToRootIds = emptySet()
                Toast.makeText(context, "\u5df2\u6062\u590d " + result.restored + " \u5f20\u56fe\u7247", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
