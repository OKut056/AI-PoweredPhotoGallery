package com.example.ai_poweredphotogallery

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ai_poweredphotogallery.ui.theme.AIPoweredPhotoGalleryTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageDetailsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun imageDetailsCardDisplaysKnownWorkspaceFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val albumName = "codex_probe_details"
        val fileName = "known_${System.currentTimeMillis()}.png"
        val album = File(galleryRoot(context), albumName).apply { mkdirs() }
        val file = File(album, fileName)

        try {
            val bitmap = Bitmap.createBitmap(7, 5, Bitmap.Config.ARGB_8888)
            file.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            bitmap.recycle()

            val photo = loadGalleryData(context).photos.single { it.relativePath == "$albumName/$fileName" }
            val details = loadImageDetails(context, photo)

            assertTrue(details.isReadable)
            assertEquals(file.length(), details.sizeBytes)
            assertEquals(7, details.width)
            assertEquals(5, details.height)

            composeRule.setContent {
                AIPoweredPhotoGalleryTheme {
                    PhotoViewerScreen(listOf(photo), photo.id, onBack = {})
                }
            }

            composeRule.onNodeWithText("\u24d8").performClick()
            composeRule.onNodeWithText(fileName).assertExists()
            composeRule.onNodeWithText("7 \u00d7 5", substring = true).assertExists()
            composeRule.onNodeWithText("Media/$albumName/$fileName").assertExists()
            composeRule.onNodeWithText("\u24d8").performClick()
            composeRule.onNodeWithText(fileName).assertDoesNotExist()
        } finally {
            file.delete()
            album.delete()
        }
    }

    @Test
    fun horizontalSwipeChangesTheCurrentPhoto() {
        val first = PhotoItem(
            id = "first",
            dateMillis = 946684800000L,
            name = "first.jpg",
        )
        val second = PhotoItem(
            id = "second",
            dateMillis = 946771200000L,
            name = "second.jpg",
        )

        composeRule.setContent {
            AIPoweredPhotoGalleryTheme {
                PhotoViewerScreen(listOf(first, second), first.id, onBack = {})
            }
        }

        composeRule.onNodeWithText("1\u67081\u65e5").assertExists()
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("1\u67082\u65e5").assertExists()
    }
}
