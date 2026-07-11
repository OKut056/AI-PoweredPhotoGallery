package com.example.ai_poweredphotogallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
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
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
private const val MediaRootName = "Media"
private const val RecentDeletedFolderName = ".recent_deleted"
private const val RecentDeletedIndexName = "trash_index.tsv"
private const val MediaIndexFolderName = ".media_index"
private const val HashIndexName = "hash_index.tsv"

private const val DurationIndexName = "duration_index.tsv"
private val importLock = Any()
fun galleryRoot(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, MediaRootName)
fun ensureGalleryRoot(context: Context): Boolean = galleryRoot(context).let { it.exists() || it.mkdirs() }

fun createAlbumFolder(context: Context, name: String): AlbumCreateResult {
    val safeName = validAlbumName(name) ?: return AlbumCreateResult.InvalidName
    val folder = File(galleryRoot(context), safeName)
    return when {
        folder.isDirectory -> AlbumCreateResult.AlreadyExists
        folder.exists() -> AlbumCreateResult.Failed
        folder.mkdirs() || folder.isDirectory -> AlbumCreateResult.Created
        else -> AlbumCreateResult.Failed
    }
}

fun importMediaFolder(context: Context, treeUri: Uri): ImportResult = synchronized(importLock) {
    val root = galleryRoot(context).apply { mkdirs() }
    val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
    val hashIndex = loadHashIndex(context, root).toMutableMap()
    val existingHashes = hashIndex.values.map { it.hash }.toMutableSet()
    // Import selected folder contents only; do not recreate the picked parent folder under Media.
    val result = importDocumentChildren(context, treeUri, treeDocumentId, root, hashIndex, existingHashes)
    writeHashIndex(context, hashIndex)
    result
}

private fun importDocumentChildren(
    context: Context,
    treeUri: Uri,
    documentId: String,
    targetDir: File,
    hashIndex: MutableMap<String, HashIndexEntry>,
    existingHashes: MutableSet<String>,
): ImportResult {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
    var imported = 0
    var skipped = 0
    var duplicateSkipped = 0

    context.contentResolver.query(
        childrenUri,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        while (cursor.moveToNext()) {
            val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val childId = cursor.getString(idColumn)
            val name = cursor.getString(nameColumn).orEmpty()
            val mimeType = cursor.getString(mimeColumn).orEmpty()
            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
            val sourceModified = modifiedColumn.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong) ?: 0L
            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                val directoryName = safeDirectoryName(name)
                val childTarget = directoryName?.let { File(targetDir, it) }
                if (childTarget == null || !isInside(galleryRoot(context), childTarget)) {
                    skipped++
                    continue
                }
                val result = importDocumentChildren(context, treeUri, childId, childTarget, hashIndex, existingHashes)
                imported += result.imported
                skipped += result.skipped
                duplicateSkipped += result.duplicateSkipped
            } else {
                val importName = importFileName(mimeType, name)
                if (importName == null) {
                    skipped++
                    continue
                }
                val mediaTime = importedMediaTimeMillis(context, childUri, mimeType, importName, sourceModified)
                val target = uniqueFile(File(targetDir, importName))
                when (copyUniqueUriToFile(context, childUri, target, galleryRoot(context), mediaTime, hashIndex, existingHashes)) {
                    ImportCopyResult.Imported -> imported++
                    ImportCopyResult.Duplicate -> { skipped++; duplicateSkipped++ }
                    ImportCopyResult.Failed -> skipped++
                }
            }
        }
    } ?: skipped++

    return ImportResult(imported, skipped, duplicateSkipped)
}

fun importMediaFiles(context: Context, uris: List<Uri>): ImportResult = synchronized(importLock) {
    val root = galleryRoot(context).apply { mkdirs() }
    val hashIndex = loadHashIndex(context, root).toMutableMap()
    val existingHashes = hashIndex.values.map { it.hash }.toMutableSet()
    var imported = 0
    var skipped = 0
    var duplicateSkipped = 0

    uris.forEach { uri ->
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val fileName = importFileName(
            mimeType,
            displayName(context, uri).ifBlank { uri.lastPathSegment.orEmpty() }.ifBlank { fallbackFileName(mimeType) },
        )
        if (fileName == null) {
            skipped++
            return@forEach
        }
        val mediaTime = importedMediaTimeMillis(context, uri, mimeType, fileName, sourceModifiedTime(context, uri))
        val target = uniqueFile(File(root, fileName))
        when (copyUniqueUriToFile(context, uri, target, root, mediaTime, hashIndex, existingHashes)) {
            ImportCopyResult.Imported -> imported++
            ImportCopyResult.Duplicate -> { skipped++; duplicateSkipped++ }
            ImportCopyResult.Failed -> skipped++
        }
    }

    if (imported > 0) writeHashIndex(context, hashIndex)
    ImportResult(imported, skipped, duplicateSkipped)
}
fun loadGalleryData(context: Context): GalleryData {
    val root = galleryRoot(context).apply { mkdirs() }
    val storedDeletedIndex = readDeletedIndex(context)
    val deletedIndex = storedDeletedIndex.filterKeys { currentPath ->
        val current = File(root, currentPath)
        isInside(root, current) && current.isFile
    }
    if (deletedIndex.size != storedDeletedIndex.size) writeDeletedIndex(context, deletedIndex)
    val deletedPaths = deletedIndex.keys
    val durationIndex = readDurationIndex(context).toMutableMap()
    val photos = loadPhotos(root, deletedPaths, durationIndex)
    val deletedPhotos = loadDeletedPhotos(root, deletedIndex, durationIndex)
    durationIndex.keys.retainAll((photos + deletedPhotos).filter { it.isVideo }.map { it.relativePath }.toSet())
    writeDurationIndex(context, durationIndex)
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

fun movePhotosToAlbum(context: Context, photos: List<PhotoItem>, selectedIds: Set<String>, albumName: String): Int {
    val root = galleryRoot(context)
    val safeName = if (albumName == AllAlbumName) "" else validAlbumName(albumName) ?: return 0
    val targetDir = if (safeName.isEmpty()) root else File(root, safeName).apply { mkdirs() }
    var moved = 0

    photos.filter { it.id in selectedIds }.forEach { photo ->
        if (photo.albumName == safeName) return@forEach
        val source = File(photo.uri?.path.orEmpty()).takeIf { it.isFile } ?: return@forEach
        val target = uniqueFile(File(targetDir, source.name))
        if (moveFile(source, target)) moved++
    }

    return moved
}

fun prepareDeleteRequest(context: Context, photos: List<PhotoItem>, selectedIds: Set<String>): DeleteRequest {
    val selected = photos.filter { it.id in selectedIds }
    val moved = movePhotosToRecentDeleted(context, selected, selectedIds)
    return DeleteRequest(localMoved = moved, skipped = selected.size - moved)
}

fun movePhotosToRecentDeleted(context: Context, photos: List<PhotoItem>, selectedIds: Set<String>): Int =
    movePhotosToRecentDeleted(context, photos, selectedIds) { photo, root, source ->
        photo.relativePath.ifBlank { relativePath(root, source) }
    }

private fun movePhotosToRecentDeleted(
    context: Context,
    photos: List<PhotoItem>,
    selectedIds: Set<String>,
    originalPathFor: (PhotoItem, File, File) -> String,
): Int {
    val root = galleryRoot(context)
    val trash = recentDeletedFolder(root).apply { mkdirs() }
    val index = readDeletedIndex(context).toMutableMap()
    var moved = 0

    photos.filter { it.id in selectedIds }.forEach { photo ->
        val source = File(photo.uri?.path.orEmpty()).takeIf { it.isFile } ?: return@forEach
        val physicalPath = photo.relativePath.ifBlank { relativePath(root, source) }
        val originalPath = originalPathFor(photo, root, source).ifBlank { physicalPath }
        val physicalTarget = uniqueFile(File(trash, source.name))
        val currentPath = relativePath(root, physicalTarget)
        val updatedIndex = index + (currentPath to originalPath)
        if (!writeDeletedIndex(context, updatedIndex)) return@forEach
        if (moveFile(source, physicalTarget)) {
            index[currentPath] = originalPath
            moved++
        } else {
            writeDeletedIndex(context, index)
        }
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

fun deleteAlbumsToRecentDeleted(context: Context, photos: List<PhotoItem>, albumNames: Set<String>): DeleteAlbumResult {
    val names = (albumNames - AllAlbumName).map { safeAlbumName(it) }.filter { it.isNotEmpty() }.toSet()
    if (names.isEmpty()) return DeleteAlbumResult(0, 0)
    val albumPhotos = photos.filter { safeAlbumName(it.albumName) in names }
    val moved = movePhotosToRecentDeleted(context, albumPhotos, albumPhotos.map { it.id }.toSet()) { photo, root, source ->
        photo.relativePath.ifBlank { relativePath(root, source) }
    }
    val root = galleryRoot(context)
    val removedFolders = names.count { name -> deleteDirectoryIfEmpty(File(root, name)) }
    return DeleteAlbumResult(moved, removedFolders)
}

fun restoreDeletedPhotos(context: Context, photos: List<PhotoItem>, selectedIds: Set<String>): RestoreResult {
    val root = galleryRoot(context)
    val index = readDeletedIndex(context).toMutableMap()
    var restored = 0

    photos.filter { it.id in selectedIds }.forEach { photo ->
        val source = File(photo.uri?.path.orEmpty()).takeIf { it.isFile } ?: return@forEach
        val currentPath = photo.relativePath.ifBlank { relativePath(root, source) }
        val originalPath = photo.originalRelativePath.ifBlank { index[currentPath].orEmpty() }.ifBlank { source.name }
        val originalTarget = File(root, originalPath)
        if (!isInside(root, originalTarget)) return@forEach
        val target = uniqueFile(originalTarget).also { it.parentFile?.mkdirs() }
        if (moveFile(source, target)) {
            index.remove(currentPath)
            restored++
        }
    }

    if (restored > 0) writeDeletedIndex(context, index)
    return RestoreResult(restored)
}

fun permanentlyDeletePhotos(context: Context, photos: List<PhotoItem>, selectedIds: Set<String>): Int {
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

private fun loadPhotos(root: File, deletedOriginalPaths: Set<String>, durationIndex: MutableMap<String, DurationIndexEntry>): List<PhotoItem> =
    root.walkTopDown()
        .onEnter { it == root || isVisibleAlbumName(it.name) }
        .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in mediaExtensions }
        .filter { isVisibleMediaPath(relativePath(root, it)) }
        .filter { relativePath(root, it) !in deletedOriginalPaths }
        .sortedByDescending { it.lastModified() }
        .map { file ->
            val path = relativePath(root, file)
            val parent = topAlbumName(path)
            PhotoItem(
                id = path,
                uri = Uri.fromFile(file),
                dateMillis = file.lastModified(),
                name = file.name,
                albumName = parent,
                relativePath = path,
                type = mediaType(file),
                durationMillis = cachedVideoDuration(root, file, durationIndex),
            )
        }
        .toList()

private fun loadDeletedPhotos(root: File, index: Map<String, String>, durationIndex: MutableMap<String, DurationIndexEntry>): List<PhotoItem> =
    index.mapNotNull { (currentPath, originalPath) ->
        val candidate = File(root, currentPath)
        val file = candidate.takeIf { isInside(root, it) && it.isFile && it.extension.lowercase(Locale.ROOT) in mediaExtensions } ?: return@mapNotNull null
        PhotoItem(
            id = currentPath,
            uri = Uri.fromFile(file),
            dateMillis = file.lastModified(),
            name = file.name,
            albumName = "\u6700\u8fd1\u5220\u9664",
            relativePath = currentPath,
            originalRelativePath = originalPath,
            type = mediaType(file),
            durationMillis = cachedVideoDuration(root, file, durationIndex),
        )
    }.sortedByDescending { it.dateMillis }

fun photosForAlbum(albumName: String, photos: List<PhotoItem>): List<PhotoItem> =
    if (albumName == AllAlbumName) photos else photos.filter { it.albumName == albumName }

private fun recentDeletedFolder(root: File): File = File(root, RecentDeletedFolderName)
private fun deletedIndexFile(context: Context): File = File(File(context.filesDir, RecentDeletedFolderName).apply { mkdirs() }, RecentDeletedIndexName)

private fun readDeletedIndex(context: Context): Map<String, String> = runCatching {
    val file = deletedIndexFile(context)
    if (!file.isFile) return emptyMap()
    file.readLines().mapNotNull { line ->
        val tab = line.indexOf('\t')
        if (tab <= 0) null else line.take(tab) to line.drop(tab + 1)
    }.toMap()
}.getOrDefault(emptyMap())

private fun writeDeletedIndex(context: Context, index: Map<String, String>): Boolean =
    writeAtomically(deletedIndexFile(context), index.entries.joinToString("\n") { it.key + "\t" + it.value })

private fun writeAtomically(file: File, text: String): Boolean = runCatching {
    file.parentFile?.mkdirs()
    val temporary = File(file.parentFile, ".${file.name}.${UUID.randomUUID()}.tmp")
    try {
        temporary.writeText(text)
        runCatching {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.recoverCatching {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrThrow()
        true
    } finally {
        temporary.delete()
    }
}.getOrDefault(false)

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

private fun sourceModifiedTime(context: Context, uri: Uri): Long {
    if (uri.scheme == "file") return File(uri.path.orEmpty()).lastModified()
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)
}

private fun importedMediaTimeMillis(
    context: Context,
    uri: Uri,
    mimeType: String,
    fileName: String,
    sourceModified: Long,
): Long {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val embedded = when {
        mimeType.startsWith("video/") || extension in videoExtensions -> videoCreationTimeMillis(context, uri)
        mimeType.startsWith("image/") || extension in imageExtensions -> imageCreationTimeMillis(context, uri)
        else -> null
    }
    // Prefer a later source mtime when the media was edited after its embedded creation time.
    return listOfNotNull(embedded?.takeIf { it > 0L }, sourceModified.takeIf { it > 0L })
        .maxOrNull()
        ?: System.currentTimeMillis()
}

@Suppress("DEPRECATION")
private fun imageCreationTimeMillis(context: Context, uri: Uri): Long? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val exif = ExifInterface(input)
        listOf(ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_DATETIME)
            .firstNotNullOfOrNull { tag -> exif.getAttribute(tag)?.let(::parseExifTime) }
    }
}.getOrNull()

private fun parseExifTime(value: String): Long? = runCatching {
    LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}.getOrNull()

private fun videoCreationTimeMillis(context: Context, uri: Uri): Long? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        if (uri.scheme == "file") retriever.setDataSource(uri.path.orEmpty()) else retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.let(::parseVideoTime)
    } finally {
        retriever.release()
    }
}.getOrNull()

private fun parseVideoTime(value: String): Long? {
    val parsers = listOf<() -> Long>(
        { Instant.parse(value).toEpochMilli() },
        { OffsetDateTime.parse(value).toInstant().toEpochMilli() },
        { OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSX", Locale.ROOT)).toInstant().toEpochMilli() },
        {
            LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS", Locale.ROOT))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        },
    )
    return parsers.firstNotNullOfOrNull { parser -> runCatching(parser).getOrNull() }
}

private fun copyUriToFile(context: Context, uri: Uri, target: File, mediaTimeMillis: Long): Boolean {
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.partial")
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching false
        check(temporary.renameTo(target))
        if (mediaTimeMillis > 0L) target.setLastModified(mediaTimeMillis)
        true
    }.getOrElse {
        temporary.delete()
        false
    }
}

private fun copyUniqueUriToFile(
    context: Context,
    uri: Uri,
    target: File,
    root: File,
    mediaTimeMillis: Long,
    hashIndex: MutableMap<String, HashIndexEntry>,
    existingHashes: MutableSet<String>,
): ImportCopyResult {
    if (!isInside(root, target)) return ImportCopyResult.Failed
    val hash = contentHash(context, uri) ?: return ImportCopyResult.Failed
    if (!existingHashes.add(hash)) return ImportCopyResult.Duplicate
    if (!copyUriToFile(context, uri, target, mediaTimeMillis)) {
        existingHashes.remove(hash)
        return ImportCopyResult.Failed
    }
    val path = relativePath(root, target)
    hashIndex[path] = HashIndexEntry(path, target.length(), target.lastModified(), hash)
    return ImportCopyResult.Imported
}

private data class DurationIndexEntry(val path: String, val size: Long, val modified: Long, val duration: Long)
private enum class ImportCopyResult { Imported, Duplicate, Failed }
private data class HashIndexEntry(val path: String, val size: Long, val modified: Long, val hash: String)

private fun loadHashIndex(context: Context, root: File): Map<String, HashIndexEntry> {
    val index = readHashIndex(context).toMutableMap()
    val files = root.walkTopDown()
        .onEnter { it == root || isVisibleAlbumName(it.name) }
        .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in mediaExtensions }
        .filter { isVisibleMediaPath(relativePath(root, it)) }
        .toList()
    val paths = files.map { relativePath(root, it) }.toSet()
    index.keys.retainAll(paths)

    files.forEach { file ->
        val path = relativePath(root, file)
        val cached = index[path]
        if (cached?.size == file.length() && cached.modified == file.lastModified()) return@forEach
        val hash = contentHash(file)
        if (hash == null) {
            index.remove(path)
        } else {
            index[path] = HashIndexEntry(path, file.length(), file.lastModified(), hash)
        }
    }

    writeHashIndex(context, index)
    return index
}

private fun hashIndexFile(context: Context): File = File(File(context.filesDir, MediaIndexFolderName).apply { mkdirs() }, HashIndexName)

private fun readHashIndex(context: Context): Map<String, HashIndexEntry> = runCatching {
    val file = hashIndexFile(context)
    if (!file.isFile) return emptyMap()
    file.readLines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 4) return@mapNotNull null
        val path = decodeIndexPath(parts[0]).ifBlank { return@mapNotNull null }
        val size = parts[1].toLongOrNull() ?: return@mapNotNull null
        val modified = parts[2].toLongOrNull() ?: return@mapNotNull null
        path to HashIndexEntry(path, size, modified, parts[3])
    }.toMap()
}.getOrDefault(emptyMap())


private fun durationIndexFile(context: Context): File = File(File(context.filesDir, MediaIndexFolderName).apply { mkdirs() }, DurationIndexName)

private fun readDurationIndex(context: Context): Map<String, DurationIndexEntry> = runCatching {
    val file = durationIndexFile(context)
    if (!file.isFile) return emptyMap()
    file.readLines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 4) return@mapNotNull null
        val path = decodeIndexPath(parts[0]).ifBlank { return@mapNotNull null }
        val size = parts[1].toLongOrNull() ?: return@mapNotNull null
        val modified = parts[2].toLongOrNull() ?: return@mapNotNull null
        val duration = parts[3].toLongOrNull() ?: return@mapNotNull null
        path to DurationIndexEntry(path, size, modified, duration)
    }.toMap()
}.getOrDefault(emptyMap())

private fun writeDurationIndex(context: Context, index: Map<String, DurationIndexEntry>): Boolean =
    writeAtomically(
        durationIndexFile(context),
        index.values.joinToString("\n") { encodeIndexPath(it.path) + "\t" + it.size + "\t" + it.modified + "\t" + it.duration },
    )

private fun cachedVideoDuration(
    root: File,
    file: File,
    index: MutableMap<String, DurationIndexEntry>,
): Long {
    if (mediaType(file) != MediaType.Video) return 0L
    val path = relativePath(root, file)
    val cached = index[path]
    if (cached?.size == file.length() && cached.modified == file.lastModified()) return cached.duration
    val duration = videoDurationMillis(file)
    index[path] = DurationIndexEntry(path, file.length(), file.lastModified(), duration)
    return duration
}
private fun writeHashIndex(context: Context, index: Map<String, HashIndexEntry>): Boolean =
    writeAtomically(hashIndexFile(context), index.values.joinToString("\n") { encodeIndexPath(it.path) + "\t" + it.size + "\t" + it.modified + "\t" + it.hash })

private fun encodeIndexPath(path: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(path.toByteArray(Charsets.UTF_8))
private fun decodeIndexPath(path: String): String = runCatching { String(Base64.getUrlDecoder().decode(path), Charsets.UTF_8) }.getOrDefault("")

private fun contentHash(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use(::sha256)
}.getOrNull()

private fun contentHash(file: File): String? = runCatching {
    FileInputStream(file).use(::sha256)
}.getOrNull()

private fun sha256(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
    }
    return Base64.getEncoder().encodeToString(digest.digest())
}

private fun safeFileName(name: String): String =
    name.trim().substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\/:*?\"<>|]"), "_")
        .takeUnless { it.isBlank() || it == "." || it == ".." }
        ?: "media_${System.currentTimeMillis()}"

private fun safeDirectoryName(name: String): String? =
    name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").takeUnless { it.isBlank() || it == "." || it == ".." }

private fun safeAlbumName(name: String): String = safeDirectoryName(name).orEmpty()

// Keep user albums distinct from virtual categories and app-owned storage directories.
private val reservedAlbumNames = setOf(
    AllAlbumName,
    MediaRootName,
    RecentDeletedFolderName,
    "\u56fe\u7247", "\u56fe\u50cf", "\u7167\u7247", "\u76f8\u7247", "\u89c6\u9891", "\u5f55\u50cf", "\u5f71\u7247", "\u7535\u5f71", "\u77ed\u89c6\u9891", "\u52a8\u56fe",
).mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }

private fun validAlbumName(name: String): String? {
    val trimmed = name.trim()
    val safeName = safeDirectoryName(trimmed) ?: return null
    return safeName.takeIf {
        it == trimmed && !it.startsWith('.') && it.lowercase(Locale.ROOT) !in reservedAlbumNames
    }
}

private fun importFileName(mimeType: String, name: String): String? {
    val safeName = safeFileName(name)
    if (safeName.substringAfterLast('.', "").lowercase(Locale.ROOT) in mediaExtensions) return safeName
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.lowercase(Locale.ROOT)
        ?.takeIf { it in mediaExtensions && (mimeType.startsWith("image/") || mimeType.startsWith("video/")) }
        ?: return null
    val base = safeName.substringBeforeLast('.', safeName).ifBlank { "media_${System.currentTimeMillis()}" }
    return "$base.$extension"
}

private fun isInside(root: File, target: File): Boolean = runCatching {
    target.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())
}.getOrDefault(false)

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
