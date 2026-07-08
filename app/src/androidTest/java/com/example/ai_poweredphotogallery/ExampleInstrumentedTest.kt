package com.example.ai_poweredphotogallery

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
        val moved = deleteAlbumsToRecentDeleted(context, initialData.photos, setOf(albumName))
        assertEquals(1, moved)
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

        val moved = deleteAlbumsToRecentDeleted(context, initialData.photos, setOf(albumName))
        assertEquals(1, moved)
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
}
