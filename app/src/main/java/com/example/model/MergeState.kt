package com.example.model

import java.io.File

sealed interface MergeState {
    data object Idle : MergeState
    data class Merging(val progress: Float, val currentStep: String) : MergeState
    data class Success(
        val outputFile: File,
        val totalPages: Int,
        val fileSizeBytes: Long,
        val isProtected: Boolean,
        val userPasswordSet: Boolean,
        val ownerPasswordSet: Boolean,
        val restrictedPrinting: Boolean,
        val restrictedModifying: Boolean,
        val restrictedCopying: Boolean,
        val restrictedAnnotations: Boolean,
        val savedPathDisplay: String? = null
    ) : MergeState
    data class Error(val message: String) : MergeState
}
