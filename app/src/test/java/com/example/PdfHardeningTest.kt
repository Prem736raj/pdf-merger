package com.example

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.example.model.PdfPageItem
import com.example.model.PdfSecurityConfig
import com.example.util.FileUtil
import com.example.util.PdfCompatibilityModeRequiredException
import com.example.util.PdfInvalidDocumentException
import com.example.util.PdfMergerEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfHardeningTest {

    private lateinit var context: Context
    private lateinit var root: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PdfMergerEngine.init(context)
        root = File(context.cacheDir, "hardening_tests").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun vectorMergePreservesExactPageOrderAndSearchableText() = runBlocking {
        val a = createTextPdf("a.pdf", listOf("A1", "A2", "A3"))
        val b = createTextPdf("b.pdf", listOf("B1", "B2"))
        val pages = listOf(
            page("a", "A", 1),
            page("b", "B", 0),
            page("a", "A", 0),
            page("b", "B", 1),
            page("a", "A", 2)
        )

        val output = PdfMergerEngine.mergePdfPages(
            context = context,
            orderedPages = pages,
            sourceFilesMap = mapOf("a" to a, "b" to b),
            securityConfig = PdfSecurityConfig(),
            customOutputName = "order-test",
            onProgress = { _, _ -> }
        ).getOrThrow()

        assertEquals(5, output.verification.pageCount)
        assertFalse(output.verification.isEncrypted)
        assertEquals(
            listOf("A2", "B1", "A1", "B2", "A3"),
            extractPageTexts(output.file).map { it.trim() }
        )
    }

    @Test
    fun protectedExportIsReopenedAndVerified() = runBlocking {
        val source = createTextPdf("secure-source.pdf", listOf("SECURE_MARKER_8675309"))
        val config = PdfSecurityConfig(
            isEnabled = true,
            userPassword = "Open-Password-42",
            ownerPassword = "Owner-Password-99",
            restrictPrinting = true,
            restrictModifying = true,
            restrictCopyingText = true,
            restrictAddingAnnotations = true
        )

        val output = PdfMergerEngine.mergePdfPages(
            context = context,
            orderedPages = listOf(page("secure", "Secure", 0)),
            sourceFilesMap = mapOf("secure" to source),
            securityConfig = config,
            customOutputName = "secure-output",
            onProgress = { _, _ -> }
        ).getOrThrow()

        assertTrue(output.verification.isEncrypted)
        assertTrue(output.verification.userPasswordAccepted)
        assertTrue(output.verification.ownerPasswordAccepted)
        assertTrue(output.verification.restrictedPrinting)
        assertTrue(output.verification.restrictedModifying)
        assertTrue(output.verification.restrictedCopying)
        assertTrue(output.verification.restrictedAnnotations)

        assertThrows(InvalidPasswordException::class.java) {
            PDDocument.load(output.file, "wrong-password").close()
        }
        PDDocument.load(output.file, config.userPassword).use { document ->
            assertEquals(1, document.numberOfPages)
            assertTrue(document.isEncrypted)
        }

        val fixtureDir = File(System.getProperty("user.dir"), "build/qpdf-fixtures").apply { mkdirs() }
        val qpdfFixture = File(fixtureDir, "protected-aes128.pdf")
        output.file.copyTo(qpdfFixture, overwrite = true)
        assertTrue(qpdfFixture.exists() && qpdfFixture.length() > 0L)
    }

    @Test
    fun enabledSecurityWithoutAnyPasswordFailsClosed() = runBlocking {
        val source = createTextPdf("no-password-security.pdf", listOf("NO_PASSWORD_SECURITY"))
        val result = PdfMergerEngine.mergePdfPages(
            context = context,
            orderedPages = listOf(page("no-password", "No password", 0)),
            sourceFilesMap = mapOf("no-password" to source),
            securityConfig = PdfSecurityConfig(isEnabled = true),
            customOutputName = "must-fail-security",
            onProgress = { _, _ -> }
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is com.example.util.PdfSecurityException)
    }

    @Test
    fun ownerOnlySecurityDoesNotClaimOpenPassword() = runBlocking {
        val source = createTextPdf("owner-only.pdf", listOf("OWNER_ONLY_SECURITY"))
        val config = PdfSecurityConfig(
            isEnabled = true,
            ownerPassword = "Owner-Only-Password-77",
            restrictPrinting = true,
            restrictModifying = true,
            restrictCopyingText = true,
            restrictAddingAnnotations = true
        )

        val output = PdfMergerEngine.mergePdfPages(
            context = context,
            orderedPages = listOf(page("owner-only", "Owner only", 0)),
            sourceFilesMap = mapOf("owner-only" to source),
            securityConfig = config,
            customOutputName = "owner-only-output",
            onProgress = { _, _ -> }
        ).getOrThrow()

        assertTrue(output.verification.isEncrypted)
        assertFalse(output.verification.userPasswordAccepted)
        assertTrue(output.verification.ownerPasswordAccepted)

        PDDocument.load(output.file).use { openedWithoutPassword ->
            assertTrue(openedWithoutPassword.isEncrypted)
            assertFalse(openedWithoutPassword.currentAccessPermission.isOwnerPermission)
        }
        PDDocument.load(output.file, config.ownerPassword).use { ownerDocument ->
            assertTrue(ownerDocument.currentAccessPermission.isOwnerPermission)
        }
    }

    @Test
    fun vectorMergePreservesUriLinkAndTextAnnotation() = runBlocking {
        val source = createAnnotatedPdf("annotations.pdf")
        val output = PdfMergerEngine.mergePdfPages(
            context = context,
            orderedPages = listOf(page("annotated", "Annotated", 0)),
            sourceFilesMap = mapOf("annotated" to source),
            securityConfig = PdfSecurityConfig(),
            customOutputName = "annotation-preservation",
            onProgress = { _, _ -> }
        ).getOrThrow()

        PDDocument.load(output.file).use { document ->
            val annotations = document.getPage(0).annotations
            val link = annotations.filterIsInstance<PDAnnotationLink>().singleOrNull()
            assertNotNull(link)
            val action = link!!.action as? PDActionURI
            assertNotNull(action)
            assertEquals("https://example.invalid/pdf-merger-link-test", action!!.uri)

            val note = annotations.filterIsInstance<PDAnnotationText>().singleOrNull()
            assertNotNull(note)
            assertEquals("PDF_NOTE_ANNOTATION_8675309", note!!.contents)
        }
    }

    @Test
    fun rotationsPreserveMediaAndCropBoxes() = runBlocking {
        val source = createBoxPdf("boxes.pdf")
        val rotations = listOf(0, 90, 180, 270)
        val output = PdfMergerEngine.mergePdfPages(
            context = context,
            orderedPages = rotations.mapIndexed { index, rotation ->
                page("boxes", "Boxes", 0, rotation, "boxes_$index")
            },
            sourceFilesMap = mapOf("boxes" to source),
            securityConfig = PdfSecurityConfig(),
            customOutputName = "rotation-boxes",
            onProgress = { _, _ -> }
        ).getOrThrow()

        PDDocument.load(output.file).use { document ->
            assertEquals(rotations.size, document.numberOfPages)
            rotations.forEachIndexed { index, rotation ->
                val mergedPage = document.getPage(index)
                assertEquals(rotation, mergedPage.rotation)
                assertRectangleEquals(PDRectangle(300f, 500f), mergedPage.mediaBox)
                assertRectangleEquals(PDRectangle(10f, 20f, 280f, 460f), mergedPage.cropBox)
            }
        }
    }

    @Test
    fun interactiveFormInputFailsInsteadOfSilentlyDetachingFields() = runBlocking {
        val source = createInteractiveFormPdf("interactive-form.pdf")
        val result = PdfMergerEngine.mergePdfPages(
            context = context,
            orderedPages = listOf(page("form", "Interactive form", 0)),
            sourceFilesMap = mapOf("form" to source),
            securityConfig = PdfSecurityConfig(),
            customOutputName = "form-output",
            onProgress = { _, _ -> }
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PdfCompatibilityModeRequiredException)
        assertTrue(result.exceptionOrNull()?.message?.contains("interactive form", ignoreCase = true) == true)
    }

    @Test
    fun unreadablePdfDoesNotBecomeFakeOnePageDocument() {
        val malformed = File(root, "malformed.pdf").apply { writeText("%PDF-1.7\ntruncated") }
        assertThrows(PdfInvalidDocumentException::class.java) {
            FileUtil.getPdfPageCount(malformed)
        }
    }

    @Test
    fun invalidRequestedPageFailsInsteadOfSilentlyDisappearing() = runBlocking {
        val source = createTextPdf("one-page.pdf", listOf("ONLY_PAGE"))
        val result = PdfMergerEngine.mergePdfPages(
            context = context,
            orderedPages = listOf(page("source", "One page", 1)),
            sourceFilesMap = mapOf("source" to source),
            securityConfig = PdfSecurityConfig(),
            customOutputName = "invalid-page",
            onProgress = { _, _ -> }
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun ownedSessionCleanupIsRecursiveAndScoped() {
        val session = FileUtil.documentSessionDir(context, "doc-1")
        File(session, "nested").mkdirs()
        File(session, "nested/source.pdf").writeText("temporary")
        val unrelated = File(context.cacheDir, "unrelated.keep").apply { writeText("keep") }

        FileUtil.deleteDocumentSession(context, "doc-1")

        assertFalse(session.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun fileProviderOnlyExposesMergedOutputDirectory() {
        val allowedDir = File(context.filesDir, "merged_pdfs").apply { mkdirs() }
        val allowed = File(allowedDir, "shareable.pdf").apply { writeText("%PDF-1.4\n%%EOF") }
        val authority = "${context.packageName}.fileprovider"

        val uri = FileProvider.getUriForFile(context, authority, allowed)
        assertEquals("content", uri.scheme)

        val unrelated = File(context.filesDir, "private-secret.txt").apply { writeText("private") }
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, authority, unrelated)
        }
    }

    @Test
    fun clearOwnedWorkingFilesIncludesGeneratedSamplesButNotUnrelatedCache() {
        val session = FileUtil.documentSessionDir(context, "doc-cleanup").apply { mkdirs() }
        File(session, "source.pdf").writeText("temporary")

        val sampleDir = File(context.cacheDir, "sample_pdfs").apply { mkdirs() }
        File(sampleDir, "sample.pdf").writeText("sample")

        val unrelated = File(context.cacheDir, "unrelated.keep").apply { writeText("keep") }

        FileUtil.clearOwnedWorkingFiles(context)

        assertFalse(session.exists())
        assertFalse(sampleDir.exists())
        assertTrue(unrelated.exists())
        assertEquals(0L, FileUtil.ownedCacheSize(context))
    }

    private fun page(
        documentId: String,
        name: String,
        index: Int,
        rotation: Int = 0,
        id: String = "${documentId}_p$index"
    ): PdfPageItem =
        PdfPageItem(
            id = id,
            documentId = documentId,
            documentName = name,
            pageIndex = index,
            rotationDegrees = rotation,
            accentColor = Color.Black
        )

    private fun createInteractiveFormPdf(name: String): File {
        val file = File(root, name)
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)

            val acroForm = PDAcroForm(document)
            document.documentCatalog.setAcroForm(acroForm)
            val resources = PDResources()
            resources.put(com.tom_roush.pdfbox.cos.COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
            acroForm.setDefaultResources(resources)
            acroForm.setDefaultAppearance("/Helv 12 Tf 0 g")

            val field = PDTextField(acroForm)
            field.setPartialName("Name")
            field.setDefaultAppearance("/Helv 12 Tf 0 g")
            acroForm.fields.add(field)

            val widget = field.widgets.first()
            widget.setRectangle(PDRectangle(72f, 700f, 220f, 28f))
            widget.setPage(page)
            page.annotations.add(widget)
            field.setValue("Form Value")

            document.save(file)
        }
        return file
    }

    private fun createAnnotatedPdf(name: String): File {
        val file = File(root, name)
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(72f, 720f)
                stream.showText("ANNOTATION_LINK_FIXTURE")
                stream.endText()
            }

            val link = PDAnnotationLink().apply {
                rectangle = PDRectangle(72f, 690f, 220f, 24f)
                action = PDActionURI().apply {
                    uri = "https://example.invalid/pdf-merger-link-test"
                }
            }
            val note = PDAnnotationText().apply {
                rectangle = PDRectangle(310f, 690f, 24f, 24f)
                contents = "PDF_NOTE_ANNOTATION_8675309"
                setName(PDAnnotationText.NAME_NOTE)
            }
            page.annotations.add(link)
            page.annotations.add(note)
            document.save(file)
        }
        return file
    }

    private fun createBoxPdf(name: String): File {
        val file = File(root, name)
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(300f, 500f)).apply {
                cropBox = PDRectangle(10f, 20f, 280f, 460f)
            }
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(40f, 450f)
                stream.showText("ROTATION_BOX_FIXTURE")
                stream.endText()
            }
            document.save(file)
        }
        return file
    }

    private fun assertRectangleEquals(expected: PDRectangle, actual: PDRectangle) {
        assertEquals(expected.lowerLeftX, actual.lowerLeftX, 0.01f)
        assertEquals(expected.lowerLeftY, actual.lowerLeftY, 0.01f)
        assertEquals(expected.width, actual.width, 0.01f)
        assertEquals(expected.height, actual.height, 0.01f)
    }

    private fun createTextPdf(name: String, markers: List<String>): File {
        val file = File(root, name)
        PDDocument().use { document ->
            markers.forEach { marker ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(72f, 720f)
                    stream.showText(marker)
                    stream.endText()
                }
            }
            document.save(file)
        }
        return file
    }

    private fun extractPageTexts(file: File): List<String> {
        return PDDocument.load(file).use { document ->
            (1..document.numberOfPages).map { pageNumber ->
                PDFTextStripper().apply {
                    startPage = pageNumber
                    endPage = pageNumber
                }.getText(document)
            }
        }
    }
}
