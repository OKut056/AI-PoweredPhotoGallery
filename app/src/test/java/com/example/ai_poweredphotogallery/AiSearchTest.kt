package com.example.ai_poweredphotogallery

import org.junit.Assert.assertEquals
import org.junit.Test

class AiSearchTest {
    @Test
    fun matchesAlbumAndChineseVideoType() {
        val photos = listOf(
            PhotoItem(id = "1", name = "clip.mp4", albumName = "\u65c5\u884c", relativePath = "\u65c5\u884c/clip.mp4", type = MediaType.Video),
            PhotoItem(id = "2", name = "cat.jpg", albumName = "\u65e5\u5e38", relativePath = "\u65e5\u5e38/cat.jpg"),
        )

        assertEquals(listOf("1"), searchAiIndex(photos, "\u65c5\u884c \u89c6\u9891").map { it.id })
    }

    @Test
    fun blankQueryReturnsNewestFirst() {
        val photos = listOf(
            PhotoItem(id = "1", name = "old.jpg", dateMillis = 1),
            PhotoItem(id = "2", name = "new.jpg", dateMillis = 2),
        )

        assertEquals(listOf("2", "1"), searchAiIndex(photos, "").map { it.id })
    }
}