package com.example.moby.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.moby.models.Publication
import com.example.moby.models.PublicationFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class BookMetadataExtractor(private val context: Context) {

    suspend fun extract(uri: Uri, fileName: String): Publication? = withContext(Dispatchers.IO) {
        try {
            val format = when {
                fileName.endsWith(".epub", true) -> PublicationFormat.EPUB
                fileName.endsWith(".pdf", true) -> PublicationFormat.PDF
                fileName.endsWith(".cbz", true) -> PublicationFormat.CBZ
                else -> PublicationFormat.OTHER
            }

            // Copy file to internal storage for permanent access
            val internalFile = copyFileToInternal(uri, fileName) ?: return@withContext null
            
            var title = fileName.replaceAfterLast(".", "").removeSuffix(".")
            var author = "Unknown Author"
            var coverPath: String? = null

            when (format) {
                PublicationFormat.EPUB -> {
                    val metadata = extractEpubMetadata(internalFile)
                    if (metadata.title != null) title = metadata.title
                    if (metadata.author != null) author = metadata.author
                    coverPath = metadata.coverPath
                }
                PublicationFormat.PDF -> {
                    coverPath = generatePdfThumbnail(internalFile)
                }
                PublicationFormat.CBZ -> {
                    coverPath = extractCbzCover(internalFile)
                }
                else -> {}
            }

            Publication(
                title = title,
                author = author,
                format = format,
                coverUrl = coverPath,
                filePath = internalFile.absolutePath
            )
        } catch (e: Exception) {
            Log.e("MetadataExtractor", "Error extracting metadata", e)
            null
        }
    }

    private fun copyFileToInternal(uri: Uri, fileName: String): File? {
        return try {
            val destinationFile = File(context.filesDir, "library/$fileName")
            destinationFile.parentFile?.mkdirs()
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            destinationFile
        } catch (e: Exception) {
            null
        }
    }

    private fun generatePdfThumbnail(file: File): String? {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val page = renderer.openPage(0)
            
            val bitmap = Bitmap.createBitmap(page.width / 4, page.height / 4, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            val thumbFile = File(context.cacheDir, "thumbs/${file.nameWithoutExtension}.jpg")
            thumbFile.parentFile?.mkdirs()
            FileOutputStream(thumbFile).use { 
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) 
            }
            
            page.close()
            renderer.close()
            pfd.close()
            thumbFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun extractCbzCover(file: File): String? {
        return try {
            val zipInput = ZipInputStream(file.inputStream())
            var entry = zipInput.nextEntry
            var thumbFile: File? = null

            while (entry != null) {
                if (!entry.isDirectory && isImageFile(entry.name)) {
                    val bitmap = BitmapFactory.decodeStream(zipInput)
                    if (bitmap != null) {
                        thumbFile = File(context.cacheDir, "thumbs/${file.nameWithoutExtension}.jpg")
                        thumbFile.parentFile?.mkdirs()
                        FileOutputStream(thumbFile).use { 
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) 
                        }
                        break
                    }
                }
                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
            zipInput.close()
            thumbFile?.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun isImageFile(name: String): Boolean {
        val low = name.lowercase()
        return low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png") || low.endsWith(".webp")
    }

    private data class EpubMetadata(val title: String?, val author: String?, val coverPath: String?)

    private fun extractEpubMetadata(file: File): EpubMetadata {
        var title: String? = null
        var author: String? = null
        var coverPath: String? = null

        try {
            java.util.zip.ZipFile(file).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml") ?: return EpubMetadata(null, null, null)
                val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
                
                val rootFileRegex = """<rootfile[^>]+full-path="([^"]+)"""".toRegex()
                val opfPath = rootFileRegex.find(containerXml)?.groupValues?.get(1) ?: return EpubMetadata(null, null, null)
                
                val opfEntry = zip.getEntry(opfPath) ?: return EpubMetadata(null, null, null)
                val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()
                
                val titleRegex = """<dc:title[^>]*>(.*?)</dc:title>""".toRegex()
                title = titleRegex.find(opfXml)?.groupValues?.get(1)
                
                val creatorRegex = """<dc:creator[^>]*>(.*?)</dc:creator>""".toRegex()
                author = creatorRegex.find(opfXml)?.groupValues?.get(1)
                
                val coverMetaRegex = """<meta[^>]+name="cover"[^>]+content="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                val coverIdMatch = coverMetaRegex.find(opfXml)
                var coverHref: String? = null
                
                if (coverIdMatch != null) {
                    val coverId = coverIdMatch.groupValues[1]
                    val itemRegex = """<item[^>]+>""".toRegex()
                    for (match in itemRegex.findAll(opfXml)) {
                        val itemStr = match.value
                        if (itemStr.contains("""id="$coverId"""") || itemStr.contains("""id='$coverId'""")) {
                            val hrefRegex = """href="([^"]+)"""".toRegex()
                            coverHref = hrefRegex.find(itemStr)?.groupValues?.get(1)
                            break
                        }
                    }
                }
                
                if (coverHref == null) {
                    // Fallback 1: properties="cover-image"
                    val itemRegex = """<item[^>]+>""".toRegex()
                    for (match in itemRegex.findAll(opfXml)) {
                        val itemStr = match.value
                        if (itemStr.contains("""properties="cover-image"""")) {
                            val hrefRegex = """href="([^"]+)"""".toRegex()
                            coverHref = hrefRegex.find(itemStr)?.groupValues?.get(1)
                            break
                        }
                    }
                }
                
                if (coverHref == null) {
                    // Fallback 2: Any item that looks like a cover image
                    val itemRegex = """<item[^>]+>""".toRegex()
                    for (match in itemRegex.findAll(opfXml)) {
                        val itemStr = match.value
                        if (itemStr.lowercase().contains("cover") && itemStr.contains("image/")) {
                            val hrefRegex = """href="([^"]+)"""".toRegex()
                            coverHref = hrefRegex.find(itemStr)?.groupValues?.get(1)
                            break
                        }
                    }
                }
                
                if (coverHref != null) {
                    val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
                    val decodedHref = android.net.Uri.decode(coverHref)
                    val fullCoverPath = opfDir + decodedHref
                    
                    val coverEntry = zip.getEntry(fullCoverPath)
                    if (coverEntry != null) {
                        val bitmap = BitmapFactory.decodeStream(zip.getInputStream(coverEntry))
                        if (bitmap != null) {
                            val thumbFile = File(context.cacheDir, "thumbs/${file.nameWithoutExtension}.jpg")
                            thumbFile.parentFile?.mkdirs()
                            FileOutputStream(thumbFile).use { 
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) 
                            }
                            coverPath = thumbFile.absolutePath
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MetadataExtractor", "Error extracting EPUB metadata", e)
        }
        
        return EpubMetadata(title, author, coverPath)
    }
}
