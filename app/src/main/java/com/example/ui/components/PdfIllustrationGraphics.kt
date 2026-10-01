package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 3D celebratory PDF success illustration matching Image 2:
 * Fanned PDF documents, red PDF banner, bursting confetti, gold sparkles,
 * and a prominent glossy emerald green checkmark badge overlapping in front.
 */
@Composable
fun CelebratoryPdfSuccessGraphic(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 180.dp, height = 130.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Confetti & Sparkles
        Box(
            modifier = Modifier
                .offset(x = (-60).dp, y = (-35).dp)
                .rotate(-25f)
                .size(width = 14.dp, height = 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF8B5CF6))
        )
        Box(
            modifier = Modifier
                .offset(x = 65.dp, y = (-28).dp)
                .rotate(35f)
                .size(width = 14.dp, height = 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF00E5FF))
        )
        Box(
            modifier = Modifier
                .offset(x = (-55).dp, y = 15.dp)
                .rotate(40f)
                .size(width = 12.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF38BDF8))
        )
        Box(
            modifier = Modifier
                .offset(x = 60.dp, y = 20.dp)
                .rotate(-45f)
                .size(width = 12.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF00E5FF))
        )
        Box(
            modifier = Modifier
                .offset(x = (-38).dp, y = (-46).dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(0xFFF59E0B))
        )
        // Gold Star Sparkle (Top Right)
        Text(
            text = "✦",
            fontSize = 18.sp,
            color = Color(0xFFFBBF24),
            modifier = Modifier.offset(x = 42.dp, y = (-42).dp)
        )
        Text(
            text = "✦",
            fontSize = 11.sp,
            color = Color(0xFFFBBF24),
            modifier = Modifier.offset(x = 48.dp, y = (-15).dp)
        )

        // Back Document 1 (Rotated Left)
        Surface(
            modifier = Modifier
                .offset(x = (-14).dp, y = 0.dp)
                .rotate(-10f)
                .size(width = 68.dp, height = 86.dp)
                .shadow(6.dp, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFC7D2FE)
        ) {}

        // Middle Document 2 (Slightly Rotated)
        Surface(
            modifier = Modifier
                .offset(x = (-6).dp, y = 2.dp)
                .rotate(-4f)
                .size(width = 72.dp, height = 90.dp)
                .shadow(8.dp, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFE2E8F0)
        ) {}

        // Front Main Document Sheet (White with Red "PDF" badge)
        Surface(
            modifier = Modifier
                .offset(x = 0.dp, y = 4.dp)
                .size(width = 76.dp, height = 94.dp)
                .shadow(12.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top document fold effect / subtle header lines
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFE2E8F0))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFE2E8F0))
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Red "PDF" Badge
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = Color(0xFFEF4444),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "PDF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom document placeholder lines
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFCBD5E1))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFCBD5E1))
                )
            }
        }

        // Vibrant Glossy Red PDF Emblem Badge in place of check sign
        Box(
            modifier = Modifier
                .offset(x = 18.dp, y = 22.dp)
                .size(46.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFEF4444), Color(0xFFDC2626), Color(0xFF991B1B))
                    )
                )
                .border(3.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = "PDF Success",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * 3D Layered PDF hero graphic matching Image 3:
 * 3 fanned PDF document sheets with red PDF tag, subtle dot grid,
 * and an overlapping circular purple badge with "+" in the lower right.
 */
@Composable
fun BatchHeroBannerGraphic(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(width = 110.dp, height = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        // Decorative Sparkles
        Text(
            text = "✦",
            fontSize = 14.sp,
            color = Color(0xFFA5B4FC),
            modifier = Modifier.offset(x = 36.dp, y = (-32).dp)
        )

        // Back Sheet 1
        Surface(
            modifier = Modifier
                .offset(x = (-22).dp, y = (-4).dp)
                .rotate(-10f)
                .size(width = 46.dp, height = 62.dp)
                .shadow(4.dp, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF818CF8)
        ) {}

        // Middle Sheet 2
        Surface(
            modifier = Modifier
                .offset(x = (-12).dp, y = (-2).dp)
                .rotate(-5f)
                .size(width = 48.dp, height = 66.dp)
                .shadow(6.dp, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFC7D2FE)
        ) {}

        // Front Main Sheet (White with red PDF emblem)
        Surface(
            modifier = Modifier
                .offset(x = 0.dp, y = 2.dp)
                .size(width = 54.dp, height = 72.dp)
                .shadow(10.dp, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top document fold bar
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(0xFFE2E8F0))
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Red PDF Tag
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFEF4444)
                ) {
                    Text(
                        text = "PDF",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Placeholder lines
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(0xFFE2E8F0))
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(0xFFE2E8F0))
                )
            }
        }

        // Circular Glossy Purple "+" Plus Badge in lower right with clean white border
        Box(
            modifier = Modifier
                .offset(x = 22.dp, y = 22.dp)
                .size(30.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF818CF8), Color(0xFF6366F1), Color(0xFF4F46E5))
                    )
                )
                .border(2.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add PDF",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
