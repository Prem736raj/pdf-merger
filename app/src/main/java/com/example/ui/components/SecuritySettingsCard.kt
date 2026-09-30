package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PdfSecurityConfig

@Composable
fun SecuritySettingsCard(
    config: PdfSecurityConfig,
    onConfigChange: (PdfSecurityConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var showUserPassword by remember { mutableStateOf(false) }
    var showOwnerPassword by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("security_settings_card")
            .border(
                width = 1.dp,
                color = if (config.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Main Protection Toggle Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (config.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (config.isEnabled) Icons.Default.Lock else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (config.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "PDF Security & Passwords",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (config.isEnabled) "Encryption & restrictions enabled" else "No security locks applied",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (config.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = config.isEnabled,
                    onCheckedChange = { isChecked ->
                        onConfigChange(config.copy(isEnabled = isChecked))
                    },
                    modifier = Modifier.testTag("toggle_security_switch")
                )
            }

            AnimatedVisibility(visible = config.isEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    // Security Info Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Standard AES 128-bit encryption will protect the merged file against unauthorized viewing and actions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 1. User Password (Open Document)
                    Text(
                        text = "1. User Password (To Open Document)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Leave empty if anyone can open, but permissions below will still be locked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    OutlinedTextField(
                        value = config.userPassword,
                        onValueChange = { onConfigChange(config.copy(userPassword = it)) },
                        placeholder = { Text("e.g. SecretOpen2026") },
                        singleLine = true,
                        visualTransformation = if (showUserPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showUserPassword = !showUserPassword }) {
                                Icon(
                                    imageVector = if (showUserPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_password_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Owner Password (Master Permissions Password)
                    Text(
                        text = "2. Owner Password (To Modify Permissions)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Required if someone wants to change these permission restrictions later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    OutlinedTextField(
                        value = config.ownerPassword,
                        onValueChange = { onConfigChange(config.copy(ownerPassword = it)) },
                        placeholder = { Text("e.g. MasterKey@2026") },
                        singleLine = true,
                        visualTransformation = if (showOwnerPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showOwnerPassword = !showOwnerPassword }) {
                                Icon(
                                    imageVector = if (showOwnerPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_password_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Permission Restrictions Checklist
                    Text(
                        text = "Granular Access Permissions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Toggle on the restrictions you wish to enforce:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // A: Restrict Printing
                    PermissionToggleRow(
                        title = "Restrict Printing",
                        description = "Prevent printing the document or downsample to low-res",
                        icon = Icons.Default.Print,
                        isChecked = config.restrictPrinting,
                        onCheckedChange = { onConfigChange(config.copy(restrictPrinting = it)) },
                        tag = "toggle_restrict_printing"
                    )

                    // B: Restrict Modifying
                    PermissionToggleRow(
                        title = "Restrict Modifying Contents",
                        description = "Prevent editing text, extracting pages, or altering layout",
                        icon = Icons.Default.Edit,
                        isChecked = config.restrictModifying,
                        onCheckedChange = { onConfigChange(config.copy(restrictModifying = it)) },
                        tag = "toggle_restrict_modifying"
                    )

                    // C: Restrict Copying Text
                    PermissionToggleRow(
                        title = "Restrict Copying Text & Graphics",
                        description = "Prevent selecting and copying content to clipboard",
                        icon = Icons.Default.ContentCopy,
                        isChecked = config.restrictCopyingText,
                        onCheckedChange = { onConfigChange(config.copy(restrictCopyingText = it)) },
                        tag = "toggle_restrict_copying"
                    )

                    // D: Restrict Adding Annotations
                    PermissionToggleRow(
                        title = "Restrict Annotations & Comments",
                        description = "Prevent adding markup, sticky notes, or form fill changes",
                        icon = Icons.AutoMirrored.Filled.Comment,
                        isChecked = config.restrictAddingAnnotations,
                        onCheckedChange = { onConfigChange(config.copy(restrictAddingAnnotations = it)) },
                        tag = "toggle_restrict_annotations"
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionToggleRow(
    title: String,
    description: String,
    icon: ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isChecked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
