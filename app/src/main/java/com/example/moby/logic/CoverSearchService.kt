package com.example.moby.logic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder

data class WebCover(
    val id: String,
    val thumbnailUrl: String,
    val largeUrl: String
)

class CoverSearchService(private val context: Context) {

    suspend fun searchCovers(query: String): List<WebCover> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://openlibrary.org/search.json?q=$encodedQuery&limit=30")
            val connection = url.openConnection()
            val jsonText = connection.getInputStream().bufferedReader().use { it.readText() }
            
            val jsonObject = JSONObject(jsonText)
            val docs = jsonObject.getJSONArray("docs")
            
            val results = mutableListOf<WebCover>()
            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                if (doc.has("cover_i")) {
                    val coverId = doc.getInt("cover_i").toString()
                    results.add(
                        WebCover(
                            id = coverId,
                            thumbnailUrl = "https://covers.openlibrary.org/b/id/$coverId-M.jpg",
                            largeUrl = "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
                        )
                    )
                } else if (doc.has("cover_edition_key")) {
                    val coverId = doc.getString("cover_edition_key")
                    results.add(
                        WebCover(
                            id = coverId,
                            thumbnailUrl = "https://covers.openlibrary.org/b/olid/$coverId-M.jpg",
                            largeUrl = "https://covers.openlibrary.org/b/olid/$coverId-L.jpg"
                        )
                    )
                }
            }
            results.distinctBy { it.thumbnailUrl }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun downloadCover(imageUrl: String, publicationId: String): String? = withContext(Dispatchers.IO) {
        try {
            val coverDir = File(context.filesDir, "covers")
            if (!coverDir.exists()) coverDir.mkdirs()
            
            val coverFile = File(coverDir, "cover_$publicationId.jpg")
            val url = URL(imageUrl)
            url.openStream().use { input ->
                coverFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            coverFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
