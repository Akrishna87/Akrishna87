package io.github.akrishna87.pakkam.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLine
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationRubberStamp
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** A fillable field of a PDF form. */
data class FormField(
    val name: String,
    val label: String,
    val kind: Kind,
    val value: String,
    val options: List<String>,
    val readOnly: Boolean,
    val multiline: Boolean,
) {
    enum class Kind { TEXT, CHECKBOX, RADIO, CHOICE }
}

/**
 * Writes changes into a PDF with PDFBox. Each function reads [src] and writes a new file, so the
 * file on screen is never half-written.
 */
object PdfEditor {
    const val AUTHOR = "Pakkam"

    fun load(file: File, password: String?): PDDocument =
        PDDocument.load(file, password ?: "", MemoryUsageSetting.setupTempFileOnly())

    /** Writes [edits] into a copy of [src] at [out]. */
    fun applyEdits(src: File, password: String?, edits: List<Edit>, out: File) {
        load(src, password).use { doc ->
            // Deletions refer to positions in the file as it was read, so do them before anything is added.
            val deletions = edits.filterIsInstance<DeleteAnnotEdit>()
            for (d in deletions.sortedByDescending { it.annot.index }) deleteAnnotation(doc, d.annot)
            for (e in edits) {
                if (e.page !in 0 until doc.numberOfPages) continue
                val page = doc.getPage(e.page)
                when (e) {
                    is MarkupEdit -> addMarkup(doc, page, e)
                    is InkEdit -> addInk(doc, page, e)
                    is ShapeEdit -> addShape(doc, page, e)
                    is NoteEdit -> addNote(doc, page, e)
                    is TextEdit -> addText(doc, page, e)
                    is ImageEdit -> addImage(doc, page, e)
                    is WhiteoutEdit -> addWhiteout(doc, page, e)
                    is DeleteAnnotEdit -> Unit
                }
            }
            doc.save(out)
        }
    }

    // ---- Reading ----

    fun listAnnotations(src: File, password: String?): List<AnnotInfo> = load(src, password).use { doc ->
        val out = ArrayList<AnnotInfo>()
        for ((pageIndex, page) in doc.pages.withIndex()) {
            val annots = try {
                page.annotations
            } catch (e: IOException) {
                continue
            }
            for ((i, a) in annots.withIndex()) {
                val subtype = a.subtype ?: continue
                if (subtype in SKIPPED_SUBTYPES || a.isHidden) continue
                val r = a.rectangle ?: continue
                out += AnnotInfo(
                    page = pageIndex,
                    index = i,
                    subtype = subtype,
                    rect = PdfRect(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY),
                    contents = a.contents.orEmpty().trim(),
                    author = (a as? PDAnnotationMarkup)?.titlePopup.orEmpty(),
                    color = try {
                        a.color?.toRGB()?.let { it or (0xFF shl 24) }
                    } catch (e: Exception) {
                        null
                    },
                    quads = quadsOf(a),
                )
            }
        }
        out
    }

    private val SKIPPED_SUBTYPES = setOf("Link", "Popup", "Widget")

    /** The boxes a text markup covers, one per group of four corners in its /QuadPoints. */
    private fun quadsOf(a: PDAnnotation): List<PdfRect> {
        val arr = a.cosObject.getDictionaryObject(COSName.getPDFName("QuadPoints")) as? COSArray ?: return emptyList()
        val v = (0 until arr.size()).map { (arr.getObject(it) as? com.tom_roush.pdfbox.cos.COSNumber)?.floatValue() ?: return emptyList() }
        return v.chunked(8).filter { it.size == 8 }.map { q ->
            val xs = listOf(q[0], q[2], q[4], q[6])
            val ys = listOf(q[1], q[3], q[5], q[7])
            PdfRect(xs.min(), ys.min(), xs.max(), ys.max())
        }
    }

    private fun deleteAnnotation(doc: PDDocument, info: AnnotInfo) {
        if (info.page !in 0 until doc.numberOfPages) return
        val page = doc.getPage(info.page)
        val annots = page.annotations
        fun matches(a: PDAnnotation): Boolean {
            val r = a.rectangle ?: return false
            return a.subtype == info.subtype &&
                kotlin.math.abs(r.lowerLeftX - info.rect.left) < 0.5f &&
                kotlin.math.abs(r.lowerLeftY - info.rect.bottom) < 0.5f
        }
        val target = annots.getOrNull(info.index)?.takeIf { matches(it) } ?: annots.firstOrNull { matches(it) } ?: return
        // A note's pop-up window goes with it.
        val popup = target.cosObject.getCOSDictionary(COSName.getPDFName("Popup"))
        page.annotations = annots.filter { it !== target && (popup == null || it.cosObject != popup) }
    }

    // ---- Annotations ----

    private fun stamp(a: PDAnnotation, color: Int?) {
        a.annotationName = UUID.randomUUID().toString()
        a.setModifiedDate(Calendar.getInstance())
        a.isPrinted = true
        if (color != null) a.color = pdColor(color)
        if (a is PDAnnotationMarkup) {
            a.titlePopup = AUTHOR
            a.creationDate = Calendar.getInstance()
        }
    }

    private fun addMarkup(doc: PDDocument, page: PDPage, e: MarkupEdit) {
        if (e.rects.isEmpty()) return
        val subtype = when (e.kind) {
            MarkupKind.HIGHLIGHT -> PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
            MarkupKind.UNDERLINE -> PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
            MarkupKind.STRIKEOUT -> PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT
        }
        val a = PDAnnotationTextMarkup(subtype)
        stamp(a, e.color)
        val bounds = e.rects.reduce { acc, r -> acc.union(r) }.inset(1f)
        a.rectangle = bounds.toPD()
        // Each line's corners: upper left, upper right, lower left, lower right (the order Acrobat uses).
        a.quadPoints = e.rects.flatMap { r ->
            listOf(r.left, r.top, r.right, r.top, r.left, r.bottom, r.right, r.bottom)
        }.toFloatArray()
        val comment = e.note.ifBlank { e.text }
        if (comment.isNotBlank()) a.contents = comment
        a.constructAppearances(doc)
        addAnnotation(page, a)
    }

    private fun addInk(doc: PDDocument, page: PDPage, e: InkEdit) {
        val strokes = e.strokes.filter { it.isNotEmpty() }
        if (strokes.isEmpty()) return
        val a = PDAnnotationMarkup()
        a.cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_INK)
        stamp(a, e.color)
        a.borderStyle = PDBorderStyleDictionary().apply { width = e.width }
        if (e.opacity < 1f) a.constantOpacity = e.opacity
        a.inkList = strokes.map { s -> s.flatMap { listOf(it.x, it.y) }.toFloatArray() }.toTypedArray()
        var bounds = PdfRect(strokes[0][0].x, strokes[0][0].y, strokes[0][0].x, strokes[0][0].y)
        for (s in strokes) for (p in s) bounds = bounds.union(PdfRect(p.x, p.y, p.x, p.y))
        val rect = bounds.inset(e.width)
        a.rectangle = rect.toPD()

        val ap = appearance(doc, rect)
        PDPageContentStream(doc, ap).use { cs ->
            if (e.opacity < 1f) cs.setGraphicsStateParameters(alpha(e.opacity))
            cs.setStrokingColor(pdColor(e.color))
            cs.setLineWidth(e.width)
            cs.setLineCapStyle(1)
            cs.setLineJoinStyle(1)
            for (s in strokes) {
                cs.moveTo(s[0].x, s[0].y)
                if (s.size == 1) cs.lineTo(s[0].x + 0.01f, s[0].y)
                for (i in 1 until s.size) cs.lineTo(s[i].x, s[i].y)
                cs.stroke()
            }
        }
        a.appearance = PDAppearanceDictionary().apply { setNormalAppearance(ap) }
        addAnnotation(page, a)
    }

    private fun addShape(doc: PDDocument, page: PDPage, e: ShapeEdit) {
        val w = e.width
        when (e.kind) {
            ShapeKind.RECTANGLE, ShapeKind.OVAL -> {
                val a = PDAnnotationSquareCircle(
                    if (e.kind == ShapeKind.RECTANGLE) PDAnnotationSquareCircle.SUB_TYPE_SQUARE else PDAnnotationSquareCircle.SUB_TYPE_CIRCLE,
                )
                stamp(a, e.color)
                a.borderStyle = PDBorderStyleDictionary().apply { width = w }
                val rect = e.bounds.inset(w / 2f)
                a.rectangle = rect.toPD()
                val ap = appearance(doc, rect)
                PDPageContentStream(doc, ap).use { cs ->
                    cs.setStrokingColor(pdColor(e.color))
                    cs.setLineWidth(w)
                    val r = e.bounds
                    if (e.kind == ShapeKind.RECTANGLE) {
                        cs.addRect(r.left, r.bottom, r.width, r.height)
                    } else {
                        ellipse(cs, r)
                    }
                    cs.stroke()
                }
                a.appearance = PDAppearanceDictionary().apply { setNormalAppearance(ap) }
                addAnnotation(page, a)
            }
            ShapeKind.LINE, ShapeKind.ARROW -> {
                val a = PDAnnotationLine()
                stamp(a, e.color)
                a.line = floatArrayOf(e.start.x, e.start.y, e.end.x, e.end.y)
                a.borderStyle = PDBorderStyleDictionary().apply { width = w }
                if (e.kind == ShapeKind.ARROW) a.endPointEndingStyle = PDAnnotationLine.LE_OPEN_ARROW
                val head = arrowHead(e.start.x, e.start.y, e.end.x, e.end.y, w)
                var bounds = e.bounds
                if (e.kind == ShapeKind.ARROW) for (i in head.indices step 2) bounds = bounds.union(PdfRect(head[i], head[i + 1], head[i], head[i + 1]))
                val rect = bounds.inset(w * 2)
                a.rectangle = rect.toPD()
                val ap = appearance(doc, rect)
                PDPageContentStream(doc, ap).use { cs ->
                    cs.setStrokingColor(pdColor(e.color))
                    cs.setLineWidth(w)
                    cs.setLineCapStyle(1)
                    cs.setLineJoinStyle(1)
                    cs.moveTo(e.start.x, e.start.y)
                    cs.lineTo(e.end.x, e.end.y)
                    cs.stroke()
                    if (e.kind == ShapeKind.ARROW) {
                        cs.moveTo(head[0], head[1])
                        cs.lineTo(e.end.x, e.end.y)
                        cs.lineTo(head[2], head[3])
                        cs.stroke()
                    }
                }
                a.appearance = PDAppearanceDictionary().apply { setNormalAppearance(ap) }
                addAnnotation(page, a)
            }
        }
    }

    /** The two outer points of an arrow head at (x2, y2). */
    fun arrowHead(x1: Float, y1: Float, x2: Float, y2: Float, width: Float): FloatArray {
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val len = (width * 4f).coerceAtLeast(8f)
        val spread = Math.toRadians(28.0)
        return floatArrayOf(
            (x2 - len * cos(angle - spread)).toFloat(), (y2 - len * sin(angle - spread)).toFloat(),
            (x2 - len * cos(angle + spread)).toFloat(), (y2 - len * sin(angle + spread)).toFloat(),
        )
    }

    private fun ellipse(cs: PDPageContentStream, r: PdfRect) {
        val k = 0.5523f
        val cx = (r.left + r.right) / 2f
        val cy = (r.bottom + r.top) / 2f
        val rx = r.width / 2f
        val ry = r.height / 2f
        cs.moveTo(cx + rx, cy)
        cs.curveTo(cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry)
        cs.curveTo(cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy)
        cs.curveTo(cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry)
        cs.curveTo(cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy)
        cs.closePath()
    }

    private fun addNote(doc: PDDocument, page: PDPage, e: NoteEdit) {
        val a = PDAnnotationText()
        stamp(a, e.color)
        a.name = PDAnnotationText.NAME_COMMENT
        a.contents = e.text
        val size = 20f
        a.rectangle = PDRectangle(e.at.x, e.at.y - size, size, size)
        // Notes keep their size and stay upright when zooming or turning, like in Acrobat.
        a.isNoZoom = true
        a.isNoRotate = true
        a.constructAppearances(doc)
        addAnnotation(page, a)
    }

    private fun addText(doc: PDDocument, page: PDPage, e: TextEdit) {
        val a = PDAnnotationMarkup()
        a.cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_FREETEXT)
        stamp(a, null)
        a.contents = e.text
        a.rectangle = e.rect.toPD()
        val (r, g, b) = rgb(e.color)
        a.defaultAppearance = "/Helv ${fmt(e.sizePt)} Tf ${fmt(r)} ${fmt(g)} ${fmt(b)} rg"
        a.borderStyle = PDBorderStyleDictionary().apply { width = 0f }

        val font = fontFor(doc, e.text)
        val turned = e.rotation == 90 || e.rotation == 270
        val w = if (turned) e.rect.height else e.rect.width
        val h = if (turned) e.rect.width else e.rect.height
        val ap = appearance(doc, PdfRect(0f, 0f, w, h), e.rotation)
        PDPageContentStream(doc, ap).use { cs ->
            cs.setNonStrokingColor(pdColor(e.color))
            val lines = e.text.split('\n')
            val leading = e.sizePt * TEXT_LEADING
            cs.beginText()
            cs.setFont(font, e.sizePt)
            cs.setLeading(leading)
            cs.newLineAtOffset(TEXT_PADDING, h - TEXT_PADDING - e.sizePt * TEXT_ASCENT)
            for ((i, line) in lines.withIndex()) {
                if (i > 0) cs.newLine()
                cs.showText(cleanForFont(line))
            }
            cs.endText()
        }
        a.appearance = PDAppearanceDictionary().apply { setNormalAppearance(ap) }
        addAnnotation(page, a)
    }

    private fun addImage(doc: PDDocument, page: PDPage, e: ImageEdit) {
        val bmp = BitmapFactory.decodeFile(e.image.path) ?: throw IOException("The picture couldn't be read")
        val image = toPdfImage(doc, bmp)
        val a = PDAnnotationRubberStamp()
        stamp(a, null)
        a.contents = e.label
        a.rectangle = e.rect.toPD()
        val turned = e.rotation == 90 || e.rotation == 270
        val w = if (turned) e.rect.height else e.rect.width
        val h = if (turned) e.rect.width else e.rect.height
        val ap = appearance(doc, PdfRect(0f, 0f, w, h), e.rotation)
        PDPageContentStream(doc, ap).use { cs -> cs.drawImage(image, 0f, 0f, w, h) }
        a.appearance = PDAppearanceDictionary().apply { setNormalAppearance(ap) }
        addAnnotation(page, a)
    }

    /** Pictures with see-through parts (signatures) stay lossless; photos become JPEG. */
    fun toPdfImage(doc: PDDocument, bmp: Bitmap): PDImageXObject {
        val max = 2000
        val scaled = if (bmp.width > max || bmp.height > max) {
            val s = max.toFloat() / maxOf(bmp.width, bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
        } else {
            bmp
        }
        return if (scaled.hasAlpha()) LosslessFactory.createFromImage(doc, scaled) else JPEGFactory.createFromImage(doc, scaled, 0.85f)
    }

    private fun addWhiteout(doc: PDDocument, page: PDPage, e: WhiteoutEdit) {
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.setNonStrokingColor(1f)
            cs.addRect(e.rect.left, e.rect.bottom, e.rect.width, e.rect.height)
            cs.fill()
        }
    }

    // ---- Forms ----

    fun listFormFields(src: File, password: String?): List<FormField> = load(src, password).use { doc ->
        val form = doc.documentCatalog.acroForm ?: return@use emptyList()
        val out = ArrayList<FormField>()
        for (f in form.fieldTree) {
            val name = f.fullyQualifiedName ?: continue
            val label = (f.alternateFieldName?.takeIf { it.isNotBlank() } ?: f.partialName ?: name).trim()
            val field = when (f) {
                is PDTextField -> FormField(name, label, FormField.Kind.TEXT, f.value.orEmpty(), emptyList(), f.isReadOnly, f.isMultiline)
                is PDCheckBox -> FormField(name, label, FormField.Kind.CHECKBOX, if (f.isChecked) "true" else "false", emptyList(), f.isReadOnly, false)
                is PDRadioButton -> FormField(name, label, FormField.Kind.RADIO, f.value.orEmpty(), f.onValues.toList().sorted(), f.isReadOnly, false)
                is PDChoice -> FormField(name, label, FormField.Kind.CHOICE, f.value.firstOrNull().orEmpty(), f.options, f.isReadOnly, false)
                is PDSignatureField -> null
                else -> null
            }
            if (field != null) out += field
        }
        out
    }

    /** Fills the form with [values] (field name → value) and returns the names of fields that couldn't be filled. */
    fun fillForm(src: File, password: String?, values: Map<String, String>, out: File): List<String> {
        val failed = ArrayList<String>()
        load(src, password).use { doc ->
            val form = doc.documentCatalog.acroForm ?: return emptyList()
            for ((name, value) in values) {
                val f: PDField = form.getField(name) ?: continue
                try {
                    when (f) {
                        is PDTextField -> f.value = value
                        is PDCheckBox -> if (value == "true") f.check() else f.unCheck()
                        is PDRadioButton -> if (value.isNotEmpty()) f.value = value
                        is PDChoice -> f.setValue(value)
                    }
                } catch (e: Exception) {
                    failed += name
                }
            }
            doc.save(out)
        }
        return failed
    }

    // ---- Flatten ----

    /** Paints every annotation and form field onto its page, so it can no longer be changed or removed. */
    fun flatten(src: File, password: String?, out: File) {
        load(src, password).use { doc ->
            try {
                doc.documentCatalog.acroForm?.flatten()
            } catch (e: Exception) {
                // Some forms can't be flattened by PDFBox; their widgets are painted below like any annotation.
            }
            for (page in doc.pages) {
                val annots = page.annotations
                if (annots.isEmpty()) continue
                val keep = ArrayList<PDAnnotation>()
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (a in annots) {
                        if (a.subtype == "Link") {
                            keep += a
                            continue
                        }
                        if (a.subtype == "Popup" || a.isHidden || a.isNoView) continue
                        val ap = a.normalAppearanceStream ?: continue
                        val rect = a.rectangle ?: continue
                        val bbox = ap.bBox ?: continue
                        val m = ap.matrix
                        // Where the appearance's box lands after its own matrix…
                        val corners = listOf(
                            m.transformPoint(bbox.lowerLeftX, bbox.lowerLeftY),
                            m.transformPoint(bbox.upperRightX, bbox.lowerLeftY),
                            m.transformPoint(bbox.lowerLeftX, bbox.upperRightY),
                            m.transformPoint(bbox.upperRightX, bbox.upperRightY),
                        )
                        val minX = corners.minOf { it.x }
                        val minY = corners.minOf { it.y }
                        val tw = corners.maxOf { it.x } - minX
                        val th = corners.maxOf { it.y } - minY
                        if (tw <= 0f || th <= 0f) continue
                        // …then stretched onto the annotation's rectangle (PDF 1.7, section 12.5.5).
                        val sx = rect.width / tw
                        val sy = rect.height / th
                        cs.saveGraphicsState()
                        cs.transform(Matrix(sx, 0f, 0f, sy, rect.lowerLeftX - minX * sx, rect.lowerLeftY - minY * sy))
                        cs.drawForm(ap)
                        cs.restoreGraphicsState()
                    }
                }
                page.annotations = keep
            }
            doc.save(out)
        }
    }

    // ---- Helpers ----

    private fun addAnnotation(page: PDPage, a: PDAnnotation) {
        a.page = page
        page.annotations = page.annotations.toMutableList().apply { add(a) }
    }

    const val TEXT_PADDING = 2f
    const val TEXT_LEADING = 1.2f
    const val TEXT_ASCENT = 0.8f

    /**
     * An appearance drawn in a box of [box] (page space when [rotation] is 0). For turned pages the
     * box is upright as shown and the stream's matrix turns it back, so text and pictures read the right way up.
     */
    private fun appearance(doc: PDDocument, box: PdfRect, rotation: Int = 0): PDAppearanceStream {
        val ap = PDAppearanceStream(doc)
        ap.bBox = box.toPD()
        ap.resources = PDResources()
        val m = when (rotation) {
            90 -> floatArrayOf(0f, 1f, -1f, 0f, 0f, 0f)
            180 -> floatArrayOf(-1f, 0f, 0f, -1f, 0f, 0f)
            270 -> floatArrayOf(0f, -1f, 1f, 0f, 0f, 0f)
            else -> null
        }
        if (m != null) ap.cosObject.setItem(COSName.MATRIX, COSArray().apply { m.forEach { add(COSFloat(it)) } })
        return ap
    }

    private fun alpha(a: Float) = PDExtendedGraphicsState().apply {
        strokingAlphaConstant = a
        nonStrokingAlphaConstant = a
    }

    fun pdColor(argb: Int): PDColor {
        val (r, g, b) = rgb(argb)
        return PDColor(floatArrayOf(r, g, b), PDDeviceRGB.INSTANCE)
    }

    private fun rgb(argb: Int) = Triple(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f)

    private fun fmt(f: Float) = String.format(java.util.Locale.US, "%.3f", f).trimEnd('0').trimEnd('.')

    fun PdfRect.toPD() = PDRectangle(left, bottom, width.coerceAtLeast(0.01f), height.coerceAtLeast(0.01f))

    private fun cleanForFont(s: String) = s.replace("\t", "    ").filter { it >= ' ' }

    /** Helvetica for plain Latin text; otherwise a phone font that has every character, embedded. */
    fun fontFor(doc: PDDocument, text: String): PDFont {
        val sample = cleanForFont(text.replace("\n", ""))
        try {
            PDType1Font.HELVETICA.encode(sample)
            return PDType1Font.HELVETICA
        } catch (e: Exception) {
            // Needs a font with more characters.
        }
        for (path in SYSTEM_FONTS) {
            val f = File(path)
            if (!f.exists()) continue
            try {
                val font = PDType0Font.load(doc, f)
                font.encode(sample)
                return font
            } catch (e: Exception) {
                continue
            }
        }
        throw IOException("This phone has no font for some of the characters in the text")
    }

    private val SYSTEM_FONTS = listOf(
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/DroidSans.ttf",
        "/system/fonts/NotoSansTamil-Regular.ttf",
        "/system/fonts/NotoSansDevanagari-Regular.ttf",
        "/system/fonts/NotoSansCJK-Regular.ttc",
    )
}
