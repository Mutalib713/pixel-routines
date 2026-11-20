package com.mosman.routines.ui.lens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads the text out of whatever the user attaches: a photo or image (on-device ML Kit, the
 * bundled model, so it works offline), a PDF (each page rendered, then read), or a plain-text
 * file. It does NOT decide what routine to make: that is RoutineAIParser's job, the same one the
 * chat uses, so a picture with no schedule in it never turns into a routine.
 *
 * (Antigravity's version made a routine from any text at all, filling in 08:00–17:00 when the
 * picture had no times; a random photo on the Pixel produced a routine, 2026-11-20.)
 */
/** An attachment ready to send to Gemini: exactly one of [bitmap], [pdfBytes], [text] is set. */
class Attachment(
    val name: String,
    val kind: RoutineLensProcessor.Kind,
    val bitmap: Bitmap? = null,
    val pdfBytes: ByteArray? = null,
    val text: String? = null,
)

object RoutineLensProcessor {

    /** PDFs: read at most this many pages, so a long document can't stall the phone. */
    private const val MAX_PDF_PAGES = 5

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** What kind of thing was attached, from its MIME type. */
    enum class Kind { IMAGE, PDF, TEXT, UNSUPPORTED }

    fun kindOf(context: Context, uri: Uri): Kind {
        val type = context.contentResolver.getType(uri).orEmpty()
        return when {
            type.startsWith("image/") -> Kind.IMAGE
            type == "application/pdf" -> Kind.PDF
            type.startsWith("text/") -> Kind.TEXT
            else -> Kind.UNSUPPORTED
        }
    }

    /** The text found in the attachment (blank if none). Fails with a plain-English message. */
    suspend fun recognizeUri(context: Context, uri: Uri): Result<String> = runCatching {
        when (kindOf(context, uri)) {
            Kind.IMAGE -> recognizeText(InputImage.fromFilePath(context, uri))
            Kind.PDF -> readPdf(context, uri)
            Kind.TEXT -> withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(20_000) }.orEmpty()
            }
            Kind.UNSUPPORTED -> throw IllegalArgumentException("that kind of file isn't supported. Use a photo, an image, a PDF or a text file")
        }
    }

    /**
     * The attachment as it would go to Gemini, only after the user said yes for this one file:
     * an image shrunk to at most 1600 px on its long side (plenty for a timetable, small on mobile
     * data), a PDF as its own bytes (Gemini reads PDFs directly; capped at 15 MB), or a text file's text.
     */
    suspend fun loadForGemini(context: Context, uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val name = displayName(context, uri)
        when (kindOf(context, uri)) {
            Kind.IMAGE -> {
                val src = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                val bmp = android.graphics.ImageDecoder.decodeBitmap(src) { d, info, _ ->
                    val longSide = maxOf(info.size.width, info.size.height)
                    if (longSide > 1600) {
                        val f = 1600f / longSide
                        d.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
                    }
                    d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                }
                Attachment(name, Kind.IMAGE, bitmap = bmp)
            }
            Kind.PDF -> {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("couldn't open the PDF")
                if (bytes.size > 15 * 1024 * 1024) throw IllegalArgumentException("that PDF is over 15 MB, too big to send")
                Attachment(name, Kind.PDF, pdfBytes = bytes)
            }
            Kind.TEXT -> Attachment(name, Kind.TEXT, text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText().take(20_000) }.orEmpty())
            Kind.UNSUPPORTED -> throw IllegalArgumentException("that kind of file isn't supported")
        }
    }

    fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "file"

    private suspend fun readPdf(context: Context, uri: Uri): String {
        val pages = withContext(Dispatchers.IO) {
            val fd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("couldn't open the PDF")
            fd.use {
                PdfRenderer(it).use { pdf ->
                    (0 until minOf(pdf.pageCount, MAX_PDF_PAGES)).map { i ->
                        pdf.openPage(i).use { page ->
                            // About 150 dpi: sharp enough for ML Kit, small enough for memory.
                            val scale = 150f / 72f
                            val bmp = Bitmap.createBitmap(
                                (page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888,
                            )
                            bmp.eraseColor(Color.WHITE) // PDFs are transparent by default; OCR needs a background
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }
        }
        val out = StringBuilder()
        for (bmp in pages) {
            try { out.appendLine(recognizeText(InputImage.fromBitmap(bmp, 0))) } finally { bmp.recycle() }
        }
        return out.toString()
    }

    private suspend fun recognizeText(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { visionText -> cont.resume(visionText.text) }
                .addOnFailureListener { exception -> cont.resumeWithException(exception) }
        }
}
