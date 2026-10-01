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
import kotlinx.coroutines.Dispatchers
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
    val documentPasswords: Map<String, String> = emptyMap(),
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
            var size = 0L
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isFile) size += file.length()
            }
            _uiState.update { it.copy(cacheSizeBytes = size) }
        }
    }

    fun clearTempCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            var count = 0
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.endsWith(".png") || file.name.startsWith("thumb_") || file.name.startsWith("unlocked_") || file.name.startsWith("sanitized_"))) {
                    if (file.delete()) count++
                }
            }
            calculateCacheSize()
            _uiState.update { it.copy(userNotice = "Temporary cache cleared ($count files removed)") }
        }
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
            val sanitizedFile = File(context.cacheDir, "unlocked_${doc.id}.pdf")
            val success = withContext(Dispatchers.IO) {
                PdfMergerEngine.decryptAndSanitizePdf(doc.localFile, password, sanitizedFile)
            }
            if (success) {
                val updatedDocs = _uiState.value.documents.map {
                    if (it.id == documentId) it.copy(localFile = sanitizedFile, isLocked = false) else it
                }
                val newPasswords = _uiState.value.documentPasswords + (documentId to password)
                _uiState.update {
                    it.copy(
                        documents = updatedDocs,
                        documentPasswords = newPasswords,
                        lockedDocumentPrompt = null,
                        userNotice = "Successfully unlocked ${doc.fileName}!"
                    )
                }
                // Automatically re-trigger merge with the now-unlocked document
                startMerge()
            } else {
                _uiState.update {
                    it.copy(userNotice = "Incorrect password for ${doc.fileName}. Please try again.")
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
                    val pageCount = FileUtil.getPdfPageCount(file)
                    val docId = UUID.randomUUID().toString()

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
                        val thumb = PdfThumbnailHelper.renderPageThumbnail(context, file, p)
                        newPages.add(
                            PdfPageItem(
                                id = "${docId}_p$p",
                                documentId = docId,
                                documentName = file.name,
                                pageIndex = p,
                                thumbnailFile = thumb,
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

            for ((i, uri) in uris.withIndex()) {
                try {
                    _uiState.update {
                        it.copy(processingMessage = "Importing file ${i + 1} of ${uris.size}...")
                    }
                    val cachedFile = FileUtil.copyUriToCache(context, uri)
                    val pageCount = FileUtil.getPdfPageCount(cachedFile)
                    val docId = UUID.randomUUID().toString()
                    val colorIndex = (_uiState.value.documents.size + i) % documentColorPalette.size
                    val color = documentColorPalette[colorIndex]

                    val docItem = PdfDocumentItem(
                        id = docId,
                        fileName = cachedFile.name.substringAfter("_"),
                        fileSizeBytes = cachedFile.length(),
                        pageCount = pageCount,
                        sourceUri = uri,
                        localFile = cachedFile,
                        accentColor = color
                    )
                    newDocs.add(docItem)

                    for (p in 0 until pageCount) {
                        val thumb = PdfThumbnailHelper.renderPageThumbnail(context, cachedFile, p)
                        newPages.add(
                            PdfPageItem(
                                id = "${docId}_p$p",
                                documentId = docId,
                                documentName = docItem.fileName,
                                pageIndex = p,
                                thumbnailFile = thumb,
                                accentColor = color
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _uiState.update { current ->
                current.copy(
                    documents = current.documents + newDocs,
                    pages = current.pages + newPages,
                    isProcessing = false,
                    userNotice = "Successfully imported ${newDocs.size} PDF document(s) with ${newPages.size} pages!"
                )
            }
        }
    }

    fun removeDocument(documentId: String) {
        _uiState.update { current ->
            current.copy(
                documents = current.documents.filterNot { it.id == documentId },
                pages = current.pages.filterNot { it.documentId == documentId },
                userNotice = "Removed document and its pages."
            )
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
        _uiState.update {
            it.copy(
                documents = emptyList(),
                pages = emptyList(),
                mergeState = MergeState.Idle,
                previewPage = null,
                reorderDialogPage = null,
                userNotice = "Cleared all loaded documents and pages."
            )
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

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    mergeState = MergeState.Merging(0.01f, "Initializing merge...")
                )
            }

            val context = getApplication<Application>()
            val sourceFiles = _uiState.value.documents.associate { it.id to it.localFile }
            val secConfig = _uiState.value.securityConfig

            val result = PdfMergerEngine.mergePdfPages(
                context = context,
                orderedPages = pagesToMerge,
                sourceFilesMap = sourceFiles,
                passwordsMap = _uiState.value.documentPasswords,
                securityConfig = secConfig,
                customOutputName = _uiState.value.outputFileName,
                onProgress = { progress, step ->
                    _uiState.update { it.copy(mergeState = MergeState.Merging(progress, step)) }
                }
            )

            result.fold(
                onSuccess = { mergeOutput ->
                    val outputFile = mergeOutput.file
                    val verification = mergeOutput.verification
                    // Attempt direct save to user's chosen folder or Downloads
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
                },
                onFailure = { error ->
                    val errorMsg = error.localizedMessage ?: "Merge failed"
                    val isPasswordError = error is PdfPasswordRequiredException || error is PdfWrongPasswordException

                    if (isPasswordError) {
                        // Find the locked document that needs a password
                        val lockedDoc = _uiState.value.documents.find { doc ->
                            errorMsg.contains(doc.fileName, ignoreCase = true) ||
                                    errorMsg.contains(doc.localFile.name, ignoreCase = true)
                        } ?: _uiState.value.documents.firstOrNull { it.isLocked } ?: _uiState.value.documents.firstOrNull()

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
