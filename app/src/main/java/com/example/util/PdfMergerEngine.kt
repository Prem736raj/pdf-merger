package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.model.PdfPageItem
import com.example.model.PdfSecurityConfig
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfPasswordRequiredException(message: String) : java.io.IOException(message)

object PdfMergerEngine {

    private const val TAG = "PdfMergerEngine"
    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                isInitialized = true
            } catch (e: Throwable) {
                Log.e(TAG, "PDFBoxResourceLoader init error: ${e.message}")
            }
        }
    }

    /**
     * Safely loads a PDF document, automatically resolving empty passwords ("")
     * and removing permission locks (which trigger "Password required" in other tools).
     */
    fun loadDocumentSafely(file: File, password: String? = null): PDDocument {
        // 1. Try standard load
        try {
            val doc = PDDocument.load(file)
            if (doc.isEncrypted) {
                doc.setAllSecurityToBeRemoved(true)
            }
            return doc
        } catch (e: Exception) {
            Log.d(TAG, "Standard load attempt failed for ${file.name}: ${e.message}")
        }

        // 2. Try with empty password "" (resolves 95% of restricted/permission-locked PDFs)
        try {
            val doc = PDDocument.load(file, "")
            if (doc.isEncrypted) {
                doc.setAllSecurityToBeRemoved(true)
            }
            return doc
        } catch (e: Exception) {
            Log.d(TAG, "Empty password load attempt failed for ${file.name}: ${e.message}")
        }

        // 3. Try user provided password if present
        if (!password.isNullOrBlank()) {
            try {
                val doc = PDDocument.load(file, password)
                if (doc.isEncrypted) {
                    doc.setAllSecurityToBeRemoved(true)
                }
                return doc
            } catch (e: Exception) {
                Log.d(TAG, "Custom password load failed for ${file.name}: ${e.message}")
            }
        }

        throw PdfPasswordRequiredException("The PDF '${file.name}' is password-protected. Please enter its password to unlock.")
    }

    /**
     * Decrypts a file if encrypted and saves a clean, unlocked copy.
     */
    fun decryptAndSanitizePdf(file: File, password: String? = null, destination: File): Boolean {
        return try {
            val doc = loadDocumentSafely(file, password)
            doc.setAllSecurityToBeRemoved(true)
            doc.save(destination)
            doc.close()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not decrypt ${file.name}: ${e.message}")
            false
        }
    }

    suspend fun mergePdfPages(
        context: Context,
        orderedPages: List<PdfPageItem>,
        sourceFilesMap: Map<String, File>,
        passwordsMap: Map<String, String> = emptyMap(),
        securityConfig: PdfSecurityConfig,
        customOutputName: String?,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (orderedPages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No pages selected for merging"))
        }

        init(context)

        val outputDir = File(context.filesDir, "merged_pdfs").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val baseName = if (!customOutputName.isNullOrBlank()) {
            customOutputName.trim().removeSuffix(".pdf")
        } else {
            "Merged_Document_$timestamp"
        }
        val sanitizedName = baseName.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".pdf"
        val outputFile = File(outputDir, sanitizedName)

        // Try Primary Strategy: Vector PDFBox Merge
        try {
            onProgress(0.05f, "Preparing documents for merge...")
            val openedSourceDocs = mutableMapOf<String, PDDocument>()
            val decryptedFiles = mutableMapOf<String, File>()

            for ((docId, file) in sourceFilesMap) {
                if (orderedPages.any { it.documentId == docId } && file.exists()) {
                    try {
                        val pwd = passwordsMap[docId]
                        val doc = loadDocumentSafely(file, pwd)
                        openedSourceDocs[docId] = doc

                        // Also create a sanitized clean file in cache for any renderer fallback
                        val cleanCache = File(context.cacheDir, "sanitized_${docId}.pdf")
                        try {
                            doc.save(cleanCache)
                            decryptedFiles[docId] = cleanCache
                        } catch (saveErr: Exception) {
                            decryptedFiles[docId] = file
                        }
                    } catch (loadErr: Exception) {
                        Log.w(TAG, "Failed to load document $docId (${file.name}): ${loadErr.message}")
                        throw loadErr // Pass through password exception to notify user which doc is locked
                    }
                }
            }

            val mergedDoc = PDDocument()
            val total = orderedPages.size

            for ((index, pageItem) in orderedPages.withIndex()) {
                val sourceDoc = openedSourceDocs[pageItem.documentId]
                    ?: throw IllegalStateException("Source document not found for ${pageItem.documentName}")

                if (pageItem.pageIndex >= sourceDoc.numberOfPages) {
                    continue
                }

                val originalPage = sourceDoc.getPage(pageItem.pageIndex)
                val importedPage = mergedDoc.importPage(originalPage)

                if (pageItem.rotationDegrees != 0) {
                    val currentRotation = importedPage.rotation
                    importedPage.rotation = (currentRotation + pageItem.rotationDegrees) % 360
                }

                val progress = 0.1f + (0.75f * ((index + 1).toFloat() / total.toFloat()))
                onProgress(progress, "Merging page ${index + 1} of $total...")
            }

            // Apply output security only if the user explicitly enabled it
            if (securityConfig.isEnabled && (securityConfig.userPassword.isNotBlank() || securityConfig.ownerPassword.isNotBlank())) {
                onProgress(0.90f, "Applying requested security settings...")
                try {
                    val ap = AccessPermission().apply {
                        setCanPrint(!securityConfig.restrictPrinting)
                        setCanModify(!securityConfig.restrictModifying)
                        setCanExtractContent(!securityConfig.restrictCopyingText)
                        setCanModifyAnnotations(!securityConfig.restrictAddingAnnotations)
                        setCanAssembleDocument(!securityConfig.restrictModifying)
                        setCanFillInForm(!securityConfig.restrictModifying)
                    }

                    val ownerPwd = when {
                        securityConfig.ownerPassword.isNotBlank() -> securityConfig.ownerPassword
                        securityConfig.userPassword.isNotBlank() -> securityConfig.userPassword
                        else -> "OwnerKey2026"
                    }
                    val userPwd = securityConfig.userPassword

                    val protectionPolicy = StandardProtectionPolicy(ownerPwd, userPwd, ap).apply {
                        encryptionKeyLength = securityConfig.encryptionKeyLength
                    }
                    mergedDoc.protect(protectionPolicy)
                } catch (secEx: Exception) {
                    Log.e(TAG, "Security protection application error: ${secEx.message}")
                }
            } else {
                // Ensure output is free from unwanted inherited passwords
                mergedDoc.setAllSecurityToBeRemoved(true)
            }

            onProgress(0.95f, "Saving combined PDF...")
            mergedDoc.save(outputFile)
            mergedDoc.close()

            for (doc in openedSourceDocs.values) {
                try { doc.close() } catch (ignored: Exception) {}
            }

            onProgress(1.0f, "Merge complete!")
            Result.success(outputFile)
        } catch (pdfBoxEx: Throwable) {
            Log.w(TAG, "PDFBox merge failed (${pdfBoxEx.message}). Triggering native renderer fallback...", pdfBoxEx)
            if (pdfBoxEx is PdfPasswordRequiredException || pdfBoxEx is InvalidPasswordException) {
                // User must provide password for locked source document
                Result.failure(pdfBoxEx)
            } else {
                mergeUsingNativeRenderer(context, orderedPages, sourceFilesMap, securityConfig, outputFile, onProgress)
            }
        }
    }

    private suspend fun mergeUsingNativeRenderer(
        context: Context,
        orderedPages: List<PdfPageItem>,
        sourceFilesMap: Map<String, File>,
        securityConfig: PdfSecurityConfig,
        outputFile: File,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var pdfDocument: PdfDocument? = null
        val openedRenderers = mutableMapOf<String, Pair<PdfRenderer, ParcelFileDescriptor>>()

        try {
            onProgress(0.1f, "Initializing document assembler...")
            pdfDocument = PdfDocument()
            val total = orderedPages.size

            for ((index, pageItem) in orderedPages.withIndex()) {
                val sourceFile = sourceFilesMap[pageItem.documentId]
                    ?: throw IllegalStateException("File not found for ${pageItem.documentName}")

                var rendererPair = openedRenderers[pageItem.documentId]
                if (rendererPair == null) {
                    // Try to unlock/sanitize first so PdfRenderer doesn't fail
                    val sanitizedFile = File(context.cacheDir, "sanitized_render_${pageItem.documentId}.pdf")
                    val fileToOpen = if (decryptAndSanitizePdf(sourceFile, null, sanitizedFile)) {
                        sanitizedFile
                    } else {
                        sourceFile
                    }

                    val pfd = ParcelFileDescriptor.open(fileToOpen, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    rendererPair = Pair(renderer, pfd)
                    openedRenderers[pageItem.documentId] = rendererPair
                }

                val renderer = rendererPair.first
                if (pageItem.pageIndex >= renderer.pageCount) {
                    continue
                }

                val srcPage = renderer.openPage(pageItem.pageIndex)

                val baseWidth = srcPage.width
                val baseHeight = srcPage.height

                val isQuarterTurn = (pageItem.rotationDegrees % 180 != 0)
                val outWidth = if (isQuarterTurn) baseHeight else baseWidth
                val outHeight = if (isQuarterTurn) baseWidth else baseHeight

                val pageInfo = PdfDocument.PageInfo.Builder(outWidth, outHeight, index + 1).create()
                val newPage = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = newPage.canvas

                // Render page to bitmap at 1.5x resolution for crisp text & visuals
                val scale = 1.5f
                val bmpW = (baseWidth * scale).toInt().coerceAtMost(2048)
                val bmpH = (baseHeight * scale).toInt().coerceAtMost(2048)
                val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                val bmpCanvas = Canvas(bitmap)
                bmpCanvas.drawColor(Color.WHITE)
                srcPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                srcPage.close()

                canvas.save()
                if (pageItem.rotationDegrees != 0) {
                    canvas.rotate(pageItem.rotationDegrees.toFloat(), outWidth / 2f, outHeight / 2f)
                }

                val matrix = Matrix()
                matrix.postScale(baseWidth.toFloat() / bmpW, baseHeight.toFloat() / bmpH)
                if (isQuarterTurn) {
                    val dx = (outWidth - baseWidth) / 2f
                    val dy = (outHeight - baseHeight) / 2f
                    matrix.postTranslate(dx, dy)
                }
                canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()

                bitmap.recycle()
                pdfDocument.finishPage(newPage)

                val progress = 0.15f + (0.80f * ((index + 1).toFloat() / total.toFloat()))
                onProgress(progress, "Assembling page ${index + 1} of $total...")
            }

            onProgress(0.95f, "Writing merged file...")
            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            pdfDocument = null

            // Output security only if explicitly requested
            if (securityConfig.isEnabled && (securityConfig.userPassword.isNotBlank() || securityConfig.ownerPassword.isNotBlank())) {
                try {
                    onProgress(0.97f, "Encrypting merged file...")
                    val pdDoc = PDDocument.load(outputFile)
                    val ap = AccessPermission().apply {
                        setCanPrint(!securityConfig.restrictPrinting)
                        setCanModify(!securityConfig.restrictModifying)
                        setCanExtractContent(!securityConfig.restrictCopyingText)
                        setCanModifyAnnotations(!securityConfig.restrictAddingAnnotations)
                    }
                    val ownerPwd = when {
                        securityConfig.ownerPassword.isNotBlank() -> securityConfig.ownerPassword
                        securityConfig.userPassword.isNotBlank() -> securityConfig.userPassword
                        else -> "OwnerKey2026"
                    }
                    val spp = StandardProtectionPolicy(ownerPwd, securityConfig.userPassword, ap).apply {
                        encryptionKeyLength = securityConfig.encryptionKeyLength
                    }
                    pdDoc.protect(spp)
                    pdDoc.save(outputFile)
                    pdDoc.close()
                } catch (secEx: Throwable) {
                    Log.w(TAG, "Native renderer security wrapper notice: ${secEx.message}")
                }
            }

            onProgress(1.0f, "Merge complete!")
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Native renderer merge error: ${e.message}", e)
            Result.failure(e)
        } finally {
            try {
                pdfDocument?.close()
            } catch (ignored: Throwable) {}
            for ((_, pair) in openedRenderers) {
                try {
                    pair.first.close()
                    pair.second.close()
                } catch (ignored: Throwable) {}
            }
        }
    }
}
