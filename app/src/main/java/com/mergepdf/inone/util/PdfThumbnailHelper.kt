package com.mergepdf.inone.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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

    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(30 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    fun getCachedBitmap(key: String): Bitmap? {
        return memoryCache.get(key)
    }

    fun putCachedBitmap(key: String, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
    }

    suspend fun renderPageThumbnail(
        context: Context,
        pdfFile: File,
        pageIndex: Int,
        targetWidth: Int = 360,
        targetHeight: Int = 500
    ): File? = withContext(Dispatchers.IO) {
        try {
            val thumbDir = File(context.cacheDir, "pdf_thumbnails").apply { mkdirs() }
            val thumbFile = File(thumbDir, "thumb_${pdfFile.nameWithoutExtension}_page_$pageIndex.png")

            if (thumbFile.exists() && thumbFile.length() > 0) {
                return@withContext thumbFile
            }

            var fileToRender = pdfFile
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null

            try {
                pfd = ParcelFileDescriptor.open(fileToRender, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
            } catch (secEx: Exception) {
                // Try sanitizing permission locks / empty passwords
                try {
                    val sanitized = File(context.cacheDir, "thumb_sanitized_${pdfFile.name}")
                    if (PdfMergerEngine.decryptAndSanitizePdf(pdfFile, null, sanitized)) {
                        fileToRender = sanitized
                        pfd = ParcelFileDescriptor.open(fileToRender, ParcelFileDescriptor.MODE_READ_ONLY)
                        renderer = PdfRenderer(pfd)
                    }
                } catch (sanErr: Exception) {
                    Log.w(TAG, "Thumbnail fallback sanitization failed: ${sanErr.message}")
                }
            }

            if (renderer == null || pfd == null) {
                return@withContext null
            }

            if (pageIndex >= renderer.pageCount) {
                renderer.close()
                pfd.close()
                return@withContext null
            }

            val page = renderer.openPage(pageIndex)
            val aspectRatio = page.width.toFloat() / page.height.toFloat()
            val finalWidth: Int
            val finalHeight: Int
            if (aspectRatio > 1.0f) {
                finalWidth = targetWidth
                finalHeight = (targetWidth / aspectRatio).toInt().coerceAtLeast(100)
            } else {
                finalHeight = targetHeight
                finalWidth = (targetHeight * aspectRatio).toInt().coerceAtLeast(100)
            }

            val bitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            page.close()
            renderer.close()
            pfd.close()

            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }

            val cacheKey = "${pdfFile.absolutePath}_$pageIndex"
            memoryCache.put(cacheKey, bitmap)

            thumbFile
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering thumbnail for ${pdfFile.name} page $pageIndex: ${e.message}")
            null
        }
    }

    suspend fun renderHighResPreview(
        pdfFile: File,
        pageIndex: Int,
        maxDimension: Int = 1600
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "highres_${pdfFile.absolutePath}_$pageIndex"
        val cached = memoryCache.get(cacheKey)
        if (cached != null) {
            return@withContext cached
        }

        try {
            var fileToRender = pdfFile
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null

            try {
                pfd = ParcelFileDescriptor.open(fileToRender, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
            } catch (secEx: Exception) {
                val sanitized = File(pdfFile.parentFile, "preview_sanitized_${pdfFile.name}")
                if (PdfMergerEngine.decryptAndSanitizePdf(pdfFile, null, sanitized)) {
                    fileToRender = sanitized
                    pfd = ParcelFileDescriptor.open(fileToRender, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(pfd)
                }
            }

            if (renderer == null || pfd == null) return@withContext null
            if (pageIndex >= renderer.pageCount) {
                renderer.close()
                pfd.close()
                return@withContext null
            }

            val page = renderer.openPage(pageIndex)
            val scale = (maxDimension.toFloat() / maxOf(page.width, page.height).toFloat()).coerceAtMost(2.0f)
            val outW = (page.width * scale).toInt().coerceAtLeast(100)
            val outH = (page.height * scale).toInt().coerceAtLeast(100)

            val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            page.close()
            renderer.close()
            pfd.close()

            memoryCache.put(cacheKey, bitmap)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering high res preview: ${e.message}")
            null
        }
    }
}
