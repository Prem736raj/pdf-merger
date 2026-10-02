package com.mergepdf.inone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mergepdf.inone.model.PdfPageItem
import com.mergepdf.inone.ui.components.DeletePagesCard
import com.mergepdf.inone.ui.components.PageThumbnailCard

@Composable
fun PagesGridTab(
    pages: List<PdfPageItem>,
    markedForDeletionPages: Set<Int> = emptySet(),
    onReorderPage: (fromIndex: Int, toIndex: Int) -> Unit,
    onMovePageDelta: (pageId: String, delta: Int) -> Unit,
    onRotatePageClockwise: (pageId: String) -> Unit,
    onRotatePageCounterClockwise: (pageId: String) -> Unit,
    onResetPageRotation: (pageId: String) -> Unit,
    onRotateAllPages: (degrees: Int) -> Unit,
    onReversePages: () -> Unit,
    onDeletePage: (pageId: String) -> Unit,
    onDeletePagesByNumbers: (Set<Int>) -> Unit = {},
    onPagesMarkedChange: (Set<Int>) -> Unit = {},
    onOpenDeletePagesDialog: () -> Unit = {},
    onPreviewPage: (PdfPageItem) -> Unit,
    onOpenReorderDialog: (PdfPageItem) -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToBatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pages.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesomeMotion,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Pages to Organize",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Upload PDF files in the 'Files' tab first to see and reorder page thumbnails.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNavigateToBatch,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Go to Files Tab")
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("pages_grid_list")
    ) {
        // Tip & Batch Quick Action Toolbar
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Use ↺ or ↻ buttons below each thumbnail to rotate pages 90°. Long-press and drag thumbnails to reorder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Toolbar: Total Pages + Batch Rotate CCW / CW + Reverse
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${pages.size} Total Pages",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Open Delete Pages Dialog
                        OutlinedButton(
                            onClick = onOpenDeletePagesDialog,
                            shape = RoundedCornerShape(8.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp).testTag("toolbar_delete_pages_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Delete...", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Batch Rotate CCW (-90°)
                        OutlinedButton(
                            onClick = { onRotateAllPages(-90) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp).testTag("rotate_all_ccw_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("↺ 90°", fontSize = 11.sp)
                        }

                        // Batch Rotate CW (+90°)
                        OutlinedButton(
                            onClick = { onRotateAllPages(90) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp).testTag("rotate_all_cw_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("↻ 90°", fontSize = 11.sp)
                        }

                        // Reverse Order
                        OutlinedButton(
                            onClick = onReversePages,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Reverse", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Dedicated "Remove Box" Delete Pages Card
        item(span = { GridItemSpan(maxLineSpan) }) {
            DeletePagesCard(
                totalPages = pages.size,
                onDeletePages = onDeletePagesByNumbers,
                onPagesMarkedChange = onPagesMarkedChange,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // Thumbnails Grid Items
        itemsIndexed(
            items = pages,
            key = { _, page -> page.id }
        ) { index, page ->
            val pageNum = index + 1
            PageThumbnailCard(
                page = page,
                sequenceNumber = pageNum,
                totalCount = pages.size,
                canMoveLeft = index > 0,
                canMoveRight = index < pages.size - 1,
                isMarkedForDeletion = pageNum in markedForDeletionPages,
                onMoveLeft = { onMovePageDelta(page.id, -1) },
                onMoveRight = { onMovePageDelta(page.id, 1) },
                onRotateClockwise = { onRotatePageClockwise(page.id) },
                onRotateCounterClockwise = { onRotatePageCounterClockwise(page.id) },
                onResetRotation = { onResetPageRotation(page.id) },
                onDelete = { onDeletePage(page.id) },
                onPreview = { onPreviewPage(page) },
                onOpenReorderDialog = { onOpenReorderDialog(page) },
                onDragReorder = { deltaY, deltaX ->
                    val columns = 2 // standard phone layout
                    val deltaSteps = (deltaY / 220f).toInt() * columns + (deltaX / 160f).toInt()
                    if (deltaSteps != 0) {
                        val targetIndex = (index + deltaSteps).coerceIn(0, pages.size - 1)
                        if (targetIndex != index) {
                            onReorderPage(index, targetIndex)
                        }
                    }
                }
            )
        }

        // Next Step Card (Security & Merge)
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ready to Merge into One?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configure output file, set password protection, and export",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onNavigateToSecurity,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Merge into One")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}
