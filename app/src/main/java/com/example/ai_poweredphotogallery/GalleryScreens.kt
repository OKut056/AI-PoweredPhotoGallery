package com.example.ai_poweredphotogallery

import android.graphics.Bitmap
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
import com.example.ai_poweredphotogallery.ui.theme.AIPoweredPhotoGalleryTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    hasPhotoPermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
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

    LaunchedEffect(selecting, selectedIds.size) {
        if (selecting && selectedIds.isEmpty()) selecting = false
        onSelectionActiveChange(selecting && selectedIds.isNotEmpty())
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
                PageHeader(
                    title = if (selecting) "\u5df2\u9009 " + selectedIds.size else "\u7167\u7247",
                    actions = {
                        HeaderIcon(Icons.Default.Search, "\u641c\u7d22")
                        HeaderIcon(Icons.Default.Check, if (selecting) "\u5b8c\u6210" else "\u9009\u62e9") {
                            selecting = !selecting
                            if (!selecting) selectedIds = emptySet()
                        }
                        MoreMenu(onSettings = onOpenSettings)
                    }
                )
            }
            when {
                !hasPhotoPermission -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("\u9700\u8981\u7167\u7247\u6743\u9650\u8bfb\u53d6 /sdcard/Pictures/" + GalleryRootName, "\u5141\u8bb8\u8bbf\u95ee\u7167\u7247", onRequestPermission)
                }
                photos.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("\u8bf7\u628a\u56fe\u7247\u63a8\u9001\u5230 /sdcard/Pictures/" + GalleryRootName, null, {})
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

    LaunchedEffect(selecting, selectedAlbums.size) {
        if (selecting && selectedAlbums.isEmpty()) selecting = false
        onSelectionActiveChange(selecting && selectedAlbums.isNotEmpty())
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 92.dp)) {
            item {
                PageHeader(
                    title = if (selecting) "\u5df2\u9009 " + selectedAlbums.size else "\u76f8\u518c",
                    actions = {
                        HeaderIcon(Icons.Default.Search, "\u641c\u7d22")
                        HeaderIcon(Icons.Default.Check, if (selecting) "\u5b8c\u6210" else "\u9009\u62e9") {
                            selecting = !selecting
                            if (!selecting) selectedAlbums = emptySet()
                        }
                        HeaderIcon(Icons.Default.Add, "\u65b0\u5efa") { showCreateAlbum = true }
                        MoreMenu(onSettings = onOpenSettings)
                    }
                )
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

    LaunchedEffect(selecting, selectedIds.size) {
        if (selecting && selectedIds.isEmpty()) selecting = false
        onSelectionActiveChange(selecting && selectedIds.isNotEmpty())
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(bottom = 92.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { DetailHeader(if (selecting) "\u5df2\u9009 " + selectedIds.size else albumName, onBack, onOpenSettings) }
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
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("\u8fd9\u4e2a\u76f8\u518c\u8fd8\u6ca1\u6709\u56fe\u7247", null, {}) }
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
    onPermanentDelete: () -> Unit,
) {
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }

    LaunchedEffect(selecting, selectedIds.size) {
        if (selecting && selectedIds.isEmpty()) selecting = false
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
                DetailHeader(if (selecting) "\u5df2\u9009 " + selectedIds.size else "\u6700\u8fd1\u5220\u9664", onBack)
            }
            if (photos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("\u6682\u65e0\u6700\u8fd1\u5220\u9664\u7684\u56fe\u7247", null, {}) }
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
                onPermanentDelete = onPermanentDelete,
            )
        }
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
            Text("\u56fe\u7247\u4e0d\u5b58\u5728", color = Color.White, modifier = Modifier.align(Alignment.Center))
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

        if (controlsVisible) {
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
fun AiScreen() {
    Column(Modifier.fillMaxSize().background(Color.White).padding(horizontal = 28.dp, vertical = 58.dp)) {
        Text("AI", fontWeight = FontWeight.Bold, fontSize = 40.sp, color = Ink)
        Spacer(Modifier.height(26.dp))
        Surface(color = Color(0xFFF7F7F7), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text("\u667a\u80fd\u52a9\u624b\u5360\u4f4d", fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Ink)
                Spacer(Modifier.height(8.dp))
                Text("\u540e\u7eed\u63a5\u5165\u804a\u5929\u3001\u641c\u56fe\u3001\u6539\u56fe\u3001\u751f\u56fe\u548c\u6279\u91cf\u6574\u7406\u6807\u7b7e\u3002", color = Color(0xFF666666), fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
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
private fun PageHeader(title: String, actions: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 22.dp, top = 66.dp, bottom = 46.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, fontSize = 42.sp, color = Color.Black)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), content = actions)
    }
}

@Composable
private fun DetailHeader(albumName: String, onBack: () -> Unit, onOpenSettings: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 22.dp, top = 66.dp, bottom = 42.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de", tint = Ink, modifier = Modifier.size(34.dp)) }
        Text(albumName, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        HeaderIcon(Icons.Default.Check, "\u9009\u62e9")
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
        if (density != PhotoDensity.Year && photo.label.isNotBlank()) {
            Text(photo.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
        }
        if (selected) SelectionMark()
    }
}

@Composable
private fun Thumbnail(
    photo: PhotoItem,
    modifier: Modifier = Modifier,
    target: Int = 320,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    var bitmap by remember(photo.uri, target) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photo.uri, target) { bitmap = photo.uri?.let { uri -> withContext(Dispatchers.IO) { loadBitmap(context, uri, target) } } }
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
private fun DeleteAlbumConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u5220\u9664\u76f8\u518c\u6587\u4ef6\u5939\uff1f") },
        text = { Text("\u5c06 " + count + " \u4e2a\u76f8\u518c\u6587\u4ef6\u5939\u91cc\u7684\u56fe\u7247\u79fb\u5230\u6700\u8fd1\u5220\u9664\uff0c\u7a7a\u6587\u4ef6\u5939\u4f1a\u5c1d\u8bd5\u79fb\u9664\u3002") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("\u5220\u9664", color = Color(0xFFE53935)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") } }
    )
}

@Composable
fun RestoreToRootConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u539f\u76f8\u518c\u4e0d\u5b58\u5728") },
        text = { Text("\u6709 " + count + " \u5f20\u56fe\u7247\u7684\u539f\u76f8\u518c\u5df2\u4e0d\u5b58\u5728\uff0c\u662f\u5426\u6062\u590d\u5230\u4e3b\u6587\u4ef6\u5939\uff1f") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("\u6062\u590d\u5230\u4e3b\u6587\u4ef6\u5939") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") } }
    )
}
@Composable
private fun DeleteConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u79fb\u5230\u6700\u8fd1\u5220\u9664\uff1f") },
        text = { Text("\u5c06 " + count + " \u5f20\u56fe\u7247\u79fb\u5230\u6700\u8fd1\u5220\u9664\uff0c\u53ef\u4ee5\u4ece\u6700\u8fd1\u5220\u9664\u4e2d\u6062\u590d\u3002") },
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
