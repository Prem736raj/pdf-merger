package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object FileUtil {
    private const val SESSION_ROOT = "pdf_sessions"
    private const val THUMBNAIL_ROOT = "pdf_thumbnails"
    private const val MAX_DISPLAY_NAME_LENGTH = 120

    fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f)
        else -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024f * 1024f))
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1 && cursor.moveToFirst()) name = cursor.getString(index)
                }
            }
        }
        if (name.isNullOrBlank()) name = uri.lastPathSegment
        return sanitizeDisplayName(name ?: "Document_${UUID.randomUUID().toString().take(8)}.pdf")
    }

    fun sanitizeDisplayName(rawName: String): String {
        val stripped = rawName
            .replace(Regex("[\\p{Cc}\\p{Cf}]"), "")
            .replace('/', '_')
            .replace('\\', '_')
            .replace("..", "_")
            .trim()
            .take(MAX_DISPLAY_NAME_LENGTH)
        return stripped.ifBlank { "Document.pdf" }
    }

    fun documentSessionDir(context: Context, documentId: String): File {
        val safeId = documentId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(File(context.cacheDir, SESSION_ROOT), safeId)
    }

    fun documentThumbnailDir(context: Context, documentId: String): File {
        val safeId = documentId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(File(context.cacheDir, THUMBNAIL_ROOT), safeId)
    }

    suspend fun copyUriToCache(
        context: Context,
        uri: Uri,
        documentId: String,
        customName: String? = null
    ): File = withContext(Dispatchers.IO) {
        val displayName = sanitizeDisplayName(customName ?: getFileNameFromUri(context, uri))
        val sessionDir = documentSessionDir(context, documentId).apply { mkdirs() }
        val targetFile = File(sessionDir, "source_$displayName")

        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open the selected PDF.")
            input.use { source ->
                FileOutputStream(targetFile).use { output -> source.copyTo(output) }
            }
            if (targetFile.length() < 5L) {
                throw PdfInvalidDocumentException("The selected file is empty or too small to be a PDF.")
            }
            val header = ByteArray(5)
            targetFile.inputStream().use { source ->
                if (source.read(header) != header.size ||
                    String(header, Charsets.US_ASCII) != "%PDF-"
                ) {
                    throw PdfInvalidDocumentException("The selected file does not contain a valid PDF header.")
                }
            }
            targetFile
        } catch (e: Exception) {
            sessionDir.deleteRecursively()
            throw e
        }
    }

    fun getPdfPageCount(file: File, password: String? = null): Int {
        PdfMergerEngine.loadDocumentSafely(file, password).use { document ->
            val count = document.numberOfPages
            if (count <= 0) throw PdfInvalidDocumentException("The PDF contains no readable pages.")
            return count
        }
    }

    fun ownedCacheSize(context: Context): Long = listOf(
        File(context.cacheDir, SESSION_ROOT),
        File(context.cacheDir, THUMBNAIL_ROOT)
    ).sumOf(::recursiveSize)

    fun deleteDocumentSession(context: Context, documentId: String): Int {
        var removed = 0
        removed += deleteOwnedTree(context.cacheDir, documentSessionDir(context, documentId))
        removed += deleteOwnedTree(context.cacheDir, documentThumbnailDir(context, documentId))
        return removed
    }

    fun clearOwnedWorkingFiles(context: Context): Int {
        var removed = 0
        removed += deleteOwnedTree(context.cacheDir, File(context.cacheDir, SESSION_ROOT))
        removed += deleteOwnedTree(context.cacheDir, File(context.cacheDir, THUMBNAIL_ROOT))
        return removed
    }

    private fun recursiveSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf(::recursiveSize) ?: 0L
    }

    private fun deleteOwnedTree(cacheDir: File, target: File): Int {
        if (!target.exists()) return 0
        val cachePath = cacheDir.canonicalFile.toPath()
        val targetPath = target.canonicalFile.toPath()
        if (!targetPath.startsWith(cachePath) || targetPath == cachePath) {
            throw SecurityException("Refusing to delete outside app-owned cache.")
        }
        val count = target.walkBottomUp().count { it.exists() }
        if (!target.deleteRecursively()) throw IOException("Could not fully remove temporary PDF files.")
        return count
    }
}
