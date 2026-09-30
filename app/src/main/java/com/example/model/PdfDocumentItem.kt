package com.example.model

import android.net.Uri
import androidx.compose.ui.graphics.Color
import java.io.File

data class PdfDocumentItem(
    val id: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val sourceUri: Uri? = null,
    val localFile: File,
    val accentColor: Color,
    val isEncrypted: Boolean = false,
    val isLocked: Boolean = false,
    val password: String? = null
)
