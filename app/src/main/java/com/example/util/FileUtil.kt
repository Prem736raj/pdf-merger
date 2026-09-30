package com.example.util

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object FileUtil {

    private const val TAG = "FileUtil"

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f)
            else -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024f * 1024f))
        }
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (name == null) {
            name = uri.lastPathSegment
        }
        return name ?: "Document_${UUID.randomUUID().toString().take(6)}.pdf"
    }

    suspend fun copyUriToCache(context: Context, uri: Uri, customName: String? = null): File = withContext(Dispatchers.IO) {
        val fileName = customName ?: getFileNameFromUri(context, uri)
        val cacheFolder = File(context.cacheDir, "imported_pdfs").apply { mkdirs() }
        val uniquePrefix = UUID.randomUUID().toString().take(6)
        val targetFile = File(cacheFolder, "${uniquePrefix}_$fileName")

        context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not open input stream for $uri")

        // Auto-sanitize permission-locked or empty-password PDFs so PdfRenderer & Merger can access without issues
        try {
            PdfMergerEngine.init(context)
            val cleanFile = File(cacheFolder, "unlocked_${uniquePrefix}_$fileName")
            if (PdfMergerEngine.decryptAndSanitizePdf(targetFile, null, cleanFile)) {
                return@withContext cleanFile
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Sanitization check note: ${e.message}")
        }

        targetFile
    }

    fun getPdfPageCount(file: File, password: String? = null): Int {
        // 1. Try native PdfRenderer
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            if (count > 0) return count
        } catch (e: Exception) {
            Log.d(TAG, "PdfRenderer count failed for ${file.name}: ${e.message}")
        }

        // 2. Try PDFBox safe loader
        try {
            PdfMergerEngine.loadDocumentSafely(file, password).use { doc ->
                return doc.numberOfPages
            }
        } catch (e: Exception) {
            Log.d(TAG, "PDFBox count failed for ${file.name}: ${e.message}")
        }

        return 1
    }
}
