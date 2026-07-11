package com.example.ai_poweredphotogallery

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.ai_poweredphotogallery", appContext.packageName)
    }

    @Test
    fun restoreDeletedAlbumPhotoRecreatesMissingAlbum() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val albumName = "codex_probe_restore_${System.currentTimeMillis()}"
        val root = galleryRoot(context).apply { mkdirs() }
        val albumDir = File(root, albumName).apply { mkdirs() }
        val photo = File(albumDir, "probe.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val initialData = loadGalleryData(context)
        val deleteResult = deleteAlbumsToRecentDeleted(context, initialData.photos, setOf(albumName))
        assertEquals(1, deleteResult.moved)
        assertFalse(photo.exists())
        assertFalse(albumDir.exists())

        val deletedData = loadGalleryData(context)
        val deletedPhoto = deletedData.deletedPhotos.single { it.name == "probe.jpg" }
        assertEquals("$albumName/probe.jpg", deletedPhoto.originalRelativePath)

        val result = restoreDeletedPhotos(context, deletedData.deletedPhotos, setOf(deletedPhoto.id))
        assertEquals(1, result.restored)
        assertTrue(File(root, "$albumName/probe.jpg").isFile)
        File(root, albumName).deleteRecursively()
    }

    @Test
    fun restoreLogicalDeletedRootFileBackToRecordedAlbum() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val albumName = "codex_probe_logical_${System.currentTimeMillis()}"
        val fileName = "$albumName.jpg"
        val root = galleryRoot(context).apply { mkdirs() }
        val albumDir = File(root, albumName).apply { mkdirs() }
        val rootPhoto = File(root, fileName).apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val indexFile = File(File(context.filesDir, ".recent_deleted").apply { mkdirs() }, "trash_index.tsv")
        val previousIndex = indexFile.takeIf { it.isFile }?.readText().orEmpty()

        try {
            val separator = if (previousIndex.isEmpty() || previousIndex.endsWith("\n")) "" else "\n"
            indexFile.writeText(previousIndex + separator + "$fileName\t$albumName/$fileName\n")
            val deletedData = loadGalleryData(context)
            val deletedPhoto = deletedData.deletedPhotos.single { it.name == fileName }
            val result = restoreDeletedPhotos(context, deletedData.deletedPhotos, setOf(deletedPhoto.id))

            assertEquals(1, result.restored)
            assertFalse(rootPhoto.exists())
            assertTrue(File(albumDir, fileName).isFile)
        } finally {
            indexFile.writeText(previousIndex)
            rootPhoto.delete()
            albumDir.deleteRecursively()
        }
    }
    @Test
    fun deleteTopLevelImportedFolderAlbumRemovesNestedEmptyFolders() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val albumName = "codex_probe_nested_${System.currentTimeMillis()}"
        val root = galleryRoot(context).apply { mkdirs() }
        val nestedDir = File(root, "$albumName/Sub").apply { mkdirs() }
        val photo = File(nestedDir, "probe.jpg").apply { writeBytes(byteArrayOf(10, 11, 12)) }

        val initialData = loadGalleryData(context)
        val nestedPhoto = initialData.photos.single { it.name == "probe.jpg" }
        assertEquals(albumName, nestedPhoto.albumName)

        val deleteResult = deleteAlbumsToRecentDeleted(context, initialData.photos, setOf(albumName))
        assertEquals(1, deleteResult.moved)
        assertFalse(photo.exists())
        assertFalse(File(root, albumName).exists())

        val deletedData = loadGalleryData(context)
        val deletedPhoto = deletedData.deletedPhotos.single { it.name == "probe.jpg" }
        assertEquals("$albumName/Sub/probe.jpg", deletedPhoto.originalRelativePath)
        permanentlyDeletePhotos(context, deletedData.deletedPhotos, setOf(deletedPhoto.id))
    }
    @Test
    fun movePhotoToAlbumMovesPhysicalFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val albumName = "codex_probe_move_${System.currentTimeMillis()}"
        val root = galleryRoot(context).apply { mkdirs() }
        val source = File(root, "$albumName.jpg").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        File(root, albumName).apply { mkdirs() }

        val data = loadGalleryData(context)
        val photo = data.photos.single { it.name == "$albumName.jpg" }
        val moved = movePhotosToAlbum(context, data.photos, setOf(photo.id), albumName)

        assertEquals(1, moved)
        assertFalse(source.exists())
        assertTrue(File(root, "$albumName/$albumName.jpg").isFile)
        File(root, albumName).deleteRecursively()
    }
    @Test
    fun importSkipsRenamedDuplicateByContentHash() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "codex_probe_hash_${System.currentTimeMillis()}"
        val root = galleryRoot(context).apply { mkdirs() }
        val existing = File(root, "$prefix-existing.jpg").apply { writeBytes(byteArrayOf(12, 13, 14)) }
        val source = File(context.cacheDir, "$prefix-renamed.jpg").apply { writeBytes(byteArrayOf(12, 13, 14)) }

        try {
            val result = importMediaFiles(context, listOf(Uri.fromFile(source)))

            assertEquals(0, result.imported)
            assertEquals(1, result.skipped)
            assertEquals(1, result.duplicateSkipped)
            assertFalse(File(root, source.name).exists())
            assertTrue(existing.isFile)
        } finally {
            existing.delete()
            source.delete()
            File(root, source.name).delete()
        }
    }

    @Test
    fun collidingJavaPathHashesStillHaveDistinctMediaIds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "codex_probe_collision_${System.currentTimeMillis()}"
        val root = galleryRoot(context).apply { mkdirs() }
        val first = File(root, "$prefix-Aa.jpg")
        val second = File(root, "$prefix-BB.jpg")

        try {
            first.writeBytes(byteArrayOf(20))
            second.writeBytes(byteArrayOf(21))
            assertEquals(first.absolutePath.hashCode(), second.absolutePath.hashCode())

            val items = loadGalleryData(context).photos.filter { it.name == first.name || it.name == second.name }
            assertEquals(2, items.size)
            assertEquals(2, items.map { it.id }.toSet().size)
        } finally {
            first.delete()
            second.delete()
        }
    }

    @Test
    fun staleTrashEntryIsPrunedWithoutHidingOriginalMedia() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "codex_probe_stale_${System.currentTimeMillis()}.jpg"
        val missingTrashPath = ".recent_deleted/missing-$fileName"
        val root = galleryRoot(context).apply { mkdirs() }
        val media = File(root, fileName)
        val indexFile = File(File(context.filesDir, ".recent_deleted").apply { mkdirs() }, "trash_index.tsv")
        val previousIndex = indexFile.takeIf { it.isFile }?.readText().orEmpty()

        try {
            media.writeBytes(byteArrayOf(22))
            val separator = if (previousIndex.isEmpty() || previousIndex.endsWith("\n")) "" else "\n"
            indexFile.writeText(previousIndex + separator + "$missingTrashPath\t$fileName\n")

            val data = loadGalleryData(context)

            assertTrue(data.photos.any { it.name == fileName })
            assertFalse(indexFile.readText().contains(missingTrashPath))
        } finally {
            indexFile.writeText(previousIndex)
            media.delete()
        }
    }
    @Test
    fun importPreservesSourceTimeWhenEmbeddedDateIsMissing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "codex_probe_date_${System.currentTimeMillis()}.jpg"
        val source = File(context.cacheDir, fileName)
        val target = File(galleryRoot(context), fileName)
        val expectedTime = 946684800000L

        try {
            source.writeBytes(System.currentTimeMillis().toString().toByteArray())
            assertTrue(source.setLastModified(expectedTime))

            val result = importMediaFiles(context, listOf(Uri.fromFile(source)))

            assertEquals(1, result.imported)
            assertTrue(target.isFile)
            assertTrue(kotlin.math.abs(target.lastModified() - expectedTime) < 2_000L)
            assertEquals(target.lastModified(), loadGalleryData(context).photos.single { it.name == fileName }.dateMillis)
        } finally {
            source.delete()
            target.delete()
        }
    }

    @Test
    fun newMediaAtDeletedOriginalPathRemainsVisible() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "codex_probe_recreated_${System.currentTimeMillis()}.jpg"
        val root = galleryRoot(context).apply { mkdirs() }
        val original = File(root, fileName).apply { writeBytes(byteArrayOf(30)) }

        try {
            val photo = loadGalleryData(context).photos.single { it.name == fileName }
            assertEquals(1, prepareDeleteRequest(context, listOf(photo), setOf(photo.id)).localMoved)
            original.writeBytes(byteArrayOf(31))

            val data = loadGalleryData(context)
            assertTrue(data.photos.any { it.relativePath == fileName })
            assertTrue(data.deletedPhotos.any { it.originalRelativePath == fileName })
            permanentlyDeletePhotos(
                context,
                data.deletedPhotos,
                data.deletedPhotos.filter { it.originalRelativePath == fileName }.map { it.id }.toSet(),
            )
        } finally {
            original.delete()
        }
    }

    @Test
    fun reservedAndHiddenAlbumNamesAreRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        listOf("Media", ".recent_deleted", ".hidden", AllAlbumName, "\u56fe\u7247", "\u89c6\u9891", "\u5f55\u50cf").forEach { name ->
            assertEquals(name, AlbumCreateResult.InvalidName, createAlbumFolder(context, name))
        }
    }
}
