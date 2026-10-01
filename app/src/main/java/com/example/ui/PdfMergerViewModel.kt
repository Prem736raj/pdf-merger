package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.MergeState
import com.example.model.PdfDocumentItem
import com.example.model.PdfPageItem
import com.example.model.PdfSecurityConfig
import com.example.util.FileUtil
import com.example.util.PdfMergerEngine
import com.example.util.PdfPasswordRequiredException
import com.example.util.PdfWrongPasswordException
import com.example.util.PdfSaveManager
import com.example.util.PdfThumbnailHelper
import com.example.util.SamplePdfGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.Context
import java.util.UUID

enum class AppThemeMode(val title: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light")
}

enum class AppTab(val title: String) {
    FILES("1. Files"),
    PAGES("2. Reorder Pages"),
    SECURITY("3. Security & Export")
}

data class PdfMergerUiState(
    val documents: List<PdfDocumentItem> = emptyList(),
    val pages: List<PdfPageItem> = emptyList(),
    val currentTab: AppTab = AppTab.FILES,
    val securityConfig: PdfSecurityConfig = PdfSecurityConfig(),
    val outputFileName: String = "Merged_Document",
    val mergeState: MergeState = MergeState.Idle,
    val isProcessing: Boolean = false,
    val processingMessage: String = "",
    val previewPage: PdfPageItem? = null,
    val reorderDialogPage: PdfPageItem? = null,
    val userNotice: String? = null,
    val lastMergedFile: File? = null,
    val lastSavedDestinationUri: Uri? = null,
    val lastSavedPathDisplay: String? = null,
    val saveDestinationDisplayName: String = "Downloads / PDF_Merger",
    val isAutoSaveDirectly: Boolean = true,
    val triggerSaveDialog: Boolean = false,
    val lockedDocumentPrompt: PdfDocumentItem? = null,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val cacheSizeBytes: Long = 0L
)

private data class SaveOutcome(
    val destUri: Uri?,
    val destDisplay: String?,
    val shouldTriggerPicker: Boolean,
    val notice: String
)

class PdfMergerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PdfMergerUiState())
    val uiState: StateFlow<PdfMergerUiState> = _uiState.asStateFlow()
    private val thumbnailJobs = mutableMapOf<String, Job>()
    private var mergeJob: Job? = null

    private val documentColorPalette = listOf(
        Color(0xFF4F46E5), // Indigo
        Color(0xFF0D9488), // Teal
        Color(0xFFD97706), // Amber
        Color(0xFFE11D48), // Rose
        Color(0xFF7C3AED), // Violet
        Color(0xFF2563EB), // Blue
        Color(0xFF059669), // Emerald
        Color(0xFFEA580C)  // Orange
    )

    init {
        // App starts clean without preloaded sample PDFs as requested
        PdfMergerEngine.init(application)
        refreshSaveDestination()

        val prefs = application.getSharedPreferences("pdf_merger_settings_prefs", Context.MODE_PRIVATE)
        val savedTheme = prefs.getString("key_theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val mode = try { AppThemeMode.valueOf(savedTheme) } catch (e: Exception) { AppThemeMode.SYSTEM }
        _uiState.update { it.copy(themeMode = mode) }
        calculateCacheSize()
    }

    fun setThemeMode(mode: AppThemeMode) {
        val context = getApplication<Application>()
        context.getSharedPreferences("pdf_merger_settings_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("key_theme_mode", mode.name)
            .apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun calculateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val size = FileUtil.ownedCacheSize(context)
            _uiState.update { it.copy(cacheSizeBytes = size) }
        }
    }

    fun clearTempCache() {
        clearAllSessionData("Temporary PDF cache cleared.")
    }

    fun resetSaveDestinationToDownloads() {
        val context = getApplication<Application>()
        PdfSaveManager.resetToDefaultDownloads(context)
        refreshSaveDestination()
        _uiState.update { it.copy(userNotice = "Save location reset to Downloads / PDF_Merger") }
    }

    fun refreshSaveDestination() {
        val context = getApplication<Application>()
        val displayName = PdfSaveManager.getDestinationDisplayName(context)
        val isAuto = PdfSaveManager.isAutoSaveEnabled(context)
        _uiState.update {
            it.copy(
                saveDestinationDisplayName = displayName,
                isAutoSaveDirectly = isAuto
            )
        }
    }

    fun setAutoSaveDirectly(enabled: Boolean) {
        val context = getApplication<Application>()
        PdfSaveManager.setAutoSaveEnabled(context, enabled)
        refreshSaveDestination()
    }

    fun consumeSaveDialogTrigger() {
        _uiState.update { it.copy(triggerSaveDialog = false) }
    }

    fun setTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun dismissUserNotice() {
        _uiState.update { it.copy(userNotice = null) }
    }

    fun dismissPasswordPrompt() {
        _uiState.update { it.copy(lockedDocumentPrompt = null) }
    }

    fun unlockDocumentWithPassword(documentId: String, password: String) {
        viewModelScope.launch {
            val doc = _uiState.value.documents.find { it.id == documentId } ?: return@launch
            val context = getApplication<Application>()
            val sanitizedFile = File(FileUtil.documentSessionDir(context, doc.id), "unlocked.pdf")
            val success = withContext(Dispatchers.IO) {
                PdfMergerEngine.decryptAndSanitizePdf(doc.localFile, password, sanitizedFile)
            }

            if (!success) {
                _uiState.update {
                    it.copy(userNotice = "Incorrect password for ${doc.fileName}. Please try again.")
                }
                return@launch
            }

            try {
                val pageCount = withContext(Dispatchers.IO) { FileUtil.getPdfPageCount(sanitizedFile) }
                val pages = (0 until pageCount).map { pageIndex ->
                    PdfPageItem(
                        id = "${doc.id}_p$pageIndex",
                        documentId = doc.id,
                        documentName = doc.fileName,
                        pageIndex = pageIndex,
                        accentColor = doc.accentColor
                    )
                }

                _uiState.update { current ->
                    current.copy(
                        documents = current.documents.map {
                            if (it.id == documentId) {
                                it.copy(
                                    localFile = sanitizedFile,
                                    fileSizeBytes = sanitizedFile.length(),
                                    pageCount = pageCount,
                                    isEncrypted = true,
                                    isLocked = false
                                )
                            } else it
                        },
                        pages = current.pages.filterNot { it.documentId == documentId } + pages,
                        lockedDocumentPrompt = null,
                        userNotice = "Unlocked ${doc.fileName}. Password was not retained."
                    )
                }
                calculateCacheSize()
            } catch (e: Exception) {
                sanitizedFile.delete()
                _uiState.update {
                    it.copy(userNotice = "Could not validate ${doc.fileName}: ${e.message}")
                }
            }
        }
    }

    fun setPreviewPage(page: PdfPageItem?) {
        _uiState.update { it.copy(previewPage = page) }
    }

    fun setReorderDialogPage(page: PdfPageItem?) {
        _uiState.update { it.copy(reorderDialogPage = page) }
    }

    fun updateOutputFileName(name: String) {
        _uiState.update { it.copy(outputFileName = name) }
    }

    fun updateSecurityConfig(config: PdfSecurityConfig) {
        _uiState.update { it.copy(securityConfig = config) }
    }

    fun loadSampleDocuments() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingMessage = "Preparing sample documents..."
                )
            }
            try {
                val context = getApplication<Application>()
                val sampleFiles = SamplePdfGenerator.generateStandardSampleDocuments(context)

                val newDocs = mutableListOf<PdfDocumentItem>()
                val newPages = mutableListOf<PdfPageItem>()

                sampleFiles.forEachIndexed { index, file ->
                    val color = documentColorPalette[(_uiState.value.documents.size + index) % documentColorPalette.size]
                    val docId = UUID.randomUUID().toString()
                    val pageCount = FileUtil.getPdfPageCount(file)

                    val docItem = PdfDocumentItem(
                        id = docId,
                        fileName = file.name,
                        fileSizeBytes = file.length(),
                        pageCount = pageCount,
                        sourceUri = null,
                        localFile = file,
                        accentColor = color
                    )
                    newDocs.add(docItem)

                    for (p in 0 until pageCount) {
                        newPages.add(
                            PdfPageItem(
                                id = "${docId}_p$p",
                                documentId = docId,
                                documentName = file.name,
                                pageIndex = p,
                                accentColor = color
                            )
                        )
                    }
                }

                _uiState.update { current ->
                    current.copy(
                        documents = current.documents + newDocs,
                        pages = current.pages + newPages,
                        isProcessing = false,
                        userNotice = "Loaded ${newDocs.size} sample PDF files ready for testing!"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        userNotice = "Failed to load sample documents: ${e.message}"
                    )
                }
            }
        }
    }

    fun addDocumentsFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingMessage = "Importing ${uris.size} PDF document(s)..."
                )
            }

            val context = getApplication<Application>()
            val newDocs = mutableListOf<PdfDocumentItem>()
            val newPages = mutableListOf<PdfPageItem>()
            val failedImports = mutableListOf<String>()
            var firstLockedDocument: PdfDocumentItem? = null
            val uniqueUris = uris.distinct()

            for ((index, uri) in uniqueUris.withIndex()) {
                val documentId = UUID.randomUUID().toString()
                val displayName = FileUtil.getFileNameFromUri(context, uri)
                val color = documentColorPalette[
                    (_uiState.value.documents.size + index) % documentColorPalette.size
                ]

                try {
                    _uiState.update {
                        it.copy(processingMessage = "Importing file ${index + 1} of ${uniqueUris.size}...")
                    }

                    val cachedFile = FileUtil.copyUriToCache(
                        context = context,
                        uri = uri,
                        documentId = documentId,
                        customName = displayName
                    )
                    val pageCount = FileUtil.getPdfPageCount(cachedFile)
                    val document = PdfDocumentItem(
                        id = documentId,
                        fileName = displayName,
                        fileSizeBytes = cachedFile.length(),
                        pageCount = pageCount,
                        sourceUri = uri,
                        localFile = cachedFile,
                        accentColor = color
                    )
                    newDocs.add(document)

                    for (pageIndex in 0 until pageCount) {
                        newPages.add(
                            PdfPageItem(
                                id = "${documentId}_p$pageIndex",
                                documentId = documentId,
                                documentName = displayName,
                                pageIndex = pageIndex,
                                accentColor = color
                            )
                        )
                    }
                } catch (e: PdfPasswordRequiredException) {
                    val sourceFile = FileUtil.documentSessionDir(context, documentId)
                        .listFiles()
                        ?.firstOrNull { it.isFile && it.name.startsWith("source_") }

                    if (sourceFile != null) {
                        val lockedDocument = PdfDocumentItem(
                            id = documentId,
                            fileName = displayName,
                            fileSizeBytes = sourceFile.length(),
                            pageCount = 0,
                            sourceUri = uri,
                            localFile = sourceFile,
                            accentColor = color,
                            isEncrypted = true,
                            isLocked = true
                        )
                        newDocs.add(lockedDocument)
                        if (firstLockedDocument == null) firstLockedDocument = lockedDocument
                    } else {
                        failedImports.add(displayName)
                        runCatching { FileUtil.deleteDocumentSession(context, documentId) }
                    }
                } catch (e: Exception) {
                    failedImports.add(displayName)
                    runCatching { FileUtil.deleteDocumentSession(context, documentId) }
                }
            }

            val notice = buildString {
                append("Imported ${newDocs.count { !it.isLocked }} PDF document(s)")
                if (newPages.isNotEmpty()) append(" with ${newPages.size} pages")
                if (newDocs.any { it.isLocked }) {
                    append(". ${newDocs.count { it.isLocked }} document(s) need a password")
                }
                if (failedImports.isNotEmpty()) {
                    append(". ${failedImports.size} file(s) were rejected as unreadable or invalid")
                }
                append(".")
            }

            _uiState.update { current ->
                current.copy(
                    documents = current.documents + newDocs,
                    pages = current.pages + newPages,
                    isProcessing = false,
                    lockedDocumentPrompt = firstLockedDocument,
                    userNotice = notice
                )
            }
            calculateCacheSize()
        }
    }

    fun requestThumbnail(pageId: String) {
        val page = _uiState.value.pages.firstOrNull { it.id == pageId } ?: return
        val currentThumbnail = page.thumbnailFile
        if (currentThumbnail != null && currentThumbnail.exists() && currentThumbnail.length() > 0L) return
        if (thumbnailJobs[pageId]?.isActive == true) return

        val document = _uiState.value.documents.firstOrNull { it.id == page.documentId } ?: return
        val context = getApplication<Application>()
        thumbnailJobs[pageId] = viewModelScope.launch {
            try {
                val thumbnail = PdfThumbnailHelper.renderPageThumbnail(
                    context = context,
                    pdfFile = document.localFile,
                    pageIndex = page.pageIndex,
                    documentId = page.documentId
                )
                if (thumbnail != null && thumbnail.exists()) {
                    _uiState.update { current ->
                        current.copy(
                            pages = current.pages.map { item ->
                                if (item.id == pageId) item.copy(thumbnailFile = thumbnail) else item
                            }
                        )
                    }
                }
            } finally {
                thumbnailJobs.remove(pageId)
            }
        }
    }

    fun removeDocument(documentId: String) {
        val pageIds = _uiState.value.pages.filter { it.documentId == documentId }.map { it.id }
        pageIds.forEach { pageId -> thumbnailJobs.remove(pageId)?.cancel() }
        val context = getApplication<Application>()
        _uiState.update { current ->
            current.copy(
                documents = current.documents.filterNot { it.id == documentId },
                pages = current.pages.filterNot { it.documentId == documentId },
                lockedDocumentPrompt = current.lockedDocumentPrompt?.takeIf { it.id != documentId },
                userNotice = "Removed document and its temporary files."
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { FileUtil.deleteDocumentSession(context, documentId) }
            PdfThumbnailHelper.clearMemoryCache()
            val size = FileUtil.ownedCacheSize(context)
            _uiState.update { it.copy(cacheSizeBytes = size) }
        }
    }

    fun reorderDocument(fromIndex: Int, toIndex: Int) {
        val currentDocs = _uiState.value.documents.toMutableList()
        if (fromIndex !in currentDocs.indices || toIndex !in currentDocs.indices) return

        val movedDoc = currentDocs.removeAt(fromIndex)
        currentDocs.add(toIndex, movedDoc)

        // Also rearrange pages according to the document ordering
        val reorderedPages = mutableListOf<PdfPageItem>()
        for (doc in currentDocs) {
            val docPages = _uiState.value.pages.filter { it.documentId == doc.id }
            reorderedPages.addAll(docPages)
        }

        _uiState.update { it.copy(documents = currentDocs, pages = reorderedPages) }
    }

    fun reorderPage(fromIndex: Int, toIndex: Int) {
        val currentPages = _uiState.value.pages.toMutableList()
        if (fromIndex !in currentPages.indices || toIndex !in currentPages.indices) return

        val item = currentPages.removeAt(fromIndex)
        currentPages.add(toIndex, item)
        _uiState.update { it.copy(pages = currentPages) }
    }

    fun movePageDelta(pageId: String, delta: Int) {
        val currentPages = _uiState.value.pages.toMutableList()
        val index = currentPages.indexOfFirst { it.id == pageId }
        if (index == -1) return
        val targetIndex = (index + delta).coerceIn(0, currentPages.size - 1)
        if (index != targetIndex) {
            val item = currentPages.removeAt(index)
            currentPages.add(targetIndex, item)
            _uiState.update { it.copy(pages = currentPages) }
        }
    }

    fun movePageToAbsolutePosition(pageId: String, newOneBasedIndex: Int) {
        val currentPages = _uiState.value.pages.toMutableList()
        val index = currentPages.indexOfFirst { it.id == pageId }
        if (index == -1) return
        val targetZeroBased = (newOneBasedIndex - 1).coerceIn(0, currentPages.size - 1)
        if (index != targetZeroBased) {
            val item = currentPages.removeAt(index)
            currentPages.add(targetZeroBased, item)
            _uiState.update { it.copy(pages = currentPages, reorderDialogPage = null) }
        } else {
            _uiState.update { it.copy(reorderDialogPage = null) }
        }
    }

    fun rotatePage(pageId: String, degrees: Int = 90) {
        _uiState.update { current ->
            val updated = current.pages.map { page ->
                if (page.id == pageId) {
                    val normalized = ((page.rotationDegrees + degrees) % 360 + 360) % 360
                    page.copy(rotationDegrees = normalized)
                } else page
            }
            val updatedPreview = if (current.previewPage?.id == pageId) {
                val normalized = ((current.previewPage.rotationDegrees + degrees) % 360 + 360) % 360
                current.previewPage.copy(rotationDegrees = normalized)
            } else current.previewPage

            current.copy(pages = updated, previewPage = updatedPreview)
        }
    }

    fun rotatePageClockwise(pageId: String) {
        rotatePage(pageId, 90)
    }

    fun rotatePageCounterClockwise(pageId: String) {
        rotatePage(pageId, -90)
    }

    fun resetPageRotation(pageId: String) {
        _uiState.update { current ->
            val updated = current.pages.map { page ->
                if (page.id == pageId) page.copy(rotationDegrees = 0) else page
            }
            val updatedPreview = if (current.previewPage?.id == pageId) {
                current.previewPage.copy(rotationDegrees = 0)
            } else current.previewPage
            current.copy(pages = updated, previewPage = updatedPreview, userNotice = "Reset page rotation to 0°")
        }
    }

    fun rotateAllPages(degrees: Int = 90) {
        _uiState.update { current ->
            val updated = current.pages.map { page ->
                val normalized = ((page.rotationDegrees + degrees) % 360 + 360) % 360
                page.copy(rotationDegrees = normalized)
            }
            val dirName = if (degrees > 0) "clockwise" else "counter-clockwise"
            current.copy(pages = updated, userNotice = "Rotated all pages 90° $dirName")
        }
    }

    fun deletePage(pageId: String) {
        thumbnailJobs.remove(pageId)?.cancel()
        _uiState.update { current ->
            val updated = current.pages.filterNot { it.id == pageId }
            val updatedPreview = if (current.previewPage?.id == pageId) null else current.previewPage
            current.copy(pages = updated, previewPage = updatedPreview, userNotice = "Page removed.")
        }
    }

    fun duplicatePage(pageId: String) {
        val currentPages = _uiState.value.pages.toMutableList()
        val index = currentPages.indexOfFirst { it.id == pageId }
        if (index == -1) return
        val original = currentPages[index]
        val duplicated = original.copy(
            id = "${original.documentId}_dup_${UUID.randomUUID().toString().take(4)}"
        )
        currentPages.add(index + 1, duplicated)
        _uiState.update {
            it.copy(pages = currentPages, userNotice = "Duplicated page ${original.displayPageNumber}.")
        }
    }

    fun reversePageOrder() {
        _uiState.update { current ->
            current.copy(pages = current.pages.reversed(), userNotice = "Reversed page order.")
        }
    }

    fun clearAll() {
        clearAllSessionData("Cleared all loaded documents and temporary PDF files.")
    }

    private fun clearAllSessionData(notice: String) {
        thumbnailJobs.values.forEach { it.cancel() }
        thumbnailJobs.clear()
        val context = getApplication<Application>()
        _uiState.update {
            it.copy(
                documents = emptyList(),
                pages = emptyList(),
                securityConfig = PdfSecurityConfig(),
                mergeState = MergeState.Idle,
                previewPage = null,
                reorderDialogPage = null,
                lockedDocumentPrompt = null,
                lastMergedFile = null,
                lastSavedDestinationUri = null,
                lastSavedPathDisplay = null,
                triggerSaveDialog = false,
                userNotice = notice
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { FileUtil.clearOwnedWorkingFiles(context) }
            runCatching { FileUtil.clearPrivateMergedOutputs(context) }
            PdfThumbnailHelper.clearMemoryCache()
            _uiState.update { it.copy(cacheSizeBytes = FileUtil.ownedCacheSize(context)) }
        }
    }

    fun dismissMergeResult() {
        _uiState.update { it.copy(mergeState = MergeState.Idle) }
    }

    fun startMerge() {
        val pagesToMerge = _uiState.value.pages
        if (pagesToMerge.isEmpty()) {
            _uiState.update { it.copy(userNotice = "No pages to merge. Please add at least one PDF.") }
            return
        }
        if (mergeJob?.isActive == true) {
            _uiState.update { it.copy(userNotice = "A merge is already in progress.") }
            return
        }

        mergeJob = viewModelScope.launch {
            var pendingOutput: File? = null
            try {
                _uiState.update {
                    it.copy(mergeState = MergeState.Merging(0.01f, "Initializing merge..."))
                }

                val context = getApplication<Application>()
                val sourceFiles = _uiState.value.documents.associate { it.id to it.localFile }
                val secConfig = _uiState.value.securityConfig

                val result = PdfMergerEngine.mergePdfPages(
                    context = context,
                    orderedPages = pagesToMerge,
                    sourceFilesMap = sourceFiles,
                    securityConfig = secConfig,
                    customOutputName = _uiState.value.outputFileName,
                    onProgress = { progress, step ->
                        _uiState.update { it.copy(mergeState = MergeState.Merging(progress, step)) }
                    }
                )

                result.fold(
                    onSuccess = { mergeOutput ->
                        val outputFile = mergeOutput.file
                        pendingOutput = outputFile
                        val verification = mergeOutput.verification
                        _uiState.update {
                            it.copy(mergeState = MergeState.Merging(0.99f, "Saving merged PDF..."))
                        }

                        val saveResult = PdfSaveManager.saveMergedPdfDirectly(
                            context = context,
                            sourcePdfFile = outputFile,
                            customFileName = _uiState.value.outputFileName
                        )

                        val outcome = when (saveResult) {
                            is PdfSaveManager.SaveResult.Success -> {
                                SaveOutcome(
                                    destUri = saveResult.destinationUri,
                                    destDisplay = saveResult.displayPath,
                                    shouldTriggerPicker = false,
                                    notice = "Successfully merged and downloaded directly to ${saveResult.displayPath}!"
                                )
                            }
                            is PdfSaveManager.SaveResult.RequiresPicker -> {
                                SaveOutcome(null, null, true, "Merge complete! Please choose a save location.")
                            }
                            is PdfSaveManager.SaveResult.Failure -> {
                                SaveOutcome(null, null, true, "Merge complete! ${saveResult.errorMessage}")
                            }
                        }

                        _uiState.update { current ->
                            current.copy(
                                lastMergedFile = outputFile,
                                lastSavedDestinationUri = outcome.destUri,
                                lastSavedPathDisplay = outcome.destDisplay,
                                triggerSaveDialog = outcome.shouldTriggerPicker,
                                mergeState = MergeState.Success(
                                    outputFile = outputFile,
                                    totalPages = verification.pageCount,
                                    fileSizeBytes = outputFile.length(),
                                    isProtected = verification.isEncrypted,
                                    userPasswordSet = verification.userPasswordAccepted,
                                    ownerPasswordSet = verification.ownerPasswordAccepted,
                                    restrictedPrinting = verification.restrictedPrinting,
                                    restrictedModifying = verification.restrictedModifying,
                                    restrictedCopying = verification.restrictedCopying,
                                    restrictedAnnotations = verification.restrictedAnnotations,
                                    savedPathDisplay = outcome.destDisplay
                                ),
                                userNotice = outcome.notice
                            )
                        }
                        pendingOutput = null
                    },
                    onFailure = { error ->
                        val errorMsg = error.localizedMessage ?: "Merge failed"
                        val isPasswordError =
                            error is PdfPasswordRequiredException || error is PdfWrongPasswordException

                        if (isPasswordError) {
                            val lockedDoc = _uiState.value.documents.find { doc ->
                                errorMsg.contains(doc.fileName, ignoreCase = true) ||
                                    errorMsg.contains(doc.localFile.name, ignoreCase = true)
                            } ?: _uiState.value.documents.firstOrNull { it.isLocked }
                                ?: _uiState.value.documents.firstOrNull()

                            _uiState.update {
                                it.copy(
                                    mergeState = MergeState.Idle,
                                    lockedDocumentPrompt = lockedDoc,
                                    userNotice = "Password required to unlock ${lockedDoc?.fileName ?: "document"}."
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    mergeState = MergeState.Error(errorMsg),
                                    userNotice = "Merge failed: $errorMsg"
                                )
                            }
                        }
                    }
                )
            } catch (e: CancellationException) {
                pendingOutput?.delete()
                _uiState.update {
                    it.copy(
                        mergeState = MergeState.Idle,
                        triggerSaveDialog = false,
                        userNotice = "Merge cancelled."
                    )
                }
                throw e
            } finally {
                mergeJob = null
            }
        }
    }

    fun cancelMerge() {
        val activeJob = mergeJob
        if (activeJob?.isActive == true) {
            activeJob.cancel(CancellationException("Merge cancelled by user"))
        }
    }

    fun downloadOrOpenMergedPdf(context: android.content.Context, onRequirePicker: (String) -> Unit) {
        val file = _uiState.value.lastMergedFile
        if (file == null || !file.exists()) {
            _uiState.update { it.copy(userNotice = "No merged PDF available yet. Please click 'Merge & Download' first.") }
            return
        }

        viewModelScope.launch {
            val saveResult = PdfSaveManager.saveMergedPdfDirectly(
                context = context,
                sourcePdfFile = file,
                customFileName = _uiState.value.outputFileName
            )

            when (saveResult) {
                is PdfSaveManager.SaveResult.Success -> {
                    _uiState.update {
                        it.copy(
                            lastSavedDestinationUri = saveResult.destinationUri,
                            lastSavedPathDisplay = saveResult.displayPath,
                            userNotice = "Downloaded directly to ${saveResult.displayPath}!"
                        )
                    }
                    // Also prompt to open
                    PdfSaveManager.openPdf(context, saveResult.destinationUri)
                }
                is PdfSaveManager.SaveResult.RequiresPicker -> {
                    onRequirePicker(file.name)
                }
                is PdfSaveManager.SaveResult.Failure -> {
                    _uiState.update { it.copy(userNotice = "Save error: ${saveResult.errorMessage}") }
                    onRequirePicker(file.name)
                }
            }
        }
    }
}
