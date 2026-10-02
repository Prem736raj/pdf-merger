package com.mergepdf.inone.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import com.mergepdf.inone.model.MergeState
import com.mergepdf.inone.util.FileUtil

@Composable
fun MergeSuccessDialog(
    successState: MergeState.Success,
    onSaveToDevice: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val cardBg = if (isDark) Color(0xFF0B101D) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) {
        Brush.linearGradient(
            listOf(Color(0xFF38BDF8).copy(alpha = 0.4f), Color(0xFF6366F1).copy(alpha = 0.3f), Color(0xFF8B5CF6).copy(alpha = 0.4f))
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFF38BDF8).copy(alpha = 0.6f), Color(0xFF6366F1).copy(alpha = 0.5f))
        )
    }

    val primaryTextColor = if (isDark) Color.White else Color(0xFF0F172A)
    val secondaryTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
    val metadataBg = if (isDark) Color(0xFF131A2E) else Color(0xFFF8FAFC)
    val metadataBorder = if (isDark) Color(0xFF38BDF8).copy(alpha = 0.18f) else Color(0xFFE2E8F0)
    val securityBg = if (isDark) Color(0xFF161A36) else Color(0xFFEEF2FF)
    val securityBorder = if (isDark) Color(0xFF6366F1).copy(alpha = 0.35f) else Color(0xFFC7D2FE)
    val securityTitleColor = if (isDark) Color(0xFFA5B4FC) else Color(0xFF3730A3)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("merge_success_dialog")
                .border(
                    width = 1.2.dp,
                    brush = cardBorder,
                    shape = RoundedCornerShape(26.dp)
                )
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Top-Right Circular Close Button (X) - 48dp touch target, high z-index
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(48.dp)
                        .zIndex(25f)
                        .testTag("close_success_dialog_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Dialog",
                            tint = if (isDark) Color(0xFFE2E8F0) else Color(0xFF475569),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Celebratory 3D PDF Illustration with PDF badge
                    CelebratoryPdfSuccessGraphic(
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Title: "PDF Merged Successfully!"
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PDF Merged ",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryTextColor
                        )
                        Text(
                            text = "Successfully!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
                        )
                    }

                    Text(
                        text = "All pages combined and encrypted securely on-device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Metadata Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = metadataBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, metadataBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Output File Row (Purple icon)
                            MetadataRowItem(
                                icon = Icons.Default.Description,
                                iconBgColor = Color(0xFF8B5CF6),
                                label = "Output File",
                                value = successState.outputFile.name,
                                labelColor = secondaryTextColor,
                                valueColor = primaryTextColor
                            )

                            HorizontalDivider(
                                color = dividerColor,
                                thickness = 0.8.dp
                            )

                            // Total Pages Row (Pink icon)
                            MetadataRowItem(
                                icon = Icons.Default.AutoAwesomeMotion,
                                iconBgColor = Color(0xFFEC4899),
                                label = "Total Pages",
                                value = "${successState.totalPages} pages",
                                labelColor = secondaryTextColor,
                                valueColor = primaryTextColor
                            )

                            HorizontalDivider(
                                color = dividerColor,
                                thickness = 0.8.dp
                            )

                            // File Size Row (Teal icon)
                            MetadataRowItem(
                                icon = Icons.Default.Folder,
                                iconBgColor = Color(0xFF10B981),
                                label = "File Size",
                                value = FileUtil.formatFileSize(successState.fileSizeBytes),
                                labelColor = secondaryTextColor,
                                valueColor = primaryTextColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Security Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = securityBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, securityBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF8B5CF6)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (successState.isProtected) "Security: AES 128-bit Protected" else "Security: Standard Document",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = securityTitleColor
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified Protection",
                                    tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            HorizontalDivider(
                                color = dividerColor,
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            // Restriction pills
                            val restrictions = mutableListOf<String>()
                            if (successState.userPasswordSet) restrictions.add("User Password Set") else restrictions.add("User Password: None")
                            if (successState.ownerPasswordSet) restrictions.add("Owner Password Set")
                            if (successState.restrictedPrinting) restrictions.add("No Printing - Yes") else restrictions.add("No Printing - No")
                            if (successState.restrictedModifying) restrictions.add("Modifying - Restricted") else restrictions.add("Modifying - No")
                            if (successState.restrictedCopying) restrictions.add("Text Copying - Restricted") else restrictions.add("Text Copying - No")
                            restrictions.add("Annotations - No")

                            Text(
                                text = restrictions.joinToString(" • "),
                                fontSize = 11.sp,
                                color = secondaryTextColor,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Button: Glowing Blue to Purple Gradient "Save / Download to Device"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(8.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF00B4D8), Color(0xFF3B82F6), Color(0xFF8B5CF6))
                                )
                            )
                            .clickable { onSaveToDevice() }
                            .testTag("download_merged_pdf_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save / Download to Device",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Row: "Open PDF" and "Share" Outlined Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val secondaryBorderColor = if (isDark) Color(0xFF38BDF8).copy(alpha = 0.5f) else Color(0xFF0284C7)
                        val secondaryBtnContentColor = if (isDark) Color.White else Color(0xFF0284C7)

                        OutlinedButton(
                            onClick = { openPdfInViewer(context, successState.outputFile) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("open_merged_pdf_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                secondaryBorderColor
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = secondaryBtnContentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open PDF", fontSize = 13.sp, color = secondaryBtnContentColor, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { sharePdfFile(context, successState.outputFile) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("share_merged_pdf_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                secondaryBorderColor
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = secondaryBtnContentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", fontSize = 13.sp, color = secondaryBtnContentColor, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // "Done / Close"
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Done / Close",
                            fontSize = 13.sp,
                            color = secondaryTextColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRowItem(
    icon: ImageVector,
    iconBgColor: Color,
    label: String,
    value: String,
    labelColor: Color = Color(0xFF94A3B8),
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                color = labelColor
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1
        )
    }
}

private fun openPdfInViewer(context: Context, file: java.io.File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open Merged PDF"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun sharePdfFile(context: Context, file: java.io.File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Merged PDF"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
