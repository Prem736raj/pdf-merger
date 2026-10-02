package com.mergepdf.inone.model

import androidx.compose.ui.graphics.Color
import java.io.File

data class PdfPageItem(
    val id: String,
    val documentId: String,
    val documentName: String,
    val pageIndex: Int,
    val rotationDegrees: Int = 0,
    val thumbnailFile: File? = null,
    val accentColor: Color
) {
    val displayPageNumber: Int
        get() = pageIndex + 1
}
