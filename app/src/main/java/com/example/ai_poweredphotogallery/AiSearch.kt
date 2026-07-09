package com.example.ai_poweredphotogallery

import java.util.Locale

fun searchAiIndex(photos: List<PhotoItem>, query: String): List<PhotoItem> {
    val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
    if (terms.isEmpty()) return photos.sortedByDescending { it.dateMillis }
    return photos
        .filter { photo ->
            val text = aiIndexText(photo)
            terms.all { term -> aiAliases(term).any(text::contains) }
        }
        .sortedWith(compareByDescending<PhotoItem> { aiScore(it, terms) }.thenByDescending { it.dateMillis })
}

private fun aiIndexText(photo: PhotoItem): String {
    // ponytail: metadata-only search; replace with a real local vision index when the model choice exists.
    val typeText = if (photo.isVideo) "video \u89c6\u9891 \u77ed\u89c6\u9891 \u5f71\u7247" else "image photo \u56fe\u7247 \u7167\u7247 \u56fe\u50cf"
    return listOf(photo.name, photo.albumName, photo.relativePath, photo.label, photo.type.name, typeText)
        .joinToString(" ")
        .lowercase(Locale.ROOT)
}

private fun aiAliases(term: String): List<String> = when (term) {
    "\u56fe", "\u56fe\u7247", "\u7167\u7247", "\u76f8\u7247", "photo", "image" -> listOf("\u56fe\u7247", "\u7167\u7247", "photo", "image")
    "\u89c6\u9891", "\u77ed\u89c6\u9891", "\u5f71\u7247", "video" -> listOf("\u89c6\u9891", "\u77ed\u89c6\u9891", "\u5f71\u7247", "video")
    else -> listOf(term)
}

private fun aiScore(photo: PhotoItem, terms: List<String>): Int {
    val name = photo.name.lowercase(Locale.ROOT)
    val album = photo.albumName.lowercase(Locale.ROOT)
    val path = photo.relativePath.lowercase(Locale.ROOT)
    return terms.sumOf { term ->
        when {
            name.contains(term) -> 4
            album.contains(term) -> 3
            path.contains(term) -> 2
            else -> 1
        }
    }
}
