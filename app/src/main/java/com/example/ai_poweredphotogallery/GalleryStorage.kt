package com.example.ai_poweredphotogallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Size
import androidx.compose.ui.graphics.Color
import java.io.File
import java.util.Locale

private const val RecentDeletedFolderName = ".recent_deleted"
private const val RecentDeletedIndexName = "trash_index.tsv"
private const val MoveIndexName = "move_index.tsv"

fun galleryRoot(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), GalleryRootName)
fun ensureGalleryRoot(): Boolean = galleryRoot().let { it.exists() || it.mkdirs() }

fun createAlbumFolder(name: String): AlbumCreateResult {
    val safeName = safeAlbumName(name)
    if (safeName.isEmpty()) return AlbumCreateResult.Failed
    val folder = File(galleryRoot(), safeName)
    return when {
        folder.isDirectory -> AlbumCreateResult.AlreadyExists
        folder.exists() -> AlbumCreateResult.Failed
        folder.mkdirs() || folder.isDirectory -> AlbumCreateResult.Created
        else -> AlbumCreateResult.Failed
    }
}

fun loadGalleryData(context: Context): GalleryData {
    val root = galleryRoot()
    if (!root.exists()) root.mkdirs()
    val deletedIndex = readDeletedIndex(context)
    val deletedPaths = deletedIndex.flatMap { listOf(it.key, it.value) }.toSet()
    val photos = loadPhotos(root, deletedPaths, emptyMap())
    val deletedPhotos = loadDeletedPhotos(root, deletedIndex)
    val albumNames = root.listFiles()
        ?.filter { it.isDirectory && it.name != RecentDeletedFolderName }
        ?.map { it.name }
        .orEmpty()
        .distinct()
        .sortedBy { it.lowercase(Locale.ROOT) }
    val folderAlbums = albumNames.map { name ->
        val folderPhotos = photos.filter { it.albumName == name }
        AlbumItem(name, folderPhotos.size, folderPhotos.firstOrNull(), colorFallback(name))
    }
    return GalleryData(
        photos = photos,
        albums = listOf(AlbumItem(AllAlbumName, photos.size, photos.firstOrNull(), palette.take(4))) + folderAlbums,
        deletedPhotos = deletedPhotos,
    )
}

fun movePhotosToAlbum(context: Context, photos: List<PhotoItem>, selectedIds: Set<Long>, albumName: String): Int {
    val root = galleryRoot()
    val safeName = safeAlbumName(albumName)
    val targetDir = if (safeName.isEmpty() || albumName == AllAlbumName) root else File(root, safeName).apply { mkdirs() }
    val moveIndex = readMoveIndex(context).toMutableMap()
    var moved = 0

    photos.filter { it.id in selectedIds }.forEach { photo ->
        if (photo.albumName == safeName) return@forEach
        val source = File(photo.uri?.path.orEmpty()).takeIf { it.isFile } ?: return@forEach
        val sourcePath = photo.relativePath.ifBlank { relativePath(root, source) }
        val target = uniqueFile(File(targetDir, source.name))
        if (moveFile(source, target)) {
            moveIndex.remove(sourcePath)
            moved++
        }
    }

    if (moved > 0) writeMoveIndex(context, moveIndex)
    return moved
}

fun movePhotosToRecentDeleted(context: Context, photos: List<PhotoItem>, selectedIds: Set<Long>): Int =
    movePhotosToRecentDeleted(context, photos, selectedIds) { photo, root, source ->
        val albumName = safeAlbumName(photo.albumName)
        if (albumName.isNotEmpty()) "$albumName/${source.name}" else photo.relativePath.ifBlank { relativePath(root, source) }
    }

private fun movePhotosToRecentDeleted(
    context: Context,
    photos: List<PhotoItem>,
    selectedIds: Set<Long>,
    originalPathFor: (PhotoItem, File, File) -> String,
): Int {
    val root = galleryRoot()
    val trash = recentDeletedFolder(root).apply { mkdirs() }
    val index = readDeletedIndex(context).toMutableMap()
    val moveIndex = readMoveIndex(context).toMutableMap()
    var moved = 0

    photos.filter { it.id in selectedIds }.forEach { photo ->
        val source = File(photo.uri?.path.orEmpty()).takeIf { it.isFile } ?: return@forEach
        val physicalPath = photo.relativePath.ifBlank { relativePath(root, source) }
        val originalPath = originalPathFor(photo, root, source).ifBlank { physicalPath }
        val physicalTarget = uniqueFile(File(trash, source.name))
        if (moveFile(source, physicalTarget)) {
            val currentPath = relativePath(root, physicalTarget)
            index[currentPath] = originalPath
            moveIndex.remove(physicalPath)
            moveIndex.remove(originalPath)
            moved++
        }
    }

    if (moved > 0) {
        writeDeletedIndex(context, index)
        writeMoveIndex(context, moveIndex)
    }
    return moved
}

fun deleteAlbumsToRecentDeleted(context: Context, photos: List<PhotoItem>, albumNames: Set<String>): Int {
    val names = (albumNames - AllAlbumName).map { safeAlbumName(it) }.filter { it.isNotEmpty() }.toSet()
    if (names.isEmpty()) return 0
    val albumPhotos = photos.filter { safeAlbumName(it.albumName) in names }
    val moved = movePhotosToRecentDeleted(context, albumPhotos, albumPhotos.map { it.id }.toSet()) { photo, _, source ->
        val albumName = safeAlbumName(photo.albumName)
        if (albumName.isEmpty()) photo.relativePath else "$albumName/${source.name}"
    }
    val root = galleryRoot()
    names.forEach { name ->
        val folder = File(root, name)
        if (folder.isDirectory && folder.listFiles().isNullOrEmpty()) folder.delete()
    }
    return moved
}

fun restoreDeletedPhotos(context: Context, photos: List<PhotoItem>, selectedIds: Set<Long>, restoreMissingAlbumsToRoot: Boolean = false): RestoreResult {
    val root = galleryRoot()
    val index = readDeletedIndex(context).toMutableMap()
    var restored = 0
    var needsRootConfirmation = false

    photos.filter { it.id in selectedIds }.forEach { photo ->
        val source = File(photo.uri?.path.orEmpty()).takeIf { it.isFile } ?: return@forEach
        val currentPath = photo.relativePath.ifBlank { relativePath(root, source) }
        val originalPath = photo.originalRelativePath.ifBlank { index[currentPath].orEmpty() }.ifBlank { source.name }
        val missingAlbum = originalAlbumMissing(root, originalPath)
        if (missingAlbum && !restoreMissingAlbumsToRoot) {
            needsRootConfirmation = true
            return@forEach
        }
        val targetPath = if (missingAlbum) originalPath.substringAfterLast('/') else originalPath
        val restoredFile = if (currentPath != targetPath) {
            val target = uniqueFile(File(root, targetPath)).also { it.parentFile?.mkdirs() }
            moveFile(source, target)
        } else {
            true
        }
        if (restoredFile) {
            index.remove(currentPath)
            restored++
        }
    }

    if (restored > 0) writeDeletedIndex(context, index)
    return RestoreResult(restored, needsRootConfirmation)
}

private fun loadPhotos(root: File, deletedOriginalPaths: Set<String>, moveIndex: Map<String, String>): List<PhotoItem> =
    root.walkTopDown()
        .onEnter { it.name != RecentDeletedFolderName }
        .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in imageExtensions }
        .filter { relativePath(root, it) !in deletedOriginalPaths }
        .sortedByDescending { it.lastModified() }
        .map { file ->
            val path = relativePath(root, file)
            val parent = moveIndex[path] ?: file.parentFile?.takeIf { it != root }?.name.orEmpty()
            PhotoItem(
                id = file.absolutePath.hashCode().toLong(),
                uri = Uri.fromFile(file),
                dateMillis = file.lastModified(),
                name = file.name,
                albumName = parent,
                relativePath = path,
            )
        }
        .toList()

private fun loadDeletedPhotos(root: File, index: Map<String, String>): List<PhotoItem> =
    index.mapNotNull { (currentPath, originalPath) ->
        val file = File(root, currentPath).takeIf { it.isFile && it.extension.lowercase(Locale.ROOT) in imageExtensions } ?: return@mapNotNull null
        PhotoItem(
            id = file.absolutePath.hashCode().toLong(),
            uri = Uri.fromFile(file),
            dateMillis = file.lastModified(),
            name = file.name,
            albumName = "\u6700\u8fd1\u5220\u9664",
            relativePath = currentPath,
            originalRelativePath = originalPath,
        )
    }.sortedByDescending { it.dateMillis }

fun photosForAlbum(albumName: String, photos: List<PhotoItem>): List<PhotoItem> =
    if (albumName == AllAlbumName) photos else photos.filter { it.albumName == albumName }

private fun recentDeletedFolder(root: File): File = File(root, RecentDeletedFolderName)
private fun deletedIndexFile(context: Context): File = File(File(context.filesDir, RecentDeletedFolderName).apply { mkdirs() }, RecentDeletedIndexName)
private fun moveIndexFile(context: Context): File = File(File(context.filesDir, RecentDeletedFolderName).apply { mkdirs() }, MoveIndexName)

private fun readDeletedIndex(context: Context): Map<String, String> = runCatching {
    val file = deletedIndexFile(context)
    if (!file.isFile) return emptyMap()
    file.readLines().mapNotNull { line ->
        val tab = line.indexOf('\t')
        if (tab <= 0) null else line.take(tab) to line.drop(tab + 1)
    }.toMap()
}.getOrDefault(emptyMap())

private fun writeDeletedIndex(context: Context, index: Map<String, String>) {
    runCatching {
        val file = deletedIndexFile(context)
        file.writeText(index.entries.joinToString("\n") { it.key + "\t" + it.value })
    }
}
private fun readMoveIndex(context: Context): Map<String, String> = runCatching {
    val file = moveIndexFile(context)
    if (!file.isFile) return emptyMap()
    file.readLines().mapNotNull { line ->
        val tab = line.indexOf('\t')
        if (tab <= 0) null else line.take(tab) to line.drop(tab + 1)
    }.toMap()
}.getOrDefault(emptyMap())

private fun writeMoveIndex(context: Context, index: Map<String, String>) {
    runCatching {
        val file = moveIndexFile(context)
        file.writeText(index.entries.joinToString("\n") { it.key + "\t" + it.value })
    }
}

private fun moveFile(source: File, target: File): Boolean {
    if (source.renameTo(target)) return true
    return runCatching {
        source.copyTo(target, overwrite = false)
        if (source.delete()) true else target.delete().let { false }
    }.getOrDefault(false)
}

private fun uniqueFile(target: File): File {
    if (!target.exists()) return target
    val parent = target.parentFile ?: return target
    val base = target.nameWithoutExtension
    val ext = target.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
    return generateSequence(1) { it + 1 }
        .map { File(parent, "$base ($it)$ext") }
        .first { !it.exists() }
}

private fun originalAlbumMissing(root: File, originalPath: String): Boolean {
    val folder = originalPath.substringBeforeLast('/', missingDelimiterValue = "")
    return folder.isNotBlank() && !File(root, folder).isDirectory
}

private fun safeAlbumName(name: String): String = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")

private fun relativePath(root: File, file: File): String =
    root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')

private fun colorFallback(seed: String): List<Color> =
    if (seed.isEmpty()) palette.take(4) else List(4) { palette[(seed.hashCode() + it).mod(palette.size)] }

fun loadBitmap(context: Context, uri: Uri, target: Int): Bitmap? =
    if (uri.scheme == "file") decodeFileThumbnail(uri.path.orEmpty(), target) else context.contentResolver.loadThumbnail(uri, Size(target, target), null)

private fun decodeFileThumbnail(path: String, target: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sample = generateSequence(1) { it * 2 }.first { bounds.outWidth / it <= target * 2 && bounds.outHeight / it <= target * 2 }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")
