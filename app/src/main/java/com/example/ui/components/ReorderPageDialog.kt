package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PdfPageItem

@Composable
fun ReorderPageDialog(
    page: PdfPageItem,
    currentIndex: Int, // 1-based index
    totalPages: Int,
    onMoveToPosition: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var targetPosition by remember { mutableFloatStateOf(currentIndex.toFloat()) }
    var textInput by remember { mutableStateOf(currentIndex.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Reorder Page",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Move Page #${currentIndex} (${page.documentName} P.${page.displayPageNumber}) to a new position in the merged sequence:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Target Position: ${targetPosition.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { input ->
                            textInput = input
                            input.toIntOrNull()?.let { num ->
                                if (num in 1..totalPages) {
                                    targetPosition = num.toFloat()
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .testTag("target_position_input")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value = targetPosition,
                    onValueChange = {
                        targetPosition = it
                        textInput = it.toInt().toString()
                    },
                    valueRange = 1f..totalPages.toFloat().coerceAtLeast(1f),
                    steps = (totalPages - 2).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick jump buttons: First, Last
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            targetPosition = 1f
                            textInput = "1"
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Move to First", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            targetPosition = totalPages.toFloat()
                            textInput = totalPages.toString()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Move to Last", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onMoveToPosition(targetPosition.toInt()) },
                modifier = Modifier.testTag("apply_reorder_button")
            ) {
                Text("Move Page")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
