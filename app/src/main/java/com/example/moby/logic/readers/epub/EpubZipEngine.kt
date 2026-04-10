package com.example.moby.logic.readers.epub

import android.webkit.MimeTypeMap
import java.io.File
import java.util.zip.ZipFile

object EpubZipEngine {
    private var cachedPath: String? = null
    private var cachedZip: ZipFile? = null

    @Synchronized
    fun readEntry(filePath: String, entryPath: String): ByteArray? {
        return try {
            if (cachedPath != filePath || cachedZip == null) {
                cachedZip?.close()
                cachedZip = ZipFile(File(filePath))
                cachedPath = filePath
            }
            val entry = cachedZip?.getEntry(entryPath) ?: return null
            cachedZip?.getInputStream(entry)?.readBytes()
        } catch (e: Exception) {
            cachedZip?.close()
            cachedZip = null
            cachedPath = null
            null
        }
    }

    @Synchronized
    fun close() {
        cachedZip?.close()
        cachedZip = null
        cachedPath = null
    }

    fun getMimeType(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "css"         -> "text/css"
                "js"          -> "application/javascript"
                "svg"         -> "image/svg+xml"
                "jpg", "jpeg" -> "image/jpeg"
                "png"         -> "image/png"
                "gif"         -> "image/gif"
                "webp"        -> "image/webp"
                "ttf"         -> "font/ttf"
                "otf"         -> "font/otf"
                "woff"        -> "font/woff"
                "woff2"       -> "font/woff2"
                else          -> "application/octet-stream"
            }
    }
}
