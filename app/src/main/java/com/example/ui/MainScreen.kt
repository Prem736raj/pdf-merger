package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.MergeState
import com.example.ui.components.AppSettingsDialog
import com.example.ui.components.FullscreenPreviewDialog
import com.example.ui.components.MergeProgressDialog
import com.example.ui.components.MergeSuccessDialog
import com.example.ui.components.ReorderPageDialog
import com.example.ui.components.SimplePrivacyItem
import com.example.ui.components.UnlockPasswordDialog
import com.example.ui.components.launchPlayStoreRating
import com.example.ui.screens.BatchDocumentsTab
import com.example.ui.screens.PagesGridTab
import com.example.ui.screens.SecurityTab
import com.example.util.FileUtil
import com.example.util.PdfSaveManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PdfMergerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAboutDialog by remember { mutableStateOf(false) }

    // Download/Save to user storage launcher
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        if (destinationUri != null) {
            val fileToSave = (uiState.mergeState as? MergeState.Success)?.outputFile ?: uiState.lastMergedFile
            if (fileToSave != null && fileToSave.exists()) {
                scope.launch {
                    when (
                        val result = PdfSaveManager.saveMergedPdfToUri(
                            context = context,
                            sourcePdfFile = fileToSave,
                            destinationUri = destinationUri,
                            displayName = "selected location"
                        )
                    ) {
                        is PdfSaveManager.SaveResult.Success -> {
                            Toast.makeText(
                                context,
                                "PDF saved successfully.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is PdfSaveManager.SaveResult.Failure -> {
                            Toast.makeText(
                                context,
                                "Save failed: ${result.errorMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        PdfSaveManager.SaveResult.RequiresPicker -> {
                            Toast.makeText(
                                context,
                                "Save failed. Please choose a location again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }

    // Folder picker launcher for custom save location
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val folderName = treeUri.lastPathSegment?.substringAfterLast(':') ?: "Chosen Folder"
            com.example.util.PdfSaveManager.setCustomFolder(context, treeUri, folderName)
            viewModel.refreshSaveDestination()
            Toast.makeText(context, "Default save folder set to: $folderName", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-launch system save file picker when merge finishes
    LaunchedEffect(uiState.triggerSaveDialog) {
        if (uiState.triggerSaveDialog && uiState.lastMergedFile != null) {
            val fileName = uiState.lastMergedFile?.name ?: "Merged_Document.pdf"
            savePdfLauncher.launch(fileName)
            viewModel.consumeSaveDialogTrigger()
        }
    }

    // PDF Multi-picker launcher for top app bar button
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addDocumentsFromUris(uris)
        }
    }

    // Display user messages via Snackbar
    LaunchedEffect(uiState.userNotice) {
        uiState.userNotice?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserNotice()
        }
    }

    // Play Store UX: Smooth back navigation across tabs
    BackHandler(enabled = uiState.currentTab != AppTab.FILES) {
        when (uiState.currentTab) {
            AppTab.SECURITY -> viewModel.setTab(AppTab.PAGES)
            AppTab.PAGES -> viewModel.setTab(AppTab.FILES)
            AppTab.FILES -> { /* exit app */ }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // App Icon with Blue/Violet Gradient and PDF Document emblem matching Image 3
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF3B82F6), Color(0xFF6366F1), Color(0xFF8B5CF6))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "PDF Merger",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 19.sp
                            )
                            Text(
                                text = "Merge, Organize, Share PDFs Easily",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                },
                actions = {
                    // Direct Download button in TopAppBar if merged file is ready
                    if (uiState.lastMergedFile != null && uiState.lastMergedFile!!.exists()) {
                        IconButton(
                            onClick = {
                                viewModel.downloadOrOpenMergedPdf(context) { filename ->
                                    savePdfLauncher.launch(filename)
                                }
                            },
                            modifier = Modifier.testTag("topbar_download_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = Color(0xFF10B981)
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download Combined PDF",
                                    tint = Color(0xFF10B981)
                                )
                            }
                        }
                    }

                    // Settings / Privacy Gear Button
                    IconButton(
                        onClick = { showAboutDialog = true },
                        modifier = Modifier.testTag("topbar_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings & Preferences",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Redesigned Floating Quick Merge Action Dock (Files and Pages tabs)
                AnimatedVisibility(
                    visible = uiState.currentTab != AppTab.SECURITY && uiState.pages.isNotEmpty(),
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            // Top Row: Clear Status & optional Previous Download Action
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = "${uiState.pages.size} Pages",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (uiState.securityConfig.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (uiState.securityConfig.isEnabled) Icons.Default.Lock else Icons.Default.Security,
                                                contentDescription = null,
                                                tint = if (uiState.securityConfig.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (uiState.securityConfig.isEnabled) "Password" else "Standard",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (uiState.securityConfig.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (uiState.lastMergedFile != null && uiState.lastMergedFile!!.exists()) {
                                    TextButton(
                                        onClick = {
                                            viewModel.downloadOrOpenMergedPdf(context) { filename ->
                                                savePdfLauncher.launch(filename)
                                            }
                                        },
                                        modifier = Modifier.testTag("quick_download_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFF10B981)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Download (${FileUtil.formatFileSize(uiState.lastMergedFile!!.length())})",
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Full-Width Primary Merge & Download Button (never squashes text)
                            Button(
                                onClick = { viewModel.startMerge() },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.securityConfig.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("quick_merge_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.securityConfig.isEnabled) Icons.Default.Lock else Icons.AutoMirrored.Filled.MergeType,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Merge & Download (${uiState.pages.size} Pages)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    // Tab 1: Files
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.FILES,
                        onClick = { viewModel.setTab(AppTab.FILES) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (uiState.documents.isNotEmpty()) {
                                        Badge { Text(uiState.documents.size.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Description, contentDescription = "Files")
                            }
                        },
                        label = { Text("1. Files") },
                        modifier = Modifier.testTag("nav_tab_files")
                    )

                    // Tab 2: Reorder Pages
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.PAGES,
                        onClick = { viewModel.setTab(AppTab.PAGES) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (uiState.pages.isNotEmpty()) {
                                        Badge { Text(uiState.pages.size.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.AutoAwesomeMotion, contentDescription = "Reorder Pages")
                            }
                        },
                        label = { Text("2. Reorder") },
                        modifier = Modifier.testTag("nav_tab_pages")
                    )

                    // Tab 3: Security & Export
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.SECURITY,
                        onClick = { viewModel.setTab(AppTab.SECURITY) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (uiState.securityConfig.isEnabled) {
                                        Badge { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(10.dp)) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Security, contentDescription = "Security & Export")
                            }
                        },
                        label = { Text("3. Security") },
                        modifier = Modifier.testTag("nav_tab_security")
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                AppTab.FILES -> {
                    BatchDocumentsTab(
                        documents = uiState.documents,
                        totalPages = uiState.pages.size,
                        onAddUris = { uris -> viewModel.addDocumentsFromUris(uris) },
                        onMoveDocumentUp = { index -> viewModel.reorderDocument(index, index - 1) },
                        onMoveDocumentDown = { index -> viewModel.reorderDocument(index, index + 1) },
                        onRemoveDocument = { docId -> viewModel.removeDocument(docId) },
                        onClearAll = { viewModel.clearAll() },
                        onNavigateToPages = { viewModel.setTab(AppTab.PAGES) }
                    )
                }

                AppTab.PAGES -> {
                    PagesGridTab(
                        pages = uiState.pages,
                        onReorderPage = { from, to -> viewModel.reorderPage(from, to) },
                        onMovePageDelta = { pageId, delta -> viewModel.movePageDelta(pageId, delta) },
                        onRotatePageClockwise = { pageId -> viewModel.rotatePageClockwise(pageId) },
                        onRotatePageCounterClockwise = { pageId -> viewModel.rotatePageCounterClockwise(pageId) },
                        onResetPageRotation = { pageId -> viewModel.resetPageRotation(pageId) },
                        onRotateAllPages = { degrees -> viewModel.rotateAllPages(degrees) },
                        onReversePages = { viewModel.reversePageOrder() },
                        onDeletePage = { pageId -> viewModel.deletePage(pageId) },
                        onPreviewPage = { page -> viewModel.setPreviewPage(page) },
                        onRequestThumbnail = { pageId -> viewModel.requestThumbnail(pageId) },
                        onOpenReorderDialog = { page -> viewModel.setReorderDialogPage(page) },
                        onNavigateToSecurity = { viewModel.setTab(AppTab.SECURITY) },
                        onNavigateToBatch = { viewModel.setTab(AppTab.FILES) }
                    )
                }

                AppTab.SECURITY -> {
                    SecurityTab(
                        outputFileName = uiState.outputFileName,
                        onOutputFileNameChange = { viewModel.updateOutputFileName(it) },
                        securityConfig = uiState.securityConfig,
                        onSecurityConfigChange = { viewModel.updateSecurityConfig(it) },
                        documents = uiState.documents,
                        pages = uiState.pages,
                        lastMergedFile = uiState.lastMergedFile,
                        onDownloadClicked = {
                            viewModel.downloadOrOpenMergedPdf(context) { filename ->
                                savePdfLauncher.launch(filename)
                            }
                        },
                        onStartMerge = { viewModel.startMerge() }
                    )
                }
            }

            // Loading / thumbnail generation overlay
            if (uiState.isProcessing) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = uiState.processingMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Merge Progress Dialog
    (uiState.mergeState as? MergeState.Merging)?.let { mergingState ->
        MergeProgressDialog(mergeState = mergingState)
    }

    // Merge Success Dialog with Download/Save
    (uiState.mergeState as? MergeState.Success)?.let { successState ->
        MergeSuccessDialog(
            successState = successState,
            onSaveToDevice = {
                val filename = successState.outputFile.name
                savePdfLauncher.launch(filename)
            },
            onDismiss = { viewModel.dismissMergeResult() }
        )
    }

    // Merge Error Dialog
    (uiState.mergeState as? MergeState.Error)?.let { errorState ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMergeResult() },
            title = {
                Text(
                    text = "Merge Issue",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = errorState.message,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.startMerge() }
                ) {
                    Text("Retry Merge")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissMergeResult() }
                ) {
                    Text("Dismiss")
                }
            }
        )
    }

    // Fullscreen Page Preview Zoom Dialog
    uiState.previewPage?.let { previewPage ->
        val sourceFile = uiState.documents.find { it.id == previewPage.documentId }?.localFile
        val overallIndex = uiState.pages.indexOfFirst { it.id == previewPage.id } + 1
        FullscreenPreviewDialog(
            page = previewPage,
            sourceFile = sourceFile,
            overallIndex = overallIndex,
            onRotateClockwise = { viewModel.rotatePageClockwise(previewPage.id) },
            onRotateCounterClockwise = { viewModel.rotatePageCounterClockwise(previewPage.id) },
            onDelete = { viewModel.deletePage(previewPage.id) },
            onDismiss = { viewModel.setPreviewPage(null) }
        )
    }

    // Direct Reorder Jump Dialog
    uiState.reorderDialogPage?.let { page ->
        val currentIndex = uiState.pages.indexOfFirst { it.id == page.id } + 1
        ReorderPageDialog(
            page = page,
            currentIndex = currentIndex,
            totalPages = uiState.pages.size,
            onMoveToPosition = { targetPosition ->
                viewModel.movePageToAbsolutePosition(page.id, targetPosition)
            },
            onDismiss = { viewModel.setReorderDialogPage(null) }
        )
    }

    // Unlock Password Dialog for protected source PDFs
    uiState.lockedDocumentPrompt?.let { lockedDoc ->
        UnlockPasswordDialog(
            document = lockedDoc,
            onUnlock = { password ->
                viewModel.unlockDocumentWithPassword(lockedDoc.id, password)
            },
            onDismiss = { viewModel.dismissPasswordPrompt() }
        )
    }

    // App Settings & Preferences Dialog (Dark/Light mode, Storage, Privacy & Legal)
    if (showAboutDialog) {
        AppSettingsDialog(
            uiState = uiState,
            onSetThemeMode = { mode -> viewModel.setThemeMode(mode) },
            onSetAutoSaveDirectly = { enabled -> viewModel.setAutoSaveDirectly(enabled) },
            onSelectFolderClick = { folderPickerLauncher.launch(null) },
            onResetFolderClick = { viewModel.resetSaveDestinationToDownloads() },
            onClearCacheClick = { viewModel.clearTempCache() },
            onUpdateOutputFileName = { name -> viewModel.updateOutputFileName(name) },
            onDismiss = { showAboutDialog = false }
        )
    }
}

@Composable
fun PlayStoreAboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedSection by remember { mutableStateOf(0) }
    val sections = listOf("Privacy Policy", "Features")

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PDF Merger",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Version 1.0.0 • Local PDF processing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section Tabs
                TabRow(
                    selectedTabIndex = selectedSection,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    sections.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedSection == index,
                            onClick = { selectedSection = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedSection == index) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                when (selectedSection) {
                    0 -> {
                        // Simple Privacy Policy
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Private, local processing",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF6EE7B7) else Color(0xFF065F46)
                                    )
                                    Text(
                                        text = "PDF processing stays on-device; files leave app-private storage only when you save or share them.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFA7F3D0) else Color(0xFF047857)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Privacy Highlights",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )

                        SimplePrivacyItem(
                            title = "No Built-in Data Collection",
                            description = "The app has no analytics or cloud-upload dependency in its current build."
                        )

                        SimplePrivacyItem(
                            title = "Local PDF Processing",
                            description = "Merging, page rotation, previews, and PDF protection run locally without Android INTERNET permission."
                        )

                        SimplePrivacyItem(
                            title = "Scoped File Access",
                            description = "The app works with PDFs you explicitly open or share through Android document and intent APIs, without broad storage permission."
                        )

                        SimplePrivacyItem(
                            title = "Controlled Cleanup",
                            description = "Remove Document, Clear All, and Clear Cache delete app-owned working copies for the active session; exported PDFs are left untouched."
                        )
                    }
                    1 -> {
                        // Features & How to Use
                        Text(
                            text = "Core Functionality:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )

                        Text(
                            text = "1. Batch Import: Select multiple PDF files at once, or use 'Open with / Share' from any Android file manager or messaging app.\n\n" +
                                    "2. Page Drag & Drop: Drag page sequence badges (#1, #2...) or long-press cards to visually organize the final document sequence.\n\n" +
                                    "3. Per-Page Rotation: Rotate individual pages 90° clockwise or counter-clockwise.\n\n" +
                                    "4. PDF Security: Apply password protection and PDF permission restrictions, then verify the saved protection state before success.\n\n" +
                                    "5. Direct Downloads: Automatically save directly to your Downloads/PDF_Merger folder or choose a custom folder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Got it")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { launchPlayStoreRating(context) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    modifier = Modifier.testTag("about_rate_app_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rate App", color = Color.White, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Check out PDF Merger: local on-device PDF merging with page controls and optional password protection for Android!"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share PDF Merger"))
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("about_share_app_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
            }
        }
    )
}

