package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
 import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
 import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
 import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
 import com.example.model.PdfPageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
 
@Composable
fun PageThumbnailCard(
    page: PdfPageItem,
    sequenceNumber: Int,
    totalCount: Int,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRotateClockwise: () -> Unit,
    onRotateCounterClockwise: () -> Unit,
    onResetRotation: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit,
    onRequestThumbnail: () -> Unit,
    onOpenReorderDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var accumulatedHorizontalDrag by remember { mutableFloatStateOf(0f) }

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1.0f,
        label = "drag_scale"
    )

    val elevation = if (isDragging) 12.dp else 3.dp

    var bitmap by remember(page.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(page.id, page.thumbnailFile?.absolutePath) {
        val thumbnailFile = page.thumbnailFile
        if (thumbnailFile != null && thumbnailFile.exists() && thumbnailFile.length() > 0L) {
            bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(thumbnailFile.absolutePath)
            }
        } else {
            bitmap = null
            onRequestThumbnail()
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        modifier = modifier
            .testTag("page_thumbnail_${sequenceNumber}")
            .scale(scale)
            .border(
                width = if (isDragging) 2.5.dp else 1.5.dp,
                color = if (isDragging) MaterialTheme.colorScheme.primary else page.accentColor.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Header: Draggable Page Number Badge + Source Document Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive Drag-to-Change-Page-Number Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clickable { onOpenReorderDialog() }
                        .pointerInput(page.id, sequenceNumber) {
                            detectHorizontalDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = { isDragging = false; accumulatedHorizontalDrag = 0f },
                                onDragCancel = { isDragging = false; accumulatedHorizontalDrag = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedHorizontalDrag += dragAmount
                                    if (accumulatedHorizontalDrag > 40f) {
                                        if (canMoveRight) onMoveRight()
                                        accumulatedHorizontalDrag = 0f
                                    } else if (accumulatedHorizontalDrag < -40f) {
                                        if (canMoveLeft) onMoveLeft()
                                        accumulatedHorizontalDrag = 0f
                                    }
                                }
                            )
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to change page number",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "#$sequenceNumber",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Origin document & page tag
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = page.accentColor.copy(alpha = 0.15f),
                    contentColor = page.accentColor,
                    modifier = Modifier
                        .clickable { onOpenReorderDialog() }
                        .padding(start = 6.dp)
                ) {
                    Text(
                        text = "P.${page.displayPageNumber}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Document name label
            Text(
                text = page.documentName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Thumbnail Preview Container
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f) // Standard A4 ratio
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                    .border(0.5.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                    .clickable { onPreview() },
                contentAlignment = Alignment.Center
            ) {
                val thumbnailBitmap = bitmap
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap.asImageBitmap(),
                        contentDescription = "Page $sequenceNumber preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = page.rotationDegrees.toFloat()
                            }
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Page ${page.displayPageNumber}",
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }

                // Magnify / Zoom icon overlay on bottom-right corner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom Page",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Rotation indicator badge if rotated
                if (page.rotationDegrees != 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clickable { onResetRotation() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${page.rotationDegrees}°",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dedicated Page Rotation Tools Row (Clockwise & Counter-Clockwise)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rotate Counter-Clockwise 90° (-90°)
                    IconButton(
                        onClick = onRotateCounterClockwise,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("rotate_ccw_page_$sequenceNumber")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateLeft,
                            contentDescription = "Rotate 90 Degrees Counter-Clockwise",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Rotation Angle Label / Reset
                    Text(
                        text = if (page.rotationDegrees == 0) "0°" else "${page.rotationDegrees}°",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (page.rotationDegrees != 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Rotate Clockwise 90° (+90°)
                    IconButton(
                        onClick = onRotateClockwise,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("rotate_cw_page_$sequenceNumber")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = "Rotate 90 Degrees Clockwise",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Toolbar: Move Left, Delete, Move Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Move Left (-1 Position)
                IconButton(
                    onClick = onMoveLeft,
                    enabled = canMoveLeft,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Move Left",
                        tint = if (canMoveLeft) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete Page
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove Page",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Move Right (+1 Position)
                IconButton(
                    onClick = onMoveRight,
                    enabled = canMoveRight,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Move Right",
                        tint = if (canMoveRight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
