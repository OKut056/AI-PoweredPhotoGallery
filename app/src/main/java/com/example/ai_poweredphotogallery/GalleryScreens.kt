package com.example.ai_poweredphotogallery

import androidx.activity.compose.BackHandler
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ai_poweredphotogallery.ui.theme.AIPoweredPhotoGalleryTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
@Composable
fun AppBottomBar(selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    NavigationBar(containerColor = Color(0xFFFAFAFA), tonalElevation = 0.dp) {
        AppDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, fontFamily = FontFamily.Cursive, fontSize = 18.sp) },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted,
                )
            )
        }
    }
}

@Composable
fun PhotosScreen(
    photos: List<PhotoItem> = emptyList(),
    onOpenSettings: () -> Unit = {},
    onSearch: () -> Unit = {},
    onImportFolder: () -> Unit = {},
    onImportFiles: () -> Unit = {},
    onSelectionActiveChange: (Boolean) -> Unit = {},
    onOpenPhoto: (Long) -> Unit = {},
    albums: List<AlbumItem> = emptyList(),
    onDeletePhotos: (Set<Long>) -> Unit = {},
    onMovePhotos: (Set<Long>, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var density by rememberSaveable { mutableStateOf(PhotoDensity.Normal) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var pendingDeleteIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var pendingMoveIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var draggingFastScroll by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val sections = remember(photos) { photoSections(photos) }
    val currentDate = currentDateFor(gridState, sections)

    LaunchedEffect(selecting) { onSelectionActiveChange(selecting) }
    BackHandler(enabled = selecting) {
        selecting = false
        selectedIds = emptySet()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(density.columns),
            state = gridState,
            contentPadding = PaddingValues(bottom = 92.dp),
            horizontalArrangement = Arrangement.spacedBy(if (density == PhotoDensity.Year) 1.dp else 3.dp),
            verticalArrangement = Arrangement.spacedBy(if (density == PhotoDensity.Year) 1.dp else 3.dp),
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    when {
                        zoom > 1.08f -> density = density.zoomIn()
                        zoom < 0.92f -> density = density.zoomOut()
                    }
                }
            }
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (selecting) {
                    SelectionModeHeader(
                        selectedCount = selectedIds.size,
                        onSelectAll = { selectedIds = photos.map { it.id }.toSet() },
                        onCancel = {
                            selecting = false
                            selectedIds = emptySet()
                        }
                    )
                } else {
                    PageHeader(
                        title = "\u7167\u7247",
                        actions = {
                            HeaderIcon(Icons.Default.Search, "\u641c\u7d22", onSearch)
                            HeaderIcon(Icons.Default.Check, "\u9009\u62e9") { selecting = true }
                            MoreMenu(onSettings = onOpenSettings)
                        }
                    )
                }
            }
            when {
                photos.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("\u8f6f\u4ef6\u5a92\u4f53\u5de5\u4f5c\u533a\u6682\u65e0\u5a92\u4f53", "\u5bfc\u5165\u6587\u4ef6\u5939", onImportFolder)
                }
                else -> sections.forEach { section ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (density == PhotoDensity.Year) YearHeader(section.title) else SectionHeader(section.title)
                    }
                    items(section.photos, key = { it.id }) { photo ->
                        PhotoTile(
                            photo = photo,
                            density = density,
                            selected = photo.id in selectedIds,
                            selectionMode = selecting,
                            onClick = { if (selecting) selectedIds = toggle(selectedIds, photo.id) else onOpenPhoto(photo.id) },
                            onLongClick = {
                                selecting = true
                                selectedIds = selectedIds + photo.id
                            }
                        )
                    }
                }
            }
        }

        if (gridState.isScrollInProgress || draggingFastScroll) DateBubble(currentDate, Modifier.align(Alignment.Center))
        val y = (maxHeight - 92.dp) * scrollProgress(gridState)
        FastScrollHandle(Modifier.align(Alignment.TopEnd).offset(y = y)) { draggingFastScroll = it }
        if (selecting && selectedIds.isNotEmpty()) SelectionActionBar(selectedIds.size, Modifier.align(Alignment.BottomCenter)) { action ->
            if (action == "\u5220\u9664") {
                pendingDeleteIds = selectedIds
            } else if (action == "\u79fb\u52a8") {
                pendingMoveIds = selectedIds
            } else {
                Toast.makeText(context, action + "\u529f\u80fd\u5360\u4f4d", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (pendingDeleteIds.isNotEmpty()) {
        DeleteConfirmDialog(
            count = pendingDeleteIds.size,
            onDismiss = { pendingDeleteIds = emptySet() },
            onConfirm = {
                onDeletePhotos(pendingDeleteIds)
                pendingDeleteIds = emptySet()
                selectedIds = emptySet()
                selecting = false
            }
        )
    }

    if (pendingMoveIds.isNotEmpty()) {
        MoveToAlbumDialog(
            albums = albums,
            onDismiss = { pendingMoveIds = emptySet() },
            onMove = { albumName ->
                onMovePhotos(pendingMoveIds, albumName)
                pendingMoveIds = emptySet()
                selectedIds = emptySet()
                selecting = false
            }
        )
    }
}

@Composable
fun AlbumsScreen(
    data: GalleryData = GalleryData(),
    onAlbumClick: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    onSearch: () -> Unit = {},
    onOpenCleanup: () -> Unit = {},
    onOpenDeleted: () -> Unit = {},
    onCreateAlbum: (String) -> Unit = {},
    onSelectionActiveChange: (Boolean) -> Unit = {},
    onOpenPhoto: (Long) -> Unit = {},
    onDeleteAlbums: (Set<String>) -> Unit = {},
) {
    val context = LocalContext.current
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedAlbums by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var pendingDeleteAlbums by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showCreateAlbum by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(selecting) { onSelectionActiveChange(selecting) }
    BackHandler(enabled = selecting) {
        selecting = false
        selectedAlbums = emptySet()
    }

    val selectedAlbumMediaCount = if (AllAlbumName in selectedAlbums) {
        data.albums.firstOrNull { it.name == AllAlbumName }?.count ?: data.photos.size
    } else {
        data.albums.filter { it.name in selectedAlbums }.sumOf { it.count }
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 92.dp)) {
            item {
                if (selecting) {
                    SelectionModeHeader(
                        selectedCount = selectedAlbums.size,
                        subtitle = "\u5171" + selectedAlbumMediaCount + "\u4e2a\u56fe\u7247/\u89c6\u9891",
                        onSelectAll = { selectedAlbums = data.albums.map { it.name }.toSet() },
                        onCancel = {
                            selecting = false
                            selectedAlbums = emptySet()
                        }
                    )
                } else {
                    PageHeader(
                        title = "\u76f8\u518c",
                        actions = {
                            HeaderIcon(Icons.Default.Search, "\u641c\u7d22", onSearch)
                            HeaderIcon(Icons.Default.Check, "\u9009\u62e9") { selecting = true }
                            HeaderIcon(Icons.Default.Add, "\u65b0\u5efa") { showCreateAlbum = true }
                            MoreMenu(onSettings = onOpenSettings)
                        }
                    )
                }
            }
            item {
                AlbumGrid(
                    data.albums.take(1),
                    onAlbumClick,
                    selecting,
                    selectedAlbums,
                    onToggle = { selectedAlbums = toggle(selectedAlbums, it) },
                    onLongClick = {
                        selecting = true
                        selectedAlbums = selectedAlbums + it
                    }
                )
            }
            item { SectionTitle("\u66f4\u591a\u76f8\u518c") }
            item {
                AlbumGrid(
                    data.albums.drop(1),
                    onAlbumClick,
                    selecting,
                    selectedAlbums,
                    onToggle = { selectedAlbums = toggle(selectedAlbums, it) },
                    onLongClick = {
                        selecting = true
                        selectedAlbums = selectedAlbums + it
                    }
                )
            }
            item { AlbumListEntry("\u6e05\u7406\u5efa\u8bae", data.photos.size.toString(), Color(0xFFE84D68), onOpenCleanup) }
            item { AlbumListEntry("\u6700\u8fd1\u5220\u9664", data.deletedPhotos.size.toString(), Color(0xFFB04CD9), onOpenDeleted) }
        }
        if (selecting && selectedAlbums.isNotEmpty()) SelectionActionBar(selectedAlbums.size, Modifier.align(Alignment.BottomCenter)) { action ->
            if (action == "\u5220\u9664") {
                pendingDeleteAlbums = selectedAlbums - AllAlbumName
            } else {
                Toast.makeText(context, action + "\u529f\u80fd\u5360\u4f4d", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (pendingDeleteAlbums.isNotEmpty()) {
        DeleteAlbumConfirmDialog(
            count = pendingDeleteAlbums.size,
            onDismiss = { pendingDeleteAlbums = emptySet() },
            onConfirm = {
                onDeleteAlbums(pendingDeleteAlbums)
                pendingDeleteAlbums = emptySet()
                selectedAlbums = emptySet()
                selecting = false
            }
        )
    }

    if (showCreateAlbum) {
        CreateAlbumDialog(
            onDismiss = { showCreateAlbum = false },
            onCreate = { name ->
                onCreateAlbum(name)
                showCreateAlbum = false
            }
        )
    }
}

@Composable
fun AlbumDetailScreen(
    albumName: String,
    photos: List<PhotoItem> = emptyList(),
    onBack: () -> Unit,
    onSortClick: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onSelectionActiveChange: (Boolean) -> Unit = {},
    onOpenPhoto: (Long) -> Unit = {},
    albums: List<AlbumItem> = emptyList(),
    onDeletePhotos: (Set<Long>) -> Unit = {},
    onMovePhotos: (Set<Long>, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var pendingDeleteIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var pendingMoveIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }

    LaunchedEffect(selecting) { onSelectionActiveChange(selecting) }
    BackHandler(enabled = selecting) {
        selecting = false
        selectedIds = emptySet()
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(bottom = 92.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (selecting) {
                    SelectionModeHeader(
                        selectedCount = selectedIds.size,
                        onSelectAll = { selectedIds = photos.map { it.id }.toSet() },
                        onCancel = {
                            selecting = false
                            selectedIds = emptySet()
                        }
                    )
                } else {
                    DetailHeader(albumName, onBack, onOpenSettings) { selecting = true }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("\u2193", fontSize = 28.sp, color = Ink)
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = onSortClick, contentPadding = PaddingValues(0.dp)) {
                        Text("\u6309\u62cd\u6444\u65f6\u95f4 \u65b0\u5230\u65e7 \u2304", color = Ink, fontFamily = FontFamily.Cursive, fontSize = 24.sp)
                    }
                }
            }
            if (photos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("\u8fd9\u4e2a\u76f8\u518c\u8fd8\u6ca1\u6709\u5a92\u4f53", null, {}) }
            } else {
                items(photos, key = { it.id }) { photo ->
                    PhotoTile(
                        photo,
                        PhotoDensity.Normal,
                        selected = photo.id in selectedIds,
                        selectionMode = selecting,
                        onClick = { if (selecting) selectedIds = toggle(selectedIds, photo.id) else onOpenPhoto(photo.id) },
                        onLongClick = {
                            selecting = true
                            selectedIds = selectedIds + photo.id
                        }
                    )
                }
            }
        }
        if (selecting && selectedIds.isNotEmpty()) SelectionActionBar(selectedIds.size, Modifier.align(Alignment.BottomCenter)) { action ->
            if (action == "\u5220\u9664") {
                pendingDeleteIds = selectedIds
            } else if (action == "\u79fb\u52a8") {
                pendingMoveIds = selectedIds
            } else {
                Toast.makeText(context, action + "\u529f\u80fd\u5360\u4f4d", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (pendingDeleteIds.isNotEmpty()) {
        DeleteConfirmDialog(
            count = pendingDeleteIds.size,
            onDismiss = { pendingDeleteIds = emptySet() },
            onConfirm = {
                onDeletePhotos(pendingDeleteIds)
                pendingDeleteIds = emptySet()
                selectedIds = emptySet()
                selecting = false
            }
        )
    }

    if (pendingMoveIds.isNotEmpty()) {
        MoveToAlbumDialog(
            albums = albums,
            onDismiss = { pendingMoveIds = emptySet() },
            onMove = { albumName ->
                onMovePhotos(pendingMoveIds, albumName)
                pendingMoveIds = emptySet()
                selectedIds = emptySet()
                selecting = false
            }
        )
    }
}

@Composable
fun RecentDeletedScreen(
    photos: List<PhotoItem>,
    onBack: () -> Unit,
    onRestorePhotos: (Set<Long>) -> Unit,
    onPermanentDelete: (Set<Long>) -> Unit,
) {
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var pendingPermanentDeleteIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }

    BackHandler(enabled = selecting) {
        selecting = false
        selectedIds = emptySet()
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(bottom = 92.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (selecting) {
                    SelectionModeHeader(
                        selectedCount = selectedIds.size,
                        onSelectAll = { selectedIds = photos.map { it.id }.toSet() },
                        onCancel = {
                            selecting = false
                            selectedIds = emptySet()
                        }
                    )
                } else {
                    DetailHeader("\u6700\u8fd1\u5220\u9664", onBack) { selecting = true }
                }
            }
            if (photos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("\u6682\u65e0\u6700\u8fd1\u5220\u9664\u7684\u5a92\u4f53", null, {}) }
            } else {
                items(photos, key = { it.id }) { photo ->
                    PhotoTile(
                        photo = photo,
                        density = PhotoDensity.Normal,
                        selected = photo.id in selectedIds,
                        selectionMode = selecting,
                        onClick = {
                            selecting = true
                            selectedIds = toggle(selectedIds, photo.id)
                        },
                        onLongClick = {
                            selecting = true
                            selectedIds = selectedIds + photo.id
                        }
                    )
                }
            }
        }
        if (selecting && selectedIds.isNotEmpty()) {
            RecentDeletedActionBar(
                count = selectedIds.size,
                modifier = Modifier.align(Alignment.BottomCenter),
                onRestore = {
                    onRestorePhotos(selectedIds)
                    selectedIds = emptySet()
                    selecting = false
                },
                onPermanentDelete = { pendingPermanentDeleteIds = selectedIds },
            )
        }
    }

    if (pendingPermanentDeleteIds.isNotEmpty()) {
        PermanentDeleteConfirmDialog(
            count = pendingPermanentDeleteIds.size,
            onDismiss = { pendingPermanentDeleteIds = emptySet() },
            onConfirm = {
                onPermanentDelete(pendingPermanentDeleteIds)
                pendingPermanentDeleteIds = emptySet()
                selectedIds = emptySet()
                selecting = false
            }
        )
    }
}
@Composable
fun PhotoViewerScreen(
    photos: List<PhotoItem>,
    photoId: Long,
    onBack: () -> Unit,
    onDeletePhoto: (Long) -> Unit = {},
) {
    var index by remember(photoId, photos) { mutableStateOf(photos.indexOfFirst { it.id == photoId }.coerceAtLeast(0)) }
    val photo = photos.getOrNull(index)
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(index) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Box(Modifier.fillMaxSize().background(if (controlsVisible) Color.White else Color.Black)) {
        if (photo == null) {
            Text("\u5a92\u4f53\u4e0d\u5b58\u5728", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else if (photo.isVideo) {
            VideoPlayer(photo, Modifier.fillMaxSize().background(Color.Black))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(index) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = {
                                if (scale > 1.01f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.5f
                                    controlsVisible = false
                                }
                            }
                        )
                    }
                    .pointerInput(index) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            if (nextScale == 1f) {
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                            scale = nextScale
                        }
                    }
            ) {
                Thumbnail(
                    photo = photo,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
                    target = 1800,
                    contentScale = ContentScale.Fit,
                )
            }
        }

        if (controlsVisible && photo?.isVideo == true) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 50.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de", tint = Color.White, modifier = Modifier.size(40.dp))
            }
        } else if (controlsVisible) {
            Surface(color = Color.White, shadowElevation = 0.dp, modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(146.dp)) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 20.dp, top = 50.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de", tint = Ink, modifier = Modifier.size(40.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(photo?.let { viewerDayTitle(it.dateMillis) }.orEmpty(), color = Ink, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                            Text(photo?.let { viewerTimeTitle(it.dateMillis) }.orEmpty(), color = Muted, fontFamily = FontFamily.Cursive, fontSize = 19.sp)
                        }
                        TextButton(onClick = {}) { Text("\u2661", color = Ink, fontSize = 43.sp) }
                        Spacer(Modifier.width(18.dp))
                        TextButton(onClick = {}) { Text("\u24d8", color = Ink, fontSize = 39.sp) }
                    }
                    HorizontalDivider(color = SoftLine)
                }
            }

            Surface(color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Column {
                    HorizontalDivider(color = SoftLine)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 110.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().height(82.dp),
                    ) {
                        itemsIndexed(photos) { thumbIndex, item ->
                            Box(
                                modifier = Modifier
                                    .width(46.dp)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (thumbIndex == index) Accent.copy(alpha = 0.35f) else Color(0xFFECECEC))
                                    .combinedClickable(onClick = { index = thumbIndex }, onLongClick = {})
                                    .padding(if (thumbIndex == index) 3.dp else 0.dp)
                            ) {
                                Thumbnail(item, Modifier.fillMaxSize(), target = 160, contentScale = ContentScale.Crop)
                                if (item.isVideo) VideoDuration(photo = item, modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp), compact = true)
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 34.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ViewerAction("\u2197", "\u5206\u4eab")
                        ViewerAction("\u270e", "\u7f16\u8f91")
                        ViewerAction("\u25a1", "\u5220\u9664") { photo?.id?.let { pendingDeleteId = it } }
                        ViewerAction("\u22ee", "\u66f4\u591a")
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        DeleteConfirmDialog(
            count = 1,
            onDismiss = { pendingDeleteId = null },
            onConfirm = {
                pendingDeleteId = null
                onDeletePhoto(id)
            }
        )
    }
}

@Composable
private fun ViewerAction(icon: String, label: String, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    TextButton(onClick = { onClick?.invoke() ?: Toast.makeText(context, label + "\u529f\u80fd\u5360\u4f4d", Toast.LENGTH_SHORT).show() }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, color = Ink, fontSize = 34.sp)
            Text(label, color = Ink, fontFamily = FontFamily.Cursive, fontSize = 20.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf("\u6309\u62cd\u6444\u65f6\u95f4") }
    var descending by rememberSaveable { mutableStateOf(true) }
    val options = listOf("\u6309\u62cd\u6444\u65f6\u95f4", "\u6309\u4fee\u6539\u65f6\u95f4", "\u6309\u521b\u5efa\u65f6\u95f4", "\u6309\u540d\u79f0", "\u6309\u5927\u5c0f")
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text("\u6392\u5e8f", Modifier.align(Alignment.Center), fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Ink)
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) { Icon(Icons.Default.Close, contentDescription = "\u5173\u95ed", tint = Ink) }
        }
        Spacer(Modifier.height(18.dp))
        options.forEach { option ->
            Row(Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == option, onClick = { selected = option }, colors = RadioButtonDefaults.colors(selectedColor = Accent))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(option, fontFamily = FontFamily.Cursive, fontSize = 25.sp, color = Ink)
                    if (selected == option && option == options.first()) Text(if (descending) "\u65b0\u5230\u65e7" else "\u65e7\u5230\u65b0", color = Muted, fontSize = 14.sp)
                }
                if (selected == option) {
                    Surface(color = Color(0xFFF4F4F4), shape = RoundedCornerShape(22.dp)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            TextButton(onClick = { descending = false }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("\u2191", color = if (descending) Muted else Ink, fontSize = 22.sp) }
                            TextButton(onClick = { descending = true }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("\u2193", color = if (descending) Ink else Muted, fontSize = 22.sp) }
                        }
                    }
                }
            }
            HorizontalDivider(color = SoftLine, modifier = Modifier.padding(start = 98.dp))
        }
        TextButton(
            onClick = { selected = options.first(); descending = true },
            modifier = Modifier.fillMaxWidth().height(72.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Accent),
        ) { Text("\u6062\u590d\u9ed8\u8ba4", fontFamily = FontFamily.Cursive, fontSize = 22.sp) }
    }
}

@Composable
fun SearchScreen(
    photos: List<PhotoItem> = emptyList(),
    onBack: () -> Unit = {},
    onOpenPhoto: (Long) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(photos, query) { searchAiIndex(photos, query) }
    Column(Modifier.fillMaxSize().background(Color.White).padding(horizontal = 28.dp, vertical = 58.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de", tint = Ink, modifier = Modifier.size(34.dp)) }
            Text("\u641c\u7d22", fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Ink)
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Muted) },
            placeholder = { Text("\u641c\u7d22\u6587\u4ef6\u540d\u3001\u76f8\u518c\u3001\u56fe\u7247\u6216\u89c6\u9891") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Text("\u672c\u5730\u7d22\u5f15 " + photos.size + " \u4e2a\u5a92\u4f53\uff0c\u547d\u4e2d " + results.size + " \u4e2a", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(18.dp))
        when {
            photos.isEmpty() -> EmptyState("\u8f6f\u4ef6\u5a92\u4f53\u5de5\u4f5c\u533a\u6682\u65e0\u53ef\u641c\u7d22\u5a92\u4f53", null, {})
            results.isEmpty() -> EmptyState("\u6ca1\u6709\u5339\u914d\u7684\u5a92\u4f53", null, {})
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(results, key = { it.id }) { photo ->
                    PhotoTile(photo, PhotoDensity.Normal, onClick = { onOpenPhoto(photo.id) })
                }
            }
        }
    }
}

@Composable
fun AiScreen() {
    Box(Modifier.fillMaxSize().background(Color.White))
}

@Composable
fun PlaceholderScreen(title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 22.dp, top = 66.dp, bottom = 42.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de", tint = Ink, modifier = Modifier.size(34.dp)) }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Ink)
        }
    }
}

@Composable
fun SettingsScreen(
    scanSource: String,
    photoCount: Int,
    albumCount: Int,
    deletedCount: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onImportFolder: () -> Unit = {},
    onImportFiles: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 22.dp, top = 66.dp, bottom = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de", tint = Ink, modifier = Modifier.size(34.dp)) }
            Text("\u8bbe\u7f6e", fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Ink)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SettingsRow("\u626b\u63cf\u6765\u6e90", scanSource)
            SettingsRow("\u5df2\u626b\u63cf", "\u5a92\u4f53 $photoCount \u4e2a\u00a0\u00a0\u76f8\u518c $albumCount \u4e2a\u00a0\u00a0\u6700\u8fd1\u5220\u9664 $deletedCount \u4e2a")
            SettingsRow("\u6743\u9650", "\u4e0d\u8bfb\u53d6\u7cfb\u7edf\u76f8\u518c\uff0c\u4ec5\u7ba1\u7406\u8f6f\u4ef6\u76ee\u5f55")
            TextButton(
                onClick = onImportFolder,
                colors = ButtonDefaults.textButtonColors(contentColor = Accent),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) { Text("\u5bfc\u5165\u6587\u4ef6\u5939", fontSize = 20.sp) }
            TextButton(
                onClick = onImportFiles,
                colors = ButtonDefaults.textButtonColors(contentColor = Accent),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) { Text("\u5bfc\u5165\u56fe\u7247 / \u89c6\u9891", fontSize = 20.sp) }
            TextButton(
                onClick = onRefresh,
                colors = ButtonDefaults.textButtonColors(contentColor = Accent),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) { Text("\u5237\u65b0 / \u91cd\u65b0\u626b\u63cf", fontSize = 20.sp) }
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Ink, fontSize = 18.sp, lineHeight = 24.sp)
    }
}


@Composable
private fun SelectionModeHeader(selectedCount: Int, subtitle: String? = null, onSelectAll: () -> Unit, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 22.dp, top = 66.dp, bottom = 42.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onSelectAll) { Text("\u5168\u9009", color = Ink, fontSize = 18.sp) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\u5df2\u9009\u62e9" + selectedCount + "\u9879", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            subtitle?.let { Text(it, color = Muted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        TextButton(onClick = onCancel) { Text("\u53d6\u6d88", color = Ink, fontSize = 18.sp) }
    }
}

@Composable
private fun PageHeader(title: String, actions: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 22.dp, top = 66.dp, bottom = 46.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, fontSize = 42.sp, color = Color.Black)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), content = actions)
    }
}

@Composable
private fun DetailHeader(albumName: String, onBack: () -> Unit, onOpenSettings: () -> Unit = {}, onSelect: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 22.dp, top = 66.dp, bottom = 42.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de", tint = Ink, modifier = Modifier.size(34.dp)) }
        Text(albumName, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        HeaderIcon(Icons.Default.Check, "\u9009\u62e9", onSelect)
        MoreMenu(onSettings = onOpenSettings)
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, description: String, onClick: () -> Unit = {}) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = description, tint = Ink, modifier = Modifier.size(35.dp)) }
}

@Composable
private fun MoreMenu(onSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        HeaderIcon(Icons.Default.MoreVert, "\u66f4\u591a") { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("\u8bbe\u7f6e") }, onClick = { expanded = false; onSettings() })
        }
    }
}

@Composable
private fun CreateAlbumDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u65b0\u5efa\u76f8\u518c") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("\u76f8\u518c\u540d\u79f0") }) },
        confirmButton = { TextButton(onClick = { name.trim().takeIf { it.isNotEmpty() }?.let(onCreate) }) { Text("\u521b\u5efa") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") } }
    )
}

@Composable
private fun EmptyState(text: String, button: String?, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 80.dp)) {
        Text(text, color = Muted, fontSize = 16.sp)
        if (button != null) {
            Spacer(Modifier.height(18.dp))
            TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = Accent)) { Text(button) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, modifier = Modifier.padding(start = 28.dp, top = 8.dp, bottom = 20.dp), fontFamily = FontFamily.Cursive, fontSize = 27.sp, color = Ink)
}

@Composable
private fun YearHeader(text: String) {
    Surface(color = Color.White.copy(alpha = 0.92f), shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(start = 66.dp, top = 4.dp, bottom = 8.dp)) {
        Text(text.take(5), modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp), fontFamily = FontFamily.Cursive, fontSize = 22.sp, color = Ink)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, modifier = Modifier.padding(start = 28.dp, top = 26.dp, bottom = 18.dp), fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Ink)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    photo: PhotoItem,
    density: PhotoDensity,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val shape = if (density == PhotoDensity.Year) RoundedCornerShape(0.dp) else RoundedCornerShape(1.dp)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (photo.uri == null) Modifier.background(photoBrush(photo.color)) else Modifier.background(Color(0xFFEDEDED)))
    ) {
        Thumbnail(photo, Modifier.fillMaxSize())
        if (photo.isVideo && density != PhotoDensity.Year) {
            VideoDuration(photo = photo, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        if (density != PhotoDensity.Year && photo.label.isNotBlank()) {
            Text(photo.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
        }
        if (selected) SelectionMark()
    }
}

@Composable
private fun VideoPlayer(photo: PhotoItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { GalleryVideoView(it) { Toast.makeText(context, "\u89c6\u9891\u65e0\u6cd5\u64ad\u653e", Toast.LENGTH_SHORT).show() } },
        update = { view -> photo.uri?.let(view::play) }
    )
}

private class GalleryVideoView(
    context: Context,
    private val onError: () -> Unit,
) : FrameLayout(context), TextureView.SurfaceTextureListener {
    private val handler = Handler(Looper.getMainLooper())
    private val textureView = TextureView(context)
    private val centerPlay = TextView(context)
    private val seekBar = SeekBar(context)
    private val bottomPlay = TextView(context)
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var pendingUri: android.net.Uri? = null
    private var prepared = false
    private var completed = false
    private var userSeeking = false
    private var videoWidth = 0
    private var videoHeight = 0

    private val progressTick = object : Runnable {
        override fun run() {
            val current = player?.currentPosition ?: 0
            if (!userSeeking) seekBar.progress = current.coerceAtMost(seekBar.max)
            handler.postDelayed(this, 250L)
        }
    }

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        textureView.surfaceTextureListener = this
        addView(textureView, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))

        centerPlay.text = "\u25b6"
        centerPlay.textSize = 44f
        centerPlay.gravity = Gravity.CENTER
        centerPlay.setTextColor(android.graphics.Color.WHITE)
        centerPlay.setOnClickListener { togglePlayback() }
        addView(centerPlay, FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER))

        seekBar.max = 1
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && prepared) player?.seekTo(progress)
            }
            override fun onStartTrackingTouch(bar: SeekBar) { userSeeking = true }
            override fun onStopTrackingTouch(bar: SeekBar) {
                userSeeking = false
                if (prepared) player?.seekTo(bar.progress)
            }
        })
        addView(seekBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.BOTTOM).apply {
            leftMargin = 22
            rightMargin = 22
            bottomMargin = 82
        })

        bottomPlay.text = "\u25b6"
        bottomPlay.textSize = 30f
        bottomPlay.gravity = Gravity.CENTER
        bottomPlay.setTextColor(android.graphics.Color.WHITE)
        bottomPlay.setOnClickListener { togglePlayback() }
        addView(bottomPlay, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(14) })
    }

    fun play(uri: android.net.Uri) {
        if (pendingUri == uri && player != null) return
        pendingUri = uri
        if (surface != null) prepare(uri)
    }

    private fun prepare(uri: android.net.Uri) {
        releasePlayer()
        prepared = false
        completed = false
        seekBar.progress = 0
        centerPlay.visibility = android.view.View.VISIBLE
        bottomPlay.text = "\u25b6"
        val next = MediaPlayer()
        player = next
        try {
            next.setSurface(surface)
            val path = uri.path.orEmpty()
            if (uri.scheme == "file" && path.isNotBlank()) next.setDataSource(path) else next.setDataSource(context, uri)
            next.setOnVideoSizeChangedListener { _, width, height -> updateVideoSize(width, height) }
            next.setOnPreparedListener { mediaPlayer ->
                prepared = true
                seekBar.max = mediaPlayer.duration.coerceAtLeast(1)
                updateVideoSize(mediaPlayer.videoWidth, mediaPlayer.videoHeight)
                mediaPlayer.seekTo(1)
                updateControls()
                handler.removeCallbacks(progressTick)
                handler.post(progressTick)
            }
            next.setOnCompletionListener {
                completed = true
                seekBar.progress = seekBar.max
                updateControls()
            }
            next.setOnErrorListener { _, _, _ -> onError(); true }
            next.prepareAsync()
        } catch (_: Exception) {
            releasePlayer()
            onError()
        }
    }

    private fun togglePlayback() {
        val current = player ?: return
        if (!prepared) return
        if (current.isPlaying) {
            current.pause()
        } else {
            if (completed) {
                completed = false
                current.seekTo(0)
            }
            current.start()
        }
        updateControls()
    }

    private fun updateControls() {
        val playing = player?.isPlaying == true
        centerPlay.visibility = if (playing) android.view.View.GONE else android.view.View.VISIBLE
        bottomPlay.text = if (playing) "\u23f8" else "\u25b6"
    }

    private fun updateVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        videoWidth = width
        videoHeight = height
        fitTexture()
    }

    private fun fitTexture() {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
        val parentRatio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (videoRatio > parentRatio) {
            targetWidth = width
            targetHeight = (width / videoRatio).toInt()
        } else {
            targetHeight = height
            targetWidth = (height * videoRatio).toInt()
        }
        textureView.layoutParams = FrameLayout.LayoutParams(targetWidth, targetHeight, Gravity.CENTER)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        fitTexture()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(texture)
        pendingUri?.let(::prepare)
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        releasePlayer()
        surface?.release()
        surface = null
        return true
    }

    override fun onDetachedFromWindow() {
        releasePlayer()
        surface?.release()
        surface = null
        super.onDetachedFromWindow()
    }

    private fun releasePlayer() {
        handler.removeCallbacks(progressTick)
        runCatching { player?.stop() }
        player?.release()
        player = null
        prepared = false
    }
}

@Composable
private fun VideoDuration(photo: PhotoItem, modifier: Modifier = Modifier, compact: Boolean = false) {
    Text(
        text = formatVideoDuration(photo.durationMillis),
        color = Color.White,
        fontSize = if (compact) 10.sp else 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
    )
}
@Composable
private fun Thumbnail(
    photo: PhotoItem,
    modifier: Modifier = Modifier,
    target: Int = 320,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    var bitmap by remember(photo.uri, target, photo.isVideo) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photo.uri, target, photo.isVideo) { bitmap = photo.uri?.let { uri -> withContext(Dispatchers.IO) { loadBitmap(context, uri, target, photo.isVideo) } } }
    bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = photo.name, contentScale = contentScale, modifier = modifier) }
}

@Composable
private fun AlbumGrid(
    albums: List<AlbumItem>,
    onAlbumClick: (String) -> Unit,
    selectionMode: Boolean = false,
    selectedAlbums: Set<String> = emptySet(),
    onToggle: (String) -> Unit = {},
    onLongClick: (String) -> Unit = {},
) {
    if (albums.isEmpty()) {
        EmptyState("\u6682\u65e0\u76f8\u518c", null, {})
        return
    }
    Column(Modifier.padding(horizontal = 28.dp)) {
        albums.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                row.forEach { album ->
                    AlbumCard(album, onAlbumClick, selectionMode, album.name in selectedAlbums, onToggle, onLongClick, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: AlbumItem,
    onAlbumClick: (String) -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggle: (String) -> Unit = {},
    onLongClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFF3F3F3))
                .combinedClickable(
                    onClick = { if (selectionMode) onToggle(album.name) else onAlbumClick(album.name) },
                    onLongClick = { onLongClick(album.name) }
                )
        ) {
            when {
                album.cover != null -> Thumbnail(album.cover, Modifier.fillMaxSize())
                album.colors.size >= 4 -> FourColorCover(album.colors)
                album.colors.isNotEmpty() -> Box(Modifier.fillMaxSize().background(photoBrush(album.colors.first())))
                else -> Box(Modifier.fillMaxSize().background(Color(0xFFF1F1F1)))
            }
            if (selected) SelectionMark()
        }
        Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp), fontFamily = FontFamily.Cursive, fontSize = 24.sp, color = Ink)
        Text(album.count.toString(), color = Muted, fontSize = 15.sp)
    }
}

@Composable
private fun FourColorCover(colors: List<Color>) {
    Column(Modifier.padding(2.dp)) {
        repeat(2) { row ->
            Row(Modifier.weight(1f)) {
                repeat(2) { col ->
                    Box(Modifier.weight(1f).padding(1.dp).clip(RoundedCornerShape(8.dp)).background(photoBrush(colors[row * 2 + col])))
                }
            }
        }
    }
}

@Composable
private fun MoveToAlbumDialog(albums: List<AlbumItem>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    val targets = albums.filter { it.name != AllAlbumName }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u79fb\u52a8\u5230\u76f8\u518c") },
        text = {
            Column {
                if (targets.isEmpty()) {
                    Text("\u8bf7\u5148\u521b\u5efa\u76f8\u518c")
                } else {
                    targets.forEach { album ->
                        TextButton(onClick = { onMove(album.name) }, modifier = Modifier.fillMaxWidth()) {
                            Text(album.name, color = Ink)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") } }
    )
}

@Composable
private fun PermanentDeleteConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u6c38\u4e45\u5220\u9664\uff1f") },
        text = { Text("\u5c06 " + count + " \u4e2a\u5a92\u4f53\u4ece\u8f6f\u4ef6\u6700\u8fd1\u5220\u9664\u4e2d\u6c38\u4e45\u5220\u9664\u3002") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("\u6c38\u4e45\u5220\u9664", color = Color(0xFFE53935)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") } }
    )
}

@Composable
private fun DeleteAlbumConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u5220\u9664\u76f8\u518c\u6587\u4ef6\u5939\uff1f") },
        text = { Text("\u5c06 " + count + " \u4e2a\u76f8\u518c\u6587\u4ef6\u5939\u91cc\u7684\u5a92\u4f53\u79fb\u5230\u6700\u8fd1\u5220\u9664\uff0c\u7a7a\u6587\u4ef6\u5939\u4f1a\u5c1d\u8bd5\u79fb\u9664\u3002") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("\u5220\u9664", color = Color(0xFFE53935)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") } }
    )
}

@Composable
private fun DeleteConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u79fb\u5230\u6700\u8fd1\u5220\u9664\uff1f") },
        text = { Text("\u5c06 " + count + " \u4e2a\u5a92\u4f53\u79fb\u5230\u6700\u8fd1\u5220\u9664\uff0c\u53ef\u4ee5\u4ece\u6700\u8fd1\u5220\u9664\u4e2d\u6062\u590d\u3002") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("\u5220\u9664", color = Color(0xFFE53935)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") } }
    )
}

@Composable
private fun SelectionMark() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f)))
    Surface(color = Accent, shape = CircleShape, modifier = Modifier.padding(6.dp).size(24.dp)) {
        Icon(Icons.Default.Check, contentDescription = "\u5df2\u9009\u62e9", tint = Color.White, modifier = Modifier.padding(4.dp))
    }
}

@Composable
private fun SelectionActionBar(count: Int, modifier: Modifier = Modifier, onAction: (String) -> Unit) {
    Surface(color = Color.White, shadowElevation = 8.dp, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.height(72.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("\u5df2\u9009 " + count, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { onAction("\u5206\u4eab") }) { Text("\u5206\u4eab", color = Ink) }
            TextButton(onClick = { onAction("\u79fb\u52a8") }) { Text("\u79fb\u52a8", color = Ink) }
            TextButton(onClick = { onAction("\u5220\u9664") }) { Text("\u5220\u9664", color = Ink) }
            TextButton(onClick = { onAction("\u66f4\u591a") }) { Text("\u66f4\u591a", color = Ink) }
        }
    }
}

@Composable
private fun RecentDeletedActionBar(count: Int, modifier: Modifier = Modifier, onRestore: () -> Unit, onPermanentDelete: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 8.dp, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.height(72.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("\u5df2\u9009 " + count, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onRestore) { Text("\u6062\u590d", color = Ink) }
            TextButton(onClick = onPermanentDelete) { Text("\u6c38\u4e45\u5220\u9664", color = Color(0xFFE53935)) }
        }
    }
}
@Composable
private fun AlbumListEntry(title: String, count: String, tint: Color, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(82.dp).combinedClickable(onClick = onClick).padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) { Text("\u25a3", color = tint, fontSize = 24.sp) }
        Spacer(Modifier.width(22.dp))
        Text(title, fontFamily = FontFamily.Cursive, fontSize = 28.sp, color = Ink, modifier = Modifier.weight(1f))
        Text(count, color = Muted, fontFamily = FontFamily.Cursive, fontSize = 22.sp)
        Text("  \u203a", color = Color(0xFFD0D0D0), fontSize = 34.sp)
    }
}

@Composable
private fun DateBubble(date: String, modifier: Modifier = Modifier) {
    Surface(color = Accent, shape = RoundedCornerShape(22.dp), modifier = modifier) {
        Text(date, modifier = Modifier.padding(horizontal = 42.dp, vertical = 24.dp), color = Color.White, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, fontSize = 28.sp)
    }
}

@Composable
private fun FastScrollHandle(modifier: Modifier = Modifier, onDragChange: (Boolean) -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
        shadowElevation = 2.dp,
        modifier = modifier.width(54.dp).height(92.dp).pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { onDragChange(true) },
                onDragEnd = { onDragChange(false) },
                onDragCancel = { onDragChange(false) },
                onVerticalDrag = { _, _ -> onDragChange(true) }
            )
        }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("\u2303", color = Color(0xFF666666), fontSize = 26.sp)
            Text("\u2304", color = Color(0xFF666666), fontSize = 26.sp)
        }
    }
}

private fun currentDateFor(state: LazyGridState, sections: List<PhotoSection>): String {
    val first = state.firstVisibleItemIndex
    var index = 1
    sections.forEach { section ->
        if (first <= index + section.photos.size) return section.title
        index += 1 + section.photos.size
    }
    return sections.lastOrNull()?.title ?: "\u65e0\u7167\u7247"
}

private fun scrollProgress(state: LazyGridState): Float {
    val total = state.layoutInfo.totalItemsCount
    val visible = state.layoutInfo.visibleItemsInfo.size
    return (state.firstVisibleItemIndex.toFloat() / (total - visible).coerceAtLeast(1)).coerceIn(0f, 1f)
}

private fun photoSections(photos: List<PhotoItem>): List<PhotoSection> =
    photos.groupBy { formatDate(it.dateMillis) }.map { (title, items) -> PhotoSection(title, items) }

private fun formatDate(millis: Long): String =
    SimpleDateFormat("yyyy\u5e74M\u6708d\u65e5", Locale.CHINA).format(Date(millis.takeIf { it > 0 } ?: System.currentTimeMillis()))

private fun viewerDayTitle(millis: Long): String {
    val date = Calendar.getInstance().apply { timeInMillis = millis.takeIf { it > 0 } ?: System.currentTimeMillis() }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(date, today) -> "\u4eca\u5929"
        sameDay(date, yesterday) -> "\u6628\u5929"
        else -> SimpleDateFormat("M\u6708d\u65e5", Locale.CHINA).format(Date(date.timeInMillis))
    }
}

private fun viewerTimeTitle(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis.takeIf { it > 0 } ?: System.currentTimeMillis()))

private fun formatVideoDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds) else "%02d:%02d".format(Locale.ROOT, minutes, seconds)
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun <T> toggle(values: Set<T>, value: T): Set<T> = if (value in values) values - value else values + value

private fun photoBrush(color: Color) = Brush.linearGradient(listOf(color.copy(alpha = 0.95f), Color.White.copy(alpha = 0.15f), color.copy(alpha = 0.72f)))
@Preview(showBackground = true)
@Composable
private fun PhotosPreview() { AIPoweredPhotoGalleryTheme { PhotosScreen() } }

@Preview(showBackground = true)
@Composable
private fun AlbumsPreview() { AIPoweredPhotoGalleryTheme { AlbumsScreen(onAlbumClick = {}) } }

@Preview(showBackground = true)
@Composable
private fun AlbumDetailPreview() { AIPoweredPhotoGalleryTheme { AlbumDetailScreen("\u6d4b\u8bd5\u76f8\u518c", photos = emptyList(), onBack = {}, onSortClick = {}) } }

@Preview(showBackground = true)
@Composable
private fun AiPreview() { AIPoweredPhotoGalleryTheme { AiScreen() } }
