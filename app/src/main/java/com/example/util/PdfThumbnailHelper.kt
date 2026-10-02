package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfThumbnailHelper {
    private const val TAG = "PdfThumbnailHelper"
    private val maxCacheBytes = (Runtime.getRuntime().maxMemory() / 16L)
        .coerceIn(4L * 1024 * 1024, 24L * 1024 * 1024)
        .toInt()

    private val memoryCache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun getCachedBitmap(key: String): Bitmap? = memoryCache.get(key)

    fun putCachedBitmap(key: String, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
    }

    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    suspend fun renderPageThumbnail(
        context: Context,
        pdfFile: File,
        pageIndex: Int,
        documentId: String,
        targetWidth: Int = 360,
        targetHeight: Int = 500
    ): File? = withContext(Dispatchers.IO) {
        val dir = FileUtil.documentThumbnailDir(context, documentId).apply { mkdirs() }
        val thumbnail = File(dir, "page_$pageIndex.png")
        if (thumbnail.exists() && thumbnail.length() > 0L) return@withContext thumbnail

        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val ratio = page.width.toFloat() / page.height.toFloat()
                        val width: Int
                        val height: Int
                        if (ratio > 1f) {
                            width = targetWidth
                            height = (targetWidth / ratio).toInt().coerceAtLeast(100)
                        } else {
                            height = targetHeight
                            width = (targetHeight * ratio).toInt().coerceAtLeast(100)
                        }
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        FileOutputStream(thumbnail).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                        }
                        memoryCache.put("${documentId}_$pageIndex", bitmap)
                    }
                }
            }
            thumbnail
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering thumbnail for page $pageIndex: ${e.message}")
            thumbnail.delete()
            null
        }
    }

    suspend fun renderHighResPreview(
        pdfFile: File,
        pageIndex: Int,
        maxDimension: Int = 1600
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = "highres_${pdfFile.absolutePath}_$pageIndex"
        memoryCache.get(key)?.let { return@withContext it }
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val scale = (maxDimension.toFloat() / maxOf(page.width, page.height).toFloat())
                            .coerceAtMost(2.0f)
                        val width = (page.width * scale).toInt().coerceAtLeast(100)
                        val height = (page.height * scale).toInt().coerceAtLeast(100)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        memoryCache.put(key, bitmap)
                        bitmap
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering high-res preview: ${e.message}")
            null
        }
    }
}
