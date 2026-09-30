package com.example.model

data class PdfSecurityConfig(
    val isEnabled: Boolean = false,
    val userPassword: String = "",
    val ownerPassword: String = "",
    val restrictPrinting: Boolean = true,
    val restrictModifying: Boolean = true,
    val restrictCopyingText: Boolean = true,
    val restrictAddingAnnotations: Boolean = true,
    val encryptionKeyLength: Int = 128
) {
    val isPasswordConfigured: Boolean
        get() = isEnabled && (userPassword.isNotBlank() || ownerPassword.isNotBlank())
}
