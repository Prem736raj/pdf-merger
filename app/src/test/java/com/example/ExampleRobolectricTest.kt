package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.PdfSecurityConfig
import com.example.util.FileUtil
import com.example.util.SamplePdfGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PDF Merger", appName)
    }

    @Test
    fun `test security config defaults and password detection`() {
        val defaultConfig = PdfSecurityConfig()
        assertFalse(defaultConfig.isEnabled)
        assertFalse(defaultConfig.isPasswordConfigured)

        val activeConfig = PdfSecurityConfig(
            isEnabled = true,
            userPassword = "SecretOpenUser",
            ownerPassword = "MasterKeyOwner",
            restrictPrinting = true,
            restrictModifying = true,
            restrictCopyingText = true,
            restrictAddingAnnotations = true
        )
        assertTrue(activeConfig.isEnabled)
        assertTrue(activeConfig.isPasswordConfigured)
        assertTrue(activeConfig.restrictPrinting)
        assertTrue(activeConfig.restrictModifying)
        assertTrue(activeConfig.restrictCopyingText)
        assertTrue(activeConfig.restrictAddingAnnotations)
    }

    @Test
    fun `test file size formatting`() {
        assertEquals("500 B", FileUtil.formatFileSize(500))
        assertEquals("1.5 KB", FileUtil.formatFileSize(1536))
        assertEquals("2.00 MB", FileUtil.formatFileSize(2 * 1024 * 1024))
    }

    @Test
    fun `test page rotation clockwise and counter clockwise arithmetic`() {
        fun rotate(current: Int, delta: Int): Int {
            return ((current + delta) % 360 + 360) % 360
        }

        // Clockwise (+90°)
        var deg = 0
        deg = rotate(deg, 90)
        assertEquals(90, deg)
        deg = rotate(deg, 90)
        assertEquals(180, deg)
        deg = rotate(deg, 90)
        assertEquals(270, deg)
        deg = rotate(deg, 90)
        assertEquals(0, deg)

        // Counter-Clockwise (-90°)
        deg = 0
        deg = rotate(deg, -90)
        assertEquals(270, deg)
        deg = rotate(deg, -90)
        assertEquals(180, deg)
        deg = rotate(deg, -90)
        assertEquals(90, deg)
        deg = rotate(deg, -90)
        assertEquals(0, deg)
    }

    @Test
    fun `test sample PDF generation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val samplePdf = SamplePdfGenerator.createSamplePdf(
            context = context,
            fileName = "Test_Document.pdf",
            documentTitle = "Test Document Title",
            subtitle = "Test Subtitle",
            pageCount = 2,
            headerColorHex = android.graphics.Color.BLUE,
            accentColorHex = android.graphics.Color.CYAN
        )
        assertNotNull(samplePdf)
        assertTrue(samplePdf.exists())
        assertTrue(samplePdf.length() > 0)
    }
}
