package com.mergepdf.inone.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object SamplePdfGenerator {

    /**
     * Generates a sample PDF file with the specified title, theme color, and number of pages.
     * Uses Android's native PdfDocument for standard A4 rendering.
     */
    fun createSamplePdf(
        context: Context,
        fileName: String,
        documentTitle: String,
        subtitle: String,
        pageCount: Int,
        headerColorHex: Int,
        accentColorHex: Int
    ): File {
        val samplesDir = File(context.cacheDir, "sample_pdfs").apply { mkdirs() }
        val outputFile = File(samplesDir, fileName)
        if (outputFile.exists() && outputFile.length() > 0) {
            return outputFile
        }

        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 standard width in points
            val pageHeight = 842 // A4 standard height in points

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 24f
                isFakeBoldText = true
            }

            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(220, 225, 235)
                textSize = 14f
            }

            val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 18f
                isFakeBoldText = true
            }

            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 12f
            }

            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColorHex
            }

            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 11f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }

            val pageNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(148, 163, 184)
                textSize = 10f
                textAlign = Paint.Align.RIGHT
            }

            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = headerColorHex
            }

            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 1f
            }

            val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(248, 250, 252)
            }

            for (i in 1..pageCount) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                // 1. Draw header background banner
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 120f, headerPaint)

                // 2. Draw decorative accent line
                val accentLinePaint = Paint().apply {
                    color = accentColorHex
                    strokeWidth = 4f
                }
                canvas.drawLine(0f, 120f, pageWidth.toFloat(), 120f, accentLinePaint)

                // 3. Header text
                canvas.drawText(documentTitle, 40f, 55f, titlePaint)
                canvas.drawText("$subtitle — Section Part $i", 40f, 85f, subtitlePaint)

                // 4. Document badge
                val badgeRect = RectF(pageWidth - 140f, 40f, pageWidth - 40f, 75f)
                canvas.drawRoundRect(badgeRect, 8f, 8f, badgePaint)
                canvas.drawText("PART $i OF $pageCount", badgeRect.centerX(), badgeRect.centerY() + 4f, badgeTextPaint)

                // 5. Body section card
                val contentCard = RectF(40f, 150f, pageWidth - 40f, 320f)
                canvas.drawRoundRect(contentCard, 12f, 12f, cardBgPaint)

                canvas.drawText("Chapter $i: Core Overview & Specifications", 60f, 190f, headingPaint)
                canvas.drawLine(60f, 205f, pageWidth - 60f, 205f, linePaint)

                val bulletPoints = listOf(
                    "• Secure digital document processing with end-to-end local encryption.",
                    "• Batch merging supports combining varied page layouts and custom dimensions.",
                    "• Granular permission locks for printing, text extraction, and annotations.",
                    "• Visual thumbnail management with seamless drag-and-drop page reordering.",
                    "• On-device PDF rendering with zero server data leakage or transmission."
                )

                var yOffset = 230f
                for (bullet in bulletPoints) {
                    canvas.drawText(bullet, 60f, yOffset, bodyPaint)
                    yOffset += 16f
                }

                // 6. Secondary card: Data & Details
                val secondaryCard = RectF(40f, 345f, pageWidth - 40f, 540f)
                canvas.drawRoundRect(secondaryCard, 12f, 12f, cardBgPaint)

                canvas.drawText("Summary Metrics & Verification Table", 60f, 380f, headingPaint)
                canvas.drawLine(60f, 395f, pageWidth - 60f, 395f, linePaint)

                val tableRows = listOf(
                    "Document Name: $fileName",
                    "Source Origin: Internal Sample Generation",
                    "Encryption Standard: AES 128-bit / Standard Protection Policy",
                    "Integrity Check: Verified SHA-256 Digest",
                    "Timestamp: 2026-09-30 (Confidential Copy)"
                )

                yOffset = 425f
                for (row in tableRows) {
                    canvas.drawText("▶  $row", 60f, yOffset, bodyPaint)
                    yOffset += 20f
                }

                // 7. Footer divider and page number
                canvas.drawLine(40f, pageHeight - 60f, pageWidth - 40f, pageHeight - 60f, linePaint)
                val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(148, 163, 184)
                    textSize = 10f
                }
                canvas.drawText("CONFIDENTIAL — PDF MERGER TEST DOCUMENT", 40f, pageHeight - 40f, footerPaint)
                canvas.drawText("Page $i of $pageCount", pageWidth - 40f, pageHeight - 40f, pageNumberPaint)

                pdfDocument.finishPage(page)
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
        } catch (e: Throwable) {
            // Robust fallback for Robolectric/JVM testing where native Android PdfDocument is unshadowed
            try {
                com.tom_roush.pdfbox.pdmodel.PDDocument().use { pdDoc ->
                    for (i in 1..pageCount) {
                        pdDoc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
                    }
                    pdDoc.save(outputFile)
                }
            } catch (fallbackEx: Throwable) {
                val minimalPdf = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\nxref\n0 4\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n190\n%%EOF"
                outputFile.writeText(minimalPdf)
            }
        }
        return outputFile
    }

    fun generateStandardSampleDocuments(context: Context): List<File> {
        val doc1 = createSamplePdf(
            context = context,
            fileName = "Project_Proposal.pdf",
            documentTitle = "Project Proposal 2026",
            subtitle = "Strategic Architecture & Roadmap",
            pageCount = 3,
            headerColorHex = Color.rgb(30, 27, 75), // Deep Indigo
            accentColorHex = Color.rgb(99, 102, 241) // Indigo accent
        )

        val doc2 = createSamplePdf(
            context = context,
            fileName = "Financial_Summary.pdf",
            documentTitle = "Financial Summary Q3",
            subtitle = "Quarterly Revenue & Projections",
            pageCount = 2,
            headerColorHex = Color.rgb(19, 78, 74), // Deep Teal
            accentColorHex = Color.rgb(20, 184, 166) // Teal accent
        )

        val doc3 = createSamplePdf(
            context = context,
            fileName = "Security_Compliance.pdf",
            documentTitle = "Security Compliance Guide",
            subtitle = "Access Control & Permissions Audit",
            pageCount = 2,
            headerColorHex = Color.rgb(120, 53, 15), // Deep Amber/Brown
            accentColorHex = Color.rgb(245, 158, 11) // Amber accent
        )

        return listOf(doc1, doc2, doc3)
    }
}
