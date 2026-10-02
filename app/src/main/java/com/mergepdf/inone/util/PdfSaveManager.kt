package com.mergepdf.inone.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PdfSaveManager {

    private const val TAG = "PdfSaveManager"
    private const val PREFS_NAME = "pdf_merger_save_prefs"
    private const val KEY_SAVE_MODE = "key_save_mode"
    private const val KEY_CUSTOM_TREE_URI = "key_custom_tree_uri"
    private const val KEY_CUSTOM_FOLDER_NAME = "key_custom_folder_name"
    private const val KEY_AUTO_SAVE_ENABLED = "key_auto_save_enabled"

    const val MODE_DEFAULT_DOWNLOADS = "DOWNLOADS"
    const val MODE_CUSTOM_FOLDER = "CUSTOM_FOLDER"
    const val MODE_ALWAYS_ASK = "ALWAYS_ASK"

    sealed class SaveResult {
        data class Success(val destinationUri: Uri, val displayPath: String, val fileName: String) : SaveResult()
        data class Failure(val errorMessage: String) : SaveResult()
        object RequiresPicker : SaveResult()
    }

    fun isAutoSaveEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_SAVE_ENABLED, true)
    }

    fun setAutoSaveEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SAVE_ENABLED, enabled)
            .apply()
    }

    fun getSaveMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SAVE_MODE, MODE_DEFAULT_DOWNLOADS) ?: MODE_DEFAULT_DOWNLOADS
    }

    fun getDestinationDisplayName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getString(KEY_SAVE_MODE, MODE_DEFAULT_DOWNLOADS)
        return when (mode) {
            MODE_CUSTOM_FOLDER -> {
                val customName = prefs.getString(KEY_CUSTOM_FOLDER_NAME, null)
                customName ?: "Chosen Folder"
            }
            MODE_ALWAYS_ASK -> "Ask every time"
            else -> "Downloads / PDF_Merger"
        }
    }

    fun setCustomFolder(context: Context, treeUri: Uri, folderName: String?) {
        try {
            // Take persistable permission so we can write to this folder anytime
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        } catch (e: Exception) {
            Log.w(TAG, "Persistable permission notice: ${e.message}")
        }

        val displayName = folderName ?: "Selected Folder"
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVE_MODE, MODE_CUSTOM_FOLDER)
            .putString(KEY_CUSTOM_TREE_URI, treeUri.toString())
            .putString(KEY_CUSTOM_FOLDER_NAME, displayName)
            .putBoolean(KEY_AUTO_SAVE_ENABLED, true)
            .apply()
    }

    fun resetToDefaultDownloads(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVE_MODE, MODE_DEFAULT_DOWNLOADS)
            .remove(KEY_CUSTOM_TREE_URI)
            .remove(KEY_CUSTOM_FOLDER_NAME)
            .putBoolean(KEY_AUTO_SAVE_ENABLED, true)
            .apply()
    }

    fun setModeAlwaysAsk(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVE_MODE, MODE_ALWAYS_ASK)
            .apply()
    }

    suspend fun saveMergedPdfDirectly(
        context: Context,
        sourcePdfFile: File,
        customFileName: String? = null
    ): SaveResult = withContext(Dispatchers.IO) {
        if (!sourcePdfFile.exists()) {
            return@withContext SaveResult.Failure("Source file does not exist")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getString(KEY_SAVE_MODE, MODE_DEFAULT_DOWNLOADS)
        val isAutoSave = prefs.getBoolean(KEY_AUTO_SAVE_ENABLED, true)

        if (!isAutoSave || mode == MODE_ALWAYS_ASK) {
            return@withContext SaveResult.RequiresPicker
        }

        val baseFileName = if (!customFileName.isNullOrBlank()) {
            if (customFileName.endsWith(".pdf", ignoreCase = true)) customFileName else "$customFileName.pdf"
        } else {
            sourcePdfFile.name
        }

        // 1. Try Custom Folder if configured
        if (mode == MODE_CUSTOM_FOLDER) {
            val treeUriStr = prefs.getString(KEY_CUSTOM_TREE_URI, null)
            if (!treeUriStr.isNullOrBlank()) {
                try {
                    val treeUri = Uri.parse(treeUriStr)
                    val docId = DocumentsContract.getTreeDocumentId(treeUri)
                    val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                    val createdDocUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        parentDocUri,
                        "application/pdf",
                        baseFileName
                    )

                    if (createdDocUri != null) {
                        context.contentResolver.openOutputStream(createdDocUri)?.use { out ->
                            FileInputStream(sourcePdfFile).use { input ->
                                input.copyTo(out)
                            }
                        }
                        val folderName = prefs.getString(KEY_CUSTOM_FOLDER_NAME, "Chosen Folder") ?: "Chosen Folder"
                        return@withContext SaveResult.Success(
                            destinationUri = createdDocUri,
                            displayPath = "$folderName/$baseFileName",
                            fileName = baseFileName
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed writing to custom tree URI: ${e.message}", e)
                    // Fallback to downloads mode if custom folder is no longer accessible
                }
            }
        }

        // 2. Default Downloads via MediaStore (Android 10+ / API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, baseFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PDF_Merger")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val targetUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (targetUri != null) {
                    context.contentResolver.openOutputStream(targetUri)?.use { out ->
                        FileInputStream(sourcePdfFile).use { input ->
                            input.copyTo(out)
                        }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(targetUri, contentValues, null, null)

                    return@withContext SaveResult.Success(
                        destinationUri = targetUri,
                        displayPath = "Downloads/PDF_Merger/$baseFileName",
                        fileName = baseFileName
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore Downloads insert error: ${e.message}", e)
            }
        }

        // 3. Fallback for older Android or direct file system write
        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val subFolder = File(publicDownloads, "PDF_Merger").apply { mkdirs() }
            val destFile = File(subFolder, baseFileName)

            FileInputStream(sourcePdfFile).use { input ->
                FileOutputStream(destFile).use { out ->
                    input.copyTo(out)
                }
            }

            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )

            return@withContext SaveResult.Success(
                destinationUri = fileUri,
                displayPath = "Downloads/PDF_Merger/$baseFileName",
                fileName = baseFileName
            )
        } catch (e: Exception) {
            Log.w(TAG, "Direct public downloads write notice: ${e.message}")
            // Return RequiresPicker so user can use system document picker as ultimate safety net
            return@withContext SaveResult.RequiresPicker
        }
    }

    fun openPdf(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open PDF with"))
        } catch (e: Exception) {
            Log.e(TAG, "Could not open PDF: ${e.message}")
        }
    }
}
