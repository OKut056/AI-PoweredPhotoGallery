package com.example.ai_poweredphotogallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.LruCache
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.Color
import java.io.File
import java.io.FileInputStream
import java.util.Locale
private const val MediaRootName = "Media"
private const val RecentDeletedFolderName = ".recent_deleted"
private const val RecentDeletedIndexName = "trash_index.tsv"
private const val MoveIndexName = "move_index.tsv"

fun galleryRoot(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, MediaRootName)
fun ensureGalleryRoot(context: Context): Boolean = galleryRoot(context).let { it.exists() || it.mkdirs() }

fun createAlbumFolder(context: Context, name: String): AlbumCreateResult {
    val safeName = safeAlbumName(name)
    if (safeName.isEmpty()) return AlbumCreateResult.Failed
    val folder = File(galleryRoot(context), safeName)
    return when {
        folder.isDirectory -> AlbumCreateResult.AlreadyExists
        folder.exists() -> AlbumCreateResult.Failed
        folder.mkdirs() || folder.isDirectory -> AlbumCreateResult.Created
        else -> AlbumCreateResult.Failed
    }
}

fun importMediaFolder(context: Context, treeUri: Uri): ImportResult {
    val root = galleryRoot(context).apply { mkdirs() }
    val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
    // Import selected folder contents only; do not recreate the picked parent folder under Media.
    return importDocumentChildren(context, treeUri, treeDocumentId, root)
}

private fun importDocumentChildren(context: Context, treeUri: Uri, documentId: String, targetDir: File): ImportResult {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
    var imported = 0
    var skipped = 0

    context.contentResolver.query(
        childrenUri,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        while (cursor.moveToNext()) {
            val childId = cursor.getString(idColumn)
            val name = cursor.getString(nameColumn).orEmpty()
            val mimeType = cursor.getString(mimeColumn).orEmpty()
            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                val result = importDocumentChildren(context, treeUri, childId, File(targetDir, safeFileName(name)))
                imported += result.imported
                skipped += result.skipped
            } else if (isImportableMedia(mimeType, name)) {
                val target = uniqueFile(File(targetDir, safeFileName(name)))
                if (copyUriToFile(context, childUri, target)) imported++ else skipped++
            } else {
                skipped++
            }
        }
    } ?: skipped++

    return ImportResult(imported, skipped)
}

fun importMediaFiles(context: Context, uris: List<Uri>): ImportResult {
    val root = galleryRoot(context).apply { mkdirs() }
    var imported = 0
    var skipped = 0

    uris.forEach { uri ->
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val fileName = safeFileName(displayName(context, uri).ifBlank { fallbackFileName(mimeType) })
        if (!isImportableMedia(mimeType, fileName)) {
            skipped++
            return@forEach
        }
        val target = uniqueFile(File(root, fileName))
        if (copyUriToFile(context, uri, target)) imported++ else skipped++
    }

    return ImportResult(imported, skipped)
}
fun loadGalleryData(context: Context): GalleryData {
    val root = galleryRoot(context).apply { mkdirs() }
    val deletedIndex = readDeletedIndex(context)
    val deletedPaths = deletedIndex.flatMap { listOf(it.key, it.value) }.toSet()
    val photos = loadPhotos(root, deletedPaths, emptyMap())
    val deletedPhotos = loadDeletedPhotos(root, deletedIndex)
    val folderNames = root.listFiles()
        ?.filter { it.isDirectory && isVisibleAlbumName(it.name) }
        ?.map { it.name }
        .orEmpty()
    val albumNames = (photos.map { it.albumName }.filter { it.isNotBlank() && isVisibleAlbumName(it) } + folderNames)
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
    val root = galleryRoot(context)
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

fun prepareDeleteRequest(context: Context, photos: List<PhotoItem>, selectedIds: Set<Long>): DeleteRequest {
    val selected = photos.filter { it.id in selectedIds }
    val moved = movePhotosToRecentDeleted(context, selected, selectedIds)
    return DeleteRequest(localMoved = moved, skipped = selected.size - moved)
}

fun movePhotosToRecentDeleted(context: Context, photos: List<PhotoItem>, selectedIds: Set<Long>): Int =
    movePhotosToRecentDeleted(context, photos, selectedIds) { photo, root, source ->
        photo.relativePath.ifBlank { relativePath(root, source) }
    }

private fun movePhotosToRecentDeleted(
    context: Context,
    photos: List<PhotoItem>,
    selectedIds: Set<Long>,
    originalPathFor: (PhotoItem, File, File) -> String,
): Int {
    val root = galleryRoot(context)
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

fun deleteEmptyAlbumFolders(context: Context, albumNames: Set<String>): Int {
    val root = galleryRoot(context)
    return (albumNames - AllAlbumName)
        .map { safeAlbumName(it) }
        .filter { it.isNotEmpty() }
        .count { name -> deleteDirectoryIfEmpty(File(root, name)) }
}

fun deleteAlbumsToRecentDeleted(context: Context, photos: List<PhotoItem>, albumNames: Set<String>): Int {
    val names = (albumNames - AllAlbumName).map { safeAlbumName(it) }.filter { it.isNotEmpty() }.toSet()
    if (names.isEmpty()) return 0
    val albumPhotos = photos.filter { safeAlbumName(it.albumName) in names }
    val moved = movePhotosToRecentDeleted(context, albumPhotos, albumPhotos.map { it.id }.toSet()) { photo, root, source ->
        photo.relativePath.ifBlank { relativePath(root, source) }
    }
    val root = galleryRoot(context)
    names.forEach { name -> deleteDirectoryIfEmpty(File(root, name)) }
    return moved
}

fun restoreDeletedPhotos(context: Context, photos: List<PhotoItem>, selectedIds: Set<Long>): RestoreResult {
    val root = galleryRoot(context)
    val index = readDeletedIndex(context).toMutableMap()
    var restored = 0

    photos.filter { it.id in selectedIds }.forEach { photo ->
        val source = File(photo.uri?.path.orEmpty()).takeIf { it.isFile } ?: return@forEach
        val currentPath = photo.relativePath.ifBlank { relativePath(root, source) }
        val originalPath = photo.originalRelativePath.ifBlank { index[currentPath].orEmpty() }.ifBlank { source.name }
        val target = uniqueFile(File(root, originalPath)).also { it.parentFile?.mkdirs() }
        if (moveFile(source, target)) {
            index.remove(currentPath)
            restored++
        }
    }

    if (restored > 0) writeDeletedIndex(context, index)
    return RestoreResult(restored)
}

fun permanentlyDeletePhotos(context: Context, photos: List<PhotoItem>, selectedIds: Set<Long>): Int {
    val root = galleryRoot(context)
    val index = readDeletedIndex(context).toMutableMap()
    var deleted = 0

    photos.filter { it.id in selectedIds }.forEach { photo ->
        val source = File(photo.uri?.path.orEmpty()).takeIf { it.isFile } ?: return@forEach
        val currentPath = photo.relativePath.ifBlank { relativePath(root, source) }
        if (source.delete()) {
            index.remove(currentPath)
            deleted++
        }
    }

    if (deleted > 0) writeDeletedIndex(context, index)
    return deleted
}

private fun loadPhotos(root: File, deletedOriginalPaths: Set<String>, moveIndex: Map<String, String>): List<PhotoItem> =
    root.walkTopDown()
        .onEnter { it.name != RecentDeletedFolderName }
        .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in mediaExtensions }
        .filter { isVisibleMediaPath(relativePath(root, it)) }
        .filter { relativePath(root, it) !in deletedOriginalPaths }
        .sortedByDescending { it.lastModified() }
        .map { file ->
            val path = relativePath(root, file)
            val parent = moveIndex[path] ?: topAlbumName(path)
            PhotoItem(
                id = file.absolutePath.hashCode().toLong(),
                uri = Uri.fromFile(file),
                dateMillis = file.lastModified(),
                name = file.name,
                albumName = parent,
                relativePath = path,
                type = mediaType(file),
                durationMillis = videoDurationMillis(file),
            )
        }
        .toList()

private fun loadDeletedPhotos(root: File, index: Map<String, String>): List<PhotoItem> =
    index.mapNotNull { (currentPath, originalPath) ->
        val file = File(root, currentPath).takeIf { it.isFile && it.extension.lowercase(Locale.ROOT) in mediaExtensions } ?: return@mapNotNull null
        PhotoItem(
            id = file.absolutePath.hashCode().toLong(),
            uri = Uri.fromFile(file),
            dateMillis = file.lastModified(),
            name = file.name,
            albumName = "\u6700\u8fd1\u5220\u9664",
            relativePath = currentPath,
            originalRelativePath = originalPath,
            type = mediaType(file),
            durationMillis = videoDurationMillis(file),
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

private fun deleteDirectoryIfEmpty(folder: File): Boolean {
    if (!folder.isDirectory) return false
    folder.listFiles()?.filter { it.isDirectory }?.forEach { deleteDirectoryIfEmpty(it) }
    return folder.listFiles().isNullOrEmpty() && folder.delete()
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


private fun displayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
    }.orEmpty()
}.getOrDefault("")

private fun fallbackFileName(mimeType: String): String {
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.let { ".$it" }.orEmpty()
    return "media_${System.currentTimeMillis()}$extension"
}

private fun copyUriToFile(context: Context, uri: Uri, target: File): Boolean = runCatching {
    target.parentFile?.mkdirs()
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: return@runCatching false
    true
}.getOrDefault(false)
private fun isImportableMedia(mimeType: String, fileName: String): Boolean {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return mimeType.startsWith("image/") || mimeType.startsWith("video/") || extension in imageExtensions || extension in videoExtensions
}

private fun safeFileName(name: String): String =
    name.trim().substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\/:*?\"<>|]"), "_").ifBlank { "media_${System.currentTimeMillis()}" }
private fun safeAlbumName(name: String): String = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")

private fun topAlbumName(path: String): String =
    path.substringBefore('/', missingDelimiterValue = "")
private fun relativePath(root: File, file: File): String =
    root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')

private fun isVisibleMediaPath(path: String): Boolean {
    val parts = path.split('/').filter { it.isNotBlank() }
    return parts.all { isVisibleAlbumName(it) } && parts.windowed(2).none { it[0] == "Android" && it[1] == "data" }
}

private fun isVisibleAlbumName(name: String): Boolean =
    name.isNotBlank() && name != MediaRootName && name != RecentDeletedFolderName && !name.startsWith('.')
private fun colorFallback(seed: String): List<Color> =
    if (seed.isEmpty()) palette.take(4) else List(4) { palette[(seed.hashCode() + it).mod(palette.size)] }

fun loadBitmap(context: Context, uri: Uri, target: Int, isVideo: Boolean = false): Bitmap? {
    val key = thumbnailCacheKey(uri, target, isVideo)
    synchronized(thumbnailCache) { thumbnailCache.get(key) }?.let { return it }
    val bitmap = if (isVideo) {
        loadVideoThumbnail(context, uri, target)
    } else if (uri.scheme == "file") {
        decodeFileThumbnail(uri.path.orEmpty(), target)
    } else {
        runCatching { context.contentResolver.loadThumbnail(uri, Size(target, target), null) }.getOrNull()
            ?: decodeContentThumbnail(context, uri, target)
    }
    if (bitmap != null) synchronized(thumbnailCache) { thumbnailCache.put(key, bitmap) }
    return bitmap
}

// ponytail: memory-only thumbnail cache; add disk cache only if cold-start decoding is still too slow.
private val thumbnailCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
}

private fun thumbnailCacheKey(uri: Uri, target: Int, isVideo: Boolean): String {
    val fileStamp = if (uri.scheme == "file") File(uri.path.orEmpty()).lastModified() else 0L
    return uri.toString() + "|" + target + "|" + isVideo + "|" + fileStamp
}

private fun loadVideoThumbnail(context: Context, uri: Uri, target: Int): Bitmap? =
    if (uri.scheme == "file") {
        runCatching { ThumbnailUtils.createVideoThumbnail(File(uri.path.orEmpty()), Size(target, target), null) }.getOrNull()
    } else {
        runCatching { context.contentResolver.loadThumbnail(uri, Size(target, target), null) }.getOrNull()
    }

private fun decodeContentThumbnail(context: Context, uri: Uri, target: Int): Bitmap? = runCatching {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sample = generateSequence(1) { it * 2 }.first { bounds.outWidth / it <= target * 2 && bounds.outHeight / it <= target * 2 }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

private fun decodeFileThumbnail(path: String, target: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sample = generateSequence(1) { it * 2 }.first { bounds.outWidth / it <= target * 2 && bounds.outHeight / it <= target * 2 }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

private fun mediaType(file: File): MediaType =
    if (file.extension.lowercase(Locale.ROOT) in videoExtensions) MediaType.Video else MediaType.Image

private fun videoDurationMillis(file: File): Long {
    if (mediaType(file) != MediaType.Video) return 0L
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            FileInputStream(file).use { input ->
                retriever.setDataSource(input.fd)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)
}

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")
private val videoExtensions = setOf("mp4", "m4v", "mov", "mkv", "webm", "3gp")
private val mediaExtensions = imageExtensions + videoExtensions
