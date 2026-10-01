package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

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
        data object RequiresPicker : SaveResult()
    }

    fun isAutoSaveEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SAVE_ENABLED, true)

    fun setAutoSaveEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_SAVE_ENABLED, enabled).apply()
    }

    fun getSaveMode(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SAVE_MODE, MODE_DEFAULT_DOWNLOADS) ?: MODE_DEFAULT_DOWNLOADS

    fun getDestinationDisplayName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_SAVE_MODE, MODE_DEFAULT_DOWNLOADS)) {
            MODE_CUSTOM_FOLDER -> prefs.getString(KEY_CUSTOM_FOLDER_NAME, null) ?: "Chosen Folder"
            MODE_ALWAYS_ASK -> "Ask every time"
            else -> "Downloads / PDF_Merger"
        }
    }

    fun setCustomFolder(context: Context, treeUri: Uri, folderName: String?) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist custom-folder permission: ${e.message}")
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVE_MODE, MODE_CUSTOM_FOLDER)
            .putString(KEY_CUSTOM_TREE_URI, treeUri.toString())
            .putString(KEY_CUSTOM_FOLDER_NAME, folderName ?: "Selected Folder")
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
            .edit().putString(KEY_SAVE_MODE, MODE_ALWAYS_ASK).apply()
    }

    suspend fun saveMergedPdfDirectly(
        context: Context,
        sourcePdfFile: File,
        customFileName: String? = null
    ): SaveResult = withContext(Dispatchers.IO) {
        if (!sourcePdfFile.exists() || !sourcePdfFile.isFile || sourcePdfFile.length() <= 0L) {
            return@withContext SaveResult.Failure("Merged PDF is missing or empty.")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getString(KEY_SAVE_MODE, MODE_DEFAULT_DOWNLOADS) ?: MODE_DEFAULT_DOWNLOADS
        if (!prefs.getBoolean(KEY_AUTO_SAVE_ENABLED, true) || mode == MODE_ALWAYS_ASK) {
            return@withContext SaveResult.RequiresPicker
        }

        val fileName = exportFileName(customFileName ?: sourcePdfFile.name)
        when (mode) {
            MODE_CUSTOM_FOLDER -> saveToCustomTree(context, sourcePdfFile, fileName)
            MODE_DEFAULT_DOWNLOADS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStoreDownloads(context, sourcePdfFile, fileName)
            } else {
                SaveResult.RequiresPicker
            }
            else -> SaveResult.RequiresPicker
        }
    }

    suspend fun saveMergedPdfToUri(
        context: Context,
        sourcePdfFile: File,
        destinationUri: Uri,
        displayName: String = "selected location"
    ): SaveResult = withContext(Dispatchers.IO) {
        if (!sourcePdfFile.exists() || !sourcePdfFile.isFile || sourcePdfFile.length() <= 0L) {
            return@withContext SaveResult.Failure("Merged PDF is missing or empty.")
        }

        try {
            val destination = context.contentResolver.openOutputStream(destinationUri, "w")
                ?: throw IOException("The document provider returned no writable output stream.")
            val copied = destination.use { output ->
                FileInputStream(sourcePdfFile).use { input ->
                    input.copyTo(output).also { output.flush() }
                }
            }
            if (copied != sourcePdfFile.length()) {
                throw IOException("The provider wrote $copied of ${sourcePdfFile.length()} bytes.")
            }

            SaveResult.Success(
                destinationUri = destinationUri,
                displayPath = displayName,
                fileName = exportFileName(sourcePdfFile.name)
            )
        } catch (e: Exception) {
            runCatching { context.contentResolver.delete(destinationUri, null, null) }
            Log.e(TAG, "Save As write failed", e)
            SaveResult.Failure("Could not write the PDF to the selected location.")
        }
    }

    private fun saveToCustomTree(context: Context, source: File, fileName: String): SaveResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val treeValue = prefs.getString(KEY_CUSTOM_TREE_URI, null)
            ?: return SaveResult.Failure("The selected custom folder is no longer configured.")
        val treeUri = Uri.parse(treeValue)
        var createdUri: Uri? = null

        return try {
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            createdUri = DocumentsContract.createDocument(
                context.contentResolver, parentUri, "application/pdf", fileName
            ) ?: return SaveResult.Failure("The document provider could not create the output file.")

            val destination = context.contentResolver.openOutputStream(createdUri!!, "w")
                ?: throw IOException("The document provider returned no writable output stream.")
            val copied = destination.use { output ->
                FileInputStream(source).use { input ->
                    input.copyTo(output).also { output.flush() }
                }
            }
            if (copied != source.length()) {
                throw IOException("The provider wrote $copied of ${source.length()} bytes.")
            }

            val folderName = prefs.getString(KEY_CUSTOM_FOLDER_NAME, "Chosen Folder") ?: "Chosen Folder"
            SaveResult.Success(createdUri!!, "$folderName/$fileName", fileName)
        } catch (e: Exception) {
            createdUri?.let { uri ->
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            }
            Log.e(TAG, "Custom-folder save failed", e)
            SaveResult.Failure("Could not save to the selected folder. Choose the folder again or use Save As.")
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStoreDownloads(context: Context, source: File, fileName: String): SaveResult {
        var targetUri: Uri? = null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PDF_Merger")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            targetUri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: return SaveResult.Failure("Android could not create a Downloads entry.")

            val destination = context.contentResolver.openOutputStream(targetUri!!, "w")
                ?: throw IOException("MediaStore returned no writable output stream.")
            val copied = destination.use { output ->
                FileInputStream(source).use { input ->
                    input.copyTo(output).also { output.flush() }
                }
            }
            if (copied != source.length()) {
                throw IOException("MediaStore wrote $copied of ${source.length()} bytes.")
            }

            val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (context.contentResolver.update(targetUri!!, publish, null, null) <= 0) {
                throw IOException("MediaStore could not publish the completed PDF.")
            }

            SaveResult.Success(
                targetUri!!,
                "Downloads/PDF_Merger/$fileName",
                fileName
            )
        } catch (e: Exception) {
            targetUri?.let { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
            Log.e(TAG, "MediaStore save failed", e)
            SaveResult.Failure("Could not save the PDF to Downloads. No completed file was published.")
        }
    }

    private fun exportFileName(rawName: String): String {
        val safe = FileUtil.sanitizeDisplayName(rawName)
        val stem = if (safe.endsWith(".pdf", ignoreCase = true)) safe.dropLast(4) else safe
        return "${stem.ifBlank { "Merged_Document" }}.pdf"
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
