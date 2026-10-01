package com.example.util

import android.content.Context
import android.util.Log
import com.example.model.PdfPageItem
import com.example.model.PdfSecurityConfig
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.coroutineContext

class PdfPasswordRequiredException(message: String, cause: Throwable? = null) : IOException(message, cause)
class PdfWrongPasswordException(message: String, cause: Throwable? = null) : IOException(message, cause)
class PdfInvalidDocumentException(message: String, cause: Throwable? = null) : IOException(message, cause)
class PdfMergeConsistencyException(message: String) : IOException(message)
class PdfSecurityException(message: String, cause: Throwable? = null) : IOException(message, cause)
class PdfCompatibilityModeRequiredException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class PdfVerificationResult(
    val pageCount: Int,
    val isEncrypted: Boolean,
    val encryptionAlgorithm: String?,
    val encryptionKeyLength: Int?,
    val userPasswordAccepted: Boolean,
    val ownerPasswordAccepted: Boolean,
    val restrictedPrinting: Boolean,
    val restrictedModifying: Boolean,
    val restrictedCopying: Boolean,
    val restrictedAnnotations: Boolean
)

data class PdfMergeOutput(
    val file: File,
    val verification: PdfVerificationResult
)

object PdfMergerEngine {

    private const val TAG = "PdfMergerEngine"
    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        PDFBoxResourceLoader.init(context.applicationContext)
        isInitialized = true
    }

    fun loadDocumentSafely(file: File, password: String? = null): PDDocument {
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            throw PdfInvalidDocumentException("PDF file is missing or empty: ${file.name}")
        }

        return try {
            val document = if (password.isNullOrEmpty()) {
                PDDocument.load(file)
            } else {
                PDDocument.load(file, password)
            }
            if (document.isEncrypted) {
                document.setAllSecurityToBeRemoved(true)
            }
            document
        } catch (e: InvalidPasswordException) {
            if (password.isNullOrEmpty()) {
                throw PdfPasswordRequiredException(
                    "The PDF '${file.name}' requires a password.",
                    e
                )
            }
            throw PdfWrongPasswordException(
                "The password supplied for '${file.name}' is incorrect.",
                e
            )
        } catch (e: PdfPasswordRequiredException) {
            throw e
        } catch (e: PdfWrongPasswordException) {
            throw e
        } catch (e: Exception) {
            throw PdfInvalidDocumentException(
                "The PDF '${file.name}' is malformed, unsupported, or unreadable.",
                e
            )
        }
    }

    fun decryptAndSanitizePdf(file: File, password: String? = null, destination: File): Boolean {
        return try {
            loadDocumentSafely(file, password).use { document ->
                document.setAllSecurityToBeRemoved(true)
                document.save(destination)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not create unlocked working copy for ${file.name}: ${e.message}")
            destination.delete()
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
    ): Result<PdfMergeOutput> = withContext(Dispatchers.IO) {
        if (orderedPages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No pages selected for merging"))
        }

        init(context)

        val outputDir = File(context.filesDir, "merged_pdfs").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseName = customOutputName
            ?.trim()
            ?.removeSuffix(".pdf")
            ?.takeIf { it.isNotBlank() }
            ?: "Merged_Document_$timestamp"
        val sanitizedName = sanitizeOutputFileName(baseName) + ".pdf"
        val outputFile = uniqueOutputFile(outputDir, sanitizedName)

        val openedSourceDocs = mutableMapOf<String, PDDocument>()
        var mergedDoc: PDDocument? = null

        try {
            validateSecurityRequest(securityConfig)
            onProgress(0.05f, "Preparing documents for merge...")

            val requiredDocumentIds = orderedPages.map { it.documentId }.toSet()
            for (documentId in requiredDocumentIds) {
                coroutineContext.ensureActive()
                val file = sourceFilesMap[documentId]
                    ?: throw PdfMergeConsistencyException("Source file is missing for document $documentId")
                if (!file.exists()) {
                    throw PdfMergeConsistencyException("Source file no longer exists: ${file.name}")
                }
                val sourceDocument = loadDocumentSafely(file, passwordsMap[documentId])
                try {
                    validateSourceFidelity(sourceDocument, file.name)
                    openedSourceDocs[documentId] = sourceDocument
                } catch (e: Exception) {
                    runCatching { sourceDocument.close() }
                    throw e
                }
            }

            mergedDoc = PDDocument()
            val totalPages = orderedPages.size

            for ((index, pageItem) in orderedPages.withIndex()) {
                coroutineContext.ensureActive()
                val sourceDoc = openedSourceDocs[pageItem.documentId]
                    ?: throw PdfMergeConsistencyException(
                        "Source document not loaded for ${pageItem.documentName}"
                    )

                if (pageItem.pageIndex !in 0 until sourceDoc.numberOfPages) {
                    throw PdfMergeConsistencyException(
                        "Requested page ${pageItem.pageIndex + 1} is outside '${pageItem.documentName}' " +
                            "(${sourceDoc.numberOfPages} pages)."
                    )
                }

                val originalPage = sourceDoc.getPage(pageItem.pageIndex)
                val importedPage = mergedDoc.importPage(originalPage)

                // PDFBox importPage() copies page-local resources, but intentionally omits
                // resources inherited from the source page tree. Materialize inherited
                // resources on the imported page so fonts/images/operators remain resolvable.
                if (!originalPage.cosObject.containsKey(COSName.RESOURCES) && originalPage.resources != null) {
                    importedPage.resources = originalPage.resources
                }

                val normalizedRotation = ((pageItem.rotationDegrees % 360) + 360) % 360
                if (normalizedRotation != 0) {
                    importedPage.rotation = ((importedPage.rotation + normalizedRotation) % 360 + 360) % 360
                }

                val progress = 0.10f + (0.75f * ((index + 1).toFloat() / totalPages.toFloat()))
                onProgress(progress, "Merging page ${index + 1} of $totalPages...")
            }

            if (mergedDoc.numberOfPages != orderedPages.size) {
                throw PdfMergeConsistencyException(
                    "Merge produced ${mergedDoc.numberOfPages} pages; ${orderedPages.size} were requested."
                )
            }

            applyOutputSecurity(mergedDoc, securityConfig)

            onProgress(0.92f, "Saving combined PDF...")
            mergedDoc.save(outputFile)
            mergedDoc.close()
            mergedDoc = null

            onProgress(0.97f, "Verifying merged PDF...")
            val verification = verifyOutputPdf(
                file = outputFile,
                expectedPageCount = orderedPages.size,
                securityConfig = securityConfig
            )

            onProgress(1.0f, "Merge complete!")
            Result.success(PdfMergeOutput(outputFile, verification))
        } catch (e: CancellationException) {
            outputFile.delete()
            throw e
        } catch (e: PdfPasswordRequiredException) {
            outputFile.delete()
            Result.failure(e)
        } catch (e: PdfWrongPasswordException) {
            outputFile.delete()
            Result.failure(e)
        } catch (e: PdfInvalidDocumentException) {
            outputFile.delete()
            Result.failure(e)
        } catch (e: PdfMergeConsistencyException) {
            outputFile.delete()
            Result.failure(e)
        } catch (e: PdfSecurityException) {
            outputFile.delete()
            Result.failure(e)
        } catch (e: PdfCompatibilityModeRequiredException) {
            outputFile.delete()
            Result.failure(e)
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(
                PdfCompatibilityModeRequiredException(
                    "Vector PDF merge failed. Raster fallback is disabled because it can destroy searchable text, links, forms, annotations, and vector content.",
                    e
                )
            )
        } finally {
            try {
                mergedDoc?.close()
            } catch (_: Exception) {
            }
            openedSourceDocs.values.forEach { document ->
                try {
                    document.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun verifyOutputPdf(
        file: File,
        expectedPageCount: Int,
        securityConfig: PdfSecurityConfig
    ): PdfVerificationResult {
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            throw PdfMergeConsistencyException("Merged PDF was not written successfully.")
        }

        val openPassword = if (securityConfig.isEnabled) securityConfig.userPassword else ""
        val document = try {
            if (openPassword.isEmpty()) PDDocument.load(file) else PDDocument.load(file, openPassword)
        } catch (e: InvalidPasswordException) {
            throw PdfSecurityException("Merged PDF could not be reopened with the configured user password.", e)
        } catch (e: Exception) {
            throw PdfMergeConsistencyException("Merged PDF could not be reopened for verification: ${e.message}")
        }

        var isEncrypted = false
        var encryptionAlgorithm: String? = null
        var encryptionKeyLength: Int? = null
        var restrictedPrinting = false
        var restrictedModifying = false
        var restrictedCopying = false
        var restrictedAnnotations = false

        document.use { verified ->
            if (verified.numberOfPages != expectedPageCount) {
                throw PdfMergeConsistencyException(
                    "Merged PDF verification found ${verified.numberOfPages} pages; expected $expectedPageCount."
                )
            }

            isEncrypted = verified.isEncrypted
            if (securityConfig.isEnabled && !isEncrypted) {
                throw PdfSecurityException("Protection was requested, but the saved PDF is not encrypted.")
            }
            if (!securityConfig.isEnabled && isEncrypted) {
                throw PdfSecurityException("Protection was not requested, but the saved PDF is encrypted.")
            }

            if (securityConfig.isEnabled) {
                val encryption = verified.encryption
                    ?: throw PdfSecurityException("Encrypted output has no readable encryption dictionary.")
                val cryptMethod = encryption.stdCryptFilterDictionary?.cryptFilterMethod
                if (encryption.filter != "Standard" ||
                    encryption.version != 4 ||
                    encryption.revision != 4 ||
                    encryption.length != 128 ||
                    cryptMethod != COSName.AESV2
                ) {
                    throw PdfSecurityException(
                        "Protected output is not the required Standard AES-128 (AESV2) profile."
                    )
                }
                encryptionAlgorithm = "AES-128"
                encryptionKeyLength = encryption.length

                val permission = verified.currentAccessPermission
                restrictedPrinting = !permission.canPrint()
                restrictedModifying = !permission.canModify()
                restrictedCopying = !permission.canExtractContent()
                restrictedAnnotations = !permission.canModifyAnnotations()

                if (restrictedPrinting != securityConfig.restrictPrinting ||
                    restrictedModifying != securityConfig.restrictModifying ||
                    restrictedCopying != securityConfig.restrictCopyingText ||
                    restrictedAnnotations != securityConfig.restrictAddingAnnotations
                ) {
                    throw PdfSecurityException("Saved PDF permission flags do not match the requested restrictions.")
                }
            }
        }

        val userPasswordAccepted = securityConfig.isEnabled && securityConfig.userPassword.isNotBlank()
        val ownerPasswordAccepted = if (securityConfig.isEnabled && securityConfig.ownerPassword.isNotBlank()) {
            try {
                PDDocument.load(file, securityConfig.ownerPassword).use { ownerDoc ->
                    ownerDoc.currentAccessPermission.isOwnerPermission
                }
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        if (securityConfig.ownerPassword.isNotBlank() && !ownerPasswordAccepted) {
            throw PdfSecurityException("Configured owner password could not be verified against the saved PDF.")
        }

        return PdfVerificationResult(
            pageCount = expectedPageCount,
            isEncrypted = isEncrypted,
            encryptionAlgorithm = encryptionAlgorithm,
            encryptionKeyLength = encryptionKeyLength,
            userPasswordAccepted = userPasswordAccepted,
            ownerPasswordAccepted = ownerPasswordAccepted,
            restrictedPrinting = restrictedPrinting,
            restrictedModifying = restrictedModifying,
            restrictedCopying = restrictedCopying,
            restrictedAnnotations = restrictedAnnotations
        )
    }

    private fun validateSourceFidelity(document: PDDocument, displayName: String) {
        val acroForm = document.documentCatalog.acroForm
        if (acroForm != null && (acroForm.hasXFA() || acroForm.fields.isNotEmpty())) {
            throw PdfCompatibilityModeRequiredException(
                "The PDF '$displayName' contains interactive form/signature structure. " +
                    "This page-level merge path refuses to detach or invalidate document-level fields silently."
            )
        }
    }

    private fun validateSecurityRequest(config: PdfSecurityConfig) {
        if (!config.isEnabled) return
        if (!config.isPasswordConfigured) {
            throw PdfSecurityException("PDF protection is enabled, but no user or owner password is configured.")
        }
        if (config.userPassword.isNotBlank() &&
            config.ownerPassword.isNotBlank() &&
            config.userPassword == config.ownerPassword
        ) {
            throw PdfSecurityException("User and owner passwords must be different.")
        }
        if (config.encryptionKeyLength != 128) {
            throw PdfSecurityException("Unsupported encryption key length: ${config.encryptionKeyLength}")
        }
    }

    private fun applyOutputSecurity(document: PDDocument, config: PdfSecurityConfig) {
        if (!config.isEnabled) {
            document.setAllSecurityToBeRemoved(true)
            return
        }

        val accessPermission = AccessPermission().apply {
            setCanPrint(!config.restrictPrinting)
            setCanModify(!config.restrictModifying)
            setCanExtractContent(!config.restrictCopyingText)
            setCanModifyAnnotations(!config.restrictAddingAnnotations)
            setCanAssembleDocument(!config.restrictModifying)
            setCanFillInForm(!config.restrictModifying)
        }

        val ownerPassword = config.ownerPassword.ifBlank {
            "internal-owner-${UUID.randomUUID()}-${UUID.randomUUID()}"
        }

        try {
            val policy = StandardProtectionPolicy(ownerPassword, config.userPassword, accessPermission).apply {
                encryptionKeyLength = config.encryptionKeyLength
                isPreferAES = true
            }
            document.protect(policy)
        } catch (e: Exception) {
            throw PdfSecurityException("Failed to apply requested PDF protection.", e)
        }
    }

    private fun sanitizeOutputFileName(name: String): String {
        val withoutControls = name
            .replace(Regex("[\\p{Cc}\\p{Cf}]"), "")
            .replace("..", "_")
            .replace('/', '_')
            .replace('\\', '_')
        return withoutControls
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('.', '_', ' ')
            .take(120)
            .ifBlank { "Merged_Document" }
    }

    private fun uniqueOutputFile(directory: File, requestedName: String): File {
        val first = File(directory, requestedName)
        if (!first.exists()) return first

        val stem = requestedName.removeSuffix(".pdf")
        var counter = 2
        while (true) {
            val candidate = File(directory, "$stem ($counter).pdf")
            if (!candidate.exists()) return candidate
            counter++
        }
    }
}
