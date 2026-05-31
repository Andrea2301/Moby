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
            var genre = "General"

            when (format) {
                PublicationFormat.EPUB -> {
                    val metadata = extractEpubMetadata(internalFile)
                    if (metadata.title != null) title = metadata.title
                    if (metadata.author != null) author = metadata.author
                    coverPath = metadata.coverPath
                    if (metadata.genre != null) genre = metadata.genre
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
                id = fileName,
                title = title,
                author = author,
                format = format,
                coverUrl = coverPath,
                filePath = internalFile.absolutePath,
                genre = genre
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

    private data class EpubMetadata(
        val title: String?, 
        val author: String?, 
        val coverPath: String?,
        val genre: String?
    )

    private fun parseXml(inputStream: InputStream): org.w3c.dom.Document {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(inputStream)
    }

    private fun extractEpubMetadata(file: File): EpubMetadata {
        var title: String? = null
        var author: String? = null
        var coverPath: String? = null
        var genre: String? = null

        try {
            java.util.zip.ZipFile(file).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml") ?: return EpubMetadata(null, null, null, null)
                val containerDoc = zip.getInputStream(containerEntry).use { parseXml(it) }
                
                val rootFileElements = containerDoc.getElementsByTagName("rootfile")
                val rootFileElement = if (rootFileElements.length > 0) {
                    rootFileElements.item(0) as org.w3c.dom.Element
                } else {
                    val rootFiles = containerDoc.getElementsByTagNameNS("*", "rootfile")
                    if (rootFiles.length > 0) rootFiles.item(0) as org.w3c.dom.Element else null
                } ?: return EpubMetadata(null, null, null, null)
                
                val opfPath = rootFileElement.getAttribute("full-path")
                if (opfPath.isNullOrEmpty()) return EpubMetadata(null, null, null, null)
                
                val opfEntry = zip.getEntry(opfPath) ?: return EpubMetadata(null, null, null, null)
                val opfDoc = zip.getInputStream(opfEntry).use { parseXml(it) }
                
                // 1. Extract Title
                var titleElement: org.w3c.dom.Element? = null
                val titleElements = opfDoc.getElementsByTagName("dc:title")
                if (titleElements.length > 0) {
                    titleElement = titleElements.item(0) as org.w3c.dom.Element
                } else {
                    val titles = opfDoc.getElementsByTagNameNS("*", "title")
                    if (titles.length > 0) {
                        titleElement = titles.item(0) as org.w3c.dom.Element
                    }
                }
                title = titleElement?.textContent?.trim()
                
                // 2. Extract Author
                var creatorElement: org.w3c.dom.Element? = null
                val creatorElements = opfDoc.getElementsByTagName("dc:creator")
                if (creatorElements.length > 0) {
                    creatorElement = creatorElements.item(0) as org.w3c.dom.Element
                } else {
                    val creators = opfDoc.getElementsByTagNameNS("*", "creator")
                    if (creators.length > 0) {
                        creatorElement = creators.item(0) as org.w3c.dom.Element
                    }
                }
                author = creatorElement?.textContent?.trim()

                // 3. Extract Subject (Genre)
                var subjectElement: org.w3c.dom.Element? = null
                val subjectElements = opfDoc.getElementsByTagName("dc:subject")
                if (subjectElements.length > 0) {
                    subjectElement = subjectElements.item(0) as org.w3c.dom.Element
                } else {
                    val subjects = opfDoc.getElementsByTagNameNS("*", "subject")
                    if (subjects.length > 0) {
                        subjectElement = subjects.item(0) as org.w3c.dom.Element
                    }
                }
                genre = subjectElement?.textContent?.trim()
                
                // 4. Extract Cover ID from meta tags
                var coverId: String? = null
                val metaElements = opfDoc.getElementsByTagName("meta")
                for (i in 0 until metaElements.length) {
                    val meta = metaElements.item(i) as org.w3c.dom.Element
                    if (meta.getAttribute("name") == "cover") {
                        coverId = meta.getAttribute("content")
                        break
                    }
                }
                if (coverId == null) {
                    val metaNsElements = opfDoc.getElementsByTagNameNS("*", "meta")
                    for (i in 0 until metaNsElements.length) {
                        val meta = metaNsElements.item(i) as org.w3c.dom.Element
                        if (meta.getAttribute("name") == "cover") {
                            coverId = meta.getAttribute("content")
                            break
                        }
                    }
                }
                
                // 5. Gather item tags under manifest
                var coverHref: String? = null
                val itemsList = mutableListOf<org.w3c.dom.Element>()
                val itemElements = opfDoc.getElementsByTagName("item")
                for (i in 0 until itemElements.length) {
                    itemsList.add(itemElements.item(i) as org.w3c.dom.Element)
                }
                val itemNsElements = opfDoc.getElementsByTagNameNS("*", "item")
                for (i in 0 until itemNsElements.length) {
                    itemsList.add(itemNsElements.item(i) as org.w3c.dom.Element)
                }

                // Fallback 1: match coverId
                if (coverId != null) {
                    for (item in itemsList) {
                        if (item.getAttribute("id") == coverId) {
                            coverHref = item.getAttribute("href")
                            break
                        }
                    }
                }

                // Fallback 2: match properties="cover-image"
                if (coverHref == null) {
                    for (item in itemsList) {
                        if (item.getAttribute("properties").contains("cover-image")) {
                            coverHref = item.getAttribute("href")
                            break
                        }
                    }
                }

                // Fallback 3: match ID/href containing "cover" and having image media-type
                if (coverHref == null) {
                    for (item in itemsList) {
                        val id = item.getAttribute("id").lowercase()
                        val href = item.getAttribute("href").lowercase()
                        val mediaType = item.getAttribute("media-type").lowercase()
                        if ((id.contains("cover") || href.contains("cover")) && mediaType.startsWith("image/")) {
                            coverHref = item.getAttribute("href")
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
        
        return EpubMetadata(title, author, coverPath, genre)
    }
}
