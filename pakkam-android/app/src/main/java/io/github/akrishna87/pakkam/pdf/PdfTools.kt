package io.github.akrishna87.pakkam.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageTree
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Calendar
import java.util.IdentityHashMap

/** One page of the result of "Organize pages". */
data class PageEntry(
    /** 0 for the open document, 1… for inserted files, [BLANK] for a new empty page. */
    val source: Int,
    val pageIndex: Int,
    /** Extra clockwise turn in degrees: 0, 90, 180 or 270. */
    val rotation: Int = 0,
    val blankSize: PageSize? = null,
) {
    companion object {
        const val BLANK = -1
    }
}

enum class PaperSize(val label: String, val width: Float, val height: Float) {
    FIT("Same as the photo", 0f, 0f),
    A4("A4", 595.28f, 841.89f),
    LETTER("Letter", 612f, 792f),
    LEGAL("Legal", 612f, 1008f),
}

enum class Compression(val label: String, val detail: String, val maxSide: Int, val quality: Float) {
    LOW("Light", "Pictures stay sharp", 2400, 0.8f),
    MEDIUM("Medium", "Good for sharing", 1600, 0.65f),
    HIGH("Strong", "Smallest file, softer pictures", 1000, 0.5f),
}

data class CompressResult(val before: Long, val after: Long, val imagesChanged: Int)

enum class Corner(val label: String) {
    BOTTOM_CENTER("Bottom centre"), BOTTOM_RIGHT("Bottom right"), BOTTOM_LEFT("Bottom left"),
    TOP_CENTER("Top centre"), TOP_RIGHT("Top right"), TOP_LEFT("Top left"),
}

/** Whole-document operations: organize, combine, split, compress, passwords, watermarks, exports. */
object PdfTools {

    private fun load(file: File, password: String?) = PdfEditor.load(file, password)

    // ---- Organize ----

    fun organize(main: File, password: String?, extras: List<Pair<File, String?>>, entries: List<PageEntry>, out: File) {
        require(entries.isNotEmpty()) { "A PDF needs at least one page" }
        val docs = ArrayList<PDDocument>()
        try {
            val doc = load(main, password)
            docs += doc
            for ((f, pw) in extras) docs += load(f, pw)
            val used = HashSet<Pair<Int, Int>>()
            val pages = ArrayList<PDPage>()
            for (e in entries) {
                val page: PDPage = when {
                    e.source == PageEntry.BLANK -> {
                        val s = e.blankSize ?: PageSize(595.28f, 841.89f)
                        PDPage(PDRectangle(s.width, s.height))
                    }
                    e.source == 0 -> {
                        val orig = doc.getPage(e.pageIndex)
                        pushDownInherited(orig)
                        // The same page twice needs its own page dictionary (contents stay shared).
                        if (used.add(0 to e.pageIndex)) orig else copyPage(orig)
                    }
                    else -> {
                        val other = docs[e.source].getPage(e.pageIndex)
                        pushDownInherited(other)
                        copyPage(other)
                    }
                }
                if (e.rotation != 0) page.rotation = ((page.rotation + e.rotation) % 360 + 360) % 360
                pages += page
            }
            setPages(doc, pages)
            doc.save(out)
        } finally {
            docs.forEach { runCatching { it.close() } }
        }
    }

    private fun copyPage(p: PDPage): PDPage {
        val d = com.tom_roush.pdfbox.cos.COSDictionary(p.cosObject)
        d.removeItem(COSName.PARENT)
        // Annotations belong to one page; dropping them from copies avoids two pages sharing one note.
        d.removeItem(COSName.ANNOTS)
        return PDPage(d)
    }

    /** Copies resources, page boxes and rotation that a page inherits from the page tree into the page itself. */
    fun pushDownInherited(page: PDPage) {
        val d = page.cosObject
        for (key in listOf(COSName.RESOURCES, COSName.MEDIA_BOX, COSName.CROP_BOX, COSName.ROTATE)) {
            if (d.containsKey(key)) continue
            val v: COSBase? = PDPageTree.getInheritableAttribute(d, key)
            if (v != null) d.setItem(key, v)
        }
    }

    /** Replaces the document's page tree with [pages], in order. */
    private fun setPages(doc: PDDocument, pages: List<PDPage>) {
        val root = doc.documentCatalog.cosObject.getCOSDictionary(COSName.PAGES) ?: throw IOException("This PDF has no page list")
        val kids = COSArray()
        for (p in pages) {
            p.cosObject.setItem(COSName.PARENT, root)
            kids.add(p.cosObject)
        }
        root.setItem(COSName.KIDS, kids)
        root.setInt(COSName.COUNT, pages.size)
    }

    // ---- Combine and split ----

    /** Joins [files] into [out], keeping bookmarks and forms. Locked files must be unlocked first. */
    fun merge(files: List<File>, out: File) {
        val m = PDFMergerUtility()
        for (f in files) m.addSource(f)
        m.destinationFileName = out.path
        m.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
    }

    /** A copy of [src] with its password removed (needed before combining). */
    fun unlock(src: File, password: String?, out: File) {
        load(src, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(out)
        }
    }

    /** Writes each range of pages (0-based) as its own PDF to the stream [open] returns. */
    fun split(src: File, password: String?, ranges: List<IntRange>, open: (index: Int, range: IntRange) -> OutputStream) {
        load(src, password).use { doc ->
            for ((i, range) in ranges.withIndex()) {
                PDDocument().use { part ->
                    for (p in range) {
                        if (p !in 0 until doc.numberOfPages) continue
                        val page = doc.getPage(p)
                        pushDownInherited(page)
                        part.importPage(page)
                    }
                    part.documentInformation.title = doc.documentInformation?.title
                    open(i, range).use { part.save(it) }
                }
            }
        }
    }

    /**
     * Reads page ranges such as "1-3, 5, 8-" (1-based, as people count) into 0-based ranges.
     * Returns null when the text can't be read or a page is out of range.
     */
    fun parseRanges(text: String, pageCount: Int): List<IntRange>? {
        val out = ArrayList<IntRange>()
        for (raw in text.split(',', ';')) {
            val part = raw.trim().replace('–', '-').replace('—', '-')
            if (part.isEmpty()) continue
            val m = Regex("^(\\d*)\\s*-\\s*(\\d*)$").find(part)
            val (a, b) = if (m != null) {
                val s = m.groupValues[1].toIntOrNull() ?: 1
                val e = m.groupValues[2].toIntOrNull() ?: pageCount
                s to e
            } else {
                val n = part.toIntOrNull() ?: return null
                n to n
            }
            if (a < 1 || b > pageCount || a > b) return null
            out += (a - 1)..(b - 1)
        }
        return out.ifEmpty { null }
    }

    /** Every [n] pages as one part. */
    fun everyN(n: Int, pageCount: Int): List<IntRange> = (0 until pageCount step n.coerceAtLeast(1)).map { it..minOf(it + n - 1, pageCount - 1) }

    // ---- Create from pictures ----

    /** Makes a PDF with one picture per page. [images] are loaded one at a time to keep memory low. */
    fun imagesToPdf(images: List<() -> Bitmap?>, paper: PaperSize, marginPt: Float, out: File, onProgress: (Int) -> Unit = {}) {
        PDDocument().use { doc ->
            for ((i, load) in images.withIndex()) {
                val bmp = load() ?: continue
                val image = PdfEditor.toPdfImage(doc, bmp)
                val landscape = bmp.width > bmp.height
                val (pw, ph) = if (paper == PaperSize.FIT) {
                    // Same width as A4, height to match the picture.
                    val w = 595.28f
                    w to w * bmp.height / bmp.width
                } else if (landscape) {
                    paper.height to paper.width
                } else {
                    paper.width to paper.height
                }
                val page = PDPage(PDRectangle(pw, ph))
                doc.addPage(page)
                val m = if (paper == PaperSize.FIT) 0f else marginPt
                val boxW = pw - 2 * m
                val boxH = ph - 2 * m
                val scale = minOf(boxW / bmp.width, boxH / bmp.height)
                val w = bmp.width * scale
                val h = bmp.height * scale
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(image, (pw - w) / 2f, (ph - h) / 2f, w, h)
                }
                bmp.recycle()
                onProgress(i + 1)
            }
            if (doc.numberOfPages == 0) throw IOException("None of the pictures could be read")
            doc.documentInformation.creator = PdfEditor.AUTHOR
            doc.documentInformation.creationDate = Calendar.getInstance()
            doc.save(out)
        }
    }

    // ---- Compress ----

    fun compress(src: File, password: String?, level: Compression, out: File): CompressResult {
        var changed = 0
        load(src, password).use { doc ->
            val seen = IdentityHashMap<COSBase, PDImageXObject?>()
            for (page in doc.pages) {
                val res = page.resources ?: continue
                changed += compressResources(doc, res, seen, level, 0)
            }
            doc.save(out)
        }
        return CompressResult(src.length(), out.length(), changed)
    }

    private fun compressResources(doc: PDDocument, res: PDResources, seen: IdentityHashMap<COSBase, PDImageXObject?>, level: Compression, depth: Int): Int {
        var changed = 0
        for (name in res.xObjectNames.toList()) {
            val xo = try {
                res.getXObject(name)
            } catch (e: Exception) {
                continue
            }
            when (xo) {
                is PDImageXObject -> {
                    val key = xo.cosObject
                    val replacement = if (seen.containsKey(key)) {
                        seen[key]
                    } else {
                        recompress(doc, xo, level).also {
                            seen[key] = it
                            if (it != null) changed++
                        }
                    }
                    if (replacement != null) res.put(name, replacement)
                }
                is PDFormXObject -> if (depth < 6) xo.resources?.let { changed += compressResources(doc, it, seen, level, depth + 1) }
            }
        }
        return changed
    }

    private fun recompress(doc: PDDocument, img: PDImageXObject, level: Compression): PDImageXObject? {
        try {
            if (img.isStencil || img.bitsPerComponent == 1) return null
            if (img.softMask != null || img.cosObject.containsKey(COSName.MASK)) return null
            if (img.width.toLong() * img.height < 40_000) return null
            val bmp = img.image ?: return null
            val s = minOf(1f, level.maxSide.toFloat() / maxOf(bmp.width, bmp.height))
            val scaled = if (s < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true) else bmp
            val jpeg = JPEGFactory.createFromImage(doc, scaled, level.quality)
            if (scaled !== bmp) scaled.recycle()
            bmp.recycle()
            val before = img.cosObject.length
            val after = jpeg.cosObject.length
            return if (after < before * 0.9) jpeg else null
        } catch (e: Exception) {
            return null
        } catch (e: OutOfMemoryError) {
            return null
        }
    }

    // ---- Passwords ----

    data class Permissions(val print: Boolean = true, val copy: Boolean = true, val edit: Boolean = true, val comment: Boolean = true)

    fun protect(src: File, password: String?, openPassword: String, ownerPassword: String?, perms: Permissions, out: File) {
        load(src, password).use { doc ->
            val ap = AccessPermission().apply {
                setCanPrint(perms.print)
                setCanPrintFaithful(perms.print)
                setCanExtractContent(perms.copy)
                setCanModify(perms.edit)
                setCanAssembleDocument(perms.edit)
                setCanModifyAnnotations(perms.comment)
                setCanFillInForm(perms.comment || perms.edit)
                setCanExtractForAccessibility(true)
            }
            val owner = ownerPassword?.takeIf { it.isNotEmpty() } ?: randomPassword()
            val policy = StandardProtectionPolicy(owner, openPassword, ap)
            policy.encryptionKeyLength = 256
            doc.protect(policy)
            doc.save(out)
        }
    }

    private fun randomPassword(): String {
        val r = SecureRandom()
        return (1..24).map { "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"[r.nextInt(56)] }.joinToString("")
    }

    fun isEncrypted(src: File, password: String?): Boolean = load(src, password).use { it.isEncrypted }

    // ---- Watermark and page numbers ----

    data class WatermarkStyle(
        val text: String,
        val sizePt: Float = 60f,
        val color: Int = 0xFFC62828.toInt(),
        val opacity: Float = 0.25f,
        val angle: Float = 45f,
        val pages: List<IntRange>? = null,
    )

    fun watermark(src: File, password: String?, style: WatermarkStyle, out: File) {
        load(src, password).use { doc ->
            val font = PdfEditor.fontFor(doc, style.text)
            val gs = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = style.opacity
                strokingAlphaConstant = style.opacity
            }
            for ((i, page) in doc.pages.withIndex()) {
                if (style.pages != null && style.pages.none { i in it }) continue
                val (w, h) = shownSize(page)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.transform(shownToPage(page))
                    cs.setGraphicsStateParameters(gs)
                    cs.setNonStrokingColor(PdfEditor.pdColor(style.color))
                    val textWidth = font.getStringWidth(style.text) / 1000f * style.sizePt
                    cs.beginText()
                    cs.setFont(font, style.sizePt)
                    val rad = Math.toRadians(style.angle.toDouble())
                    val c = Math.cos(rad).toFloat()
                    val s = Math.sin(rad).toFloat()
                    // Centre the text's middle on the page's middle, then turn it.
                    val cx = w / 2f
                    val cy = h / 2f
                    val dx = -textWidth / 2f
                    val dy = -style.sizePt * 0.35f
                    cs.setTextMatrix(Matrix(c, s, -s, c, cx + dx * c - dy * s, cy + dx * s + dy * c))
                    cs.showText(style.text)
                    cs.endText()
                }
            }
            doc.save(out)
        }
    }

    data class NumberStyle(
        val corner: Corner = Corner.BOTTOM_CENTER,
        /** "{n}" is the page number, "{total}" the number of pages. */
        val format: String = "{n}",
        val firstNumber: Int = 1,
        val skipFirst: Boolean = false,
        val sizePt: Float = 11f,
        val marginPt: Float = 28f,
    )

    fun pageNumbers(src: File, password: String?, style: NumberStyle, out: File) {
        load(src, password).use { doc ->
            val total = doc.numberOfPages
            val font = PdfEditor.fontFor(doc, style.format.replace("{n}", "0").replace("{total}", "0"))
            for ((i, page) in doc.pages.withIndex()) {
                if (style.skipFirst && i == 0) continue
                val number = style.firstNumber + i - if (style.skipFirst) 1 else 0
                val label = style.format.replace("{n}", number.toString()).replace("{total}", (total + style.firstNumber - 1).toString())
                val (w, h) = shownSize(page)
                val tw = font.getStringWidth(label) / 1000f * style.sizePt
                val x = when (style.corner) {
                    Corner.BOTTOM_LEFT, Corner.TOP_LEFT -> style.marginPt
                    Corner.BOTTOM_RIGHT, Corner.TOP_RIGHT -> w - style.marginPt - tw
                    else -> (w - tw) / 2f
                }
                val y = when (style.corner) {
                    Corner.TOP_LEFT, Corner.TOP_CENTER, Corner.TOP_RIGHT -> h - style.marginPt - style.sizePt * 0.8f
                    else -> style.marginPt
                }
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.transform(shownToPage(page))
                    cs.setNonStrokingColor(0.15f)
                    cs.beginText()
                    cs.setFont(font, style.sizePt)
                    cs.newLineAtOffset(x, y)
                    cs.showText(label)
                    cs.endText()
                }
            }
            doc.save(out)
        }
    }

    /** The page's width and height as it is shown (crop box, turned by /Rotate). */
    fun shownSize(page: PDPage): Pair<Float, Float> {
        val box = page.cropBox
        val turned = page.rotation % 180 != 0
        return if (turned) box.height to box.width else box.width to box.height
    }

    /**
     * Maps coordinates on the page as shown (origin bottom left, y up) to page space, taking the
     * crop box and the page's turn into account.
     */
    fun shownToPage(page: PDPage): Matrix {
        val b = page.cropBox
        val llx = b.lowerLeftX
        val lly = b.lowerLeftY
        val w = b.width
        val h = b.height
        return when ((page.rotation % 360 + 360) % 360) {
            90 -> Matrix(0f, 1f, -1f, 0f, llx + w, lly)
            180 -> Matrix(-1f, 0f, 0f, -1f, llx + w, lly + h)
            270 -> Matrix(0f, -1f, 1f, 0f, llx, lly + h)
            else -> Matrix(1f, 0f, 0f, 1f, llx, lly)
        }
    }

    // ---- Document info ----

    fun info(src: File, password: String?): Map<String, String> = load(src, password).use { doc ->
        val i = doc.documentInformation
        mapOf(
            "Title" to i.title.orEmpty(),
            "Author" to i.author.orEmpty(),
            "Subject" to i.subject.orEmpty(),
            "Keywords" to i.keywords.orEmpty(),
        )
    }

    fun setInfo(src: File, password: String?, values: Map<String, String>, out: File) {
        load(src, password).use { doc ->
            val i = doc.documentInformation
            values["Title"]?.let { i.title = it.ifBlank { null } }
            values["Author"]?.let { i.author = it.ifBlank { null } }
            values["Subject"]?.let { i.subject = it.ifBlank { null } }
            values["Keywords"]?.let { i.keywords = it.ifBlank { null } }
            i.modificationDate = Calendar.getInstance()
            doc.save(out)
        }
    }

    // ---- Export ----

    /** The text of each page, in reading order. */
    fun pageTexts(src: File, password: String?): List<String> = load(src, password).use { doc ->
        val stripper = PDFTextStripper()
        stripper.sortByPosition = true
        (1..doc.numberOfPages).map { p ->
            stripper.startPage = p
            stripper.endPage = p
            try {
                stripper.getText(doc)
            } catch (e: Exception) {
                ""
            }
        }
    }

    fun exportText(src: File, password: String?, out: OutputStream) {
        val pages = pageTexts(src, password)
        out.bufferedWriter().use { w ->
            for ((i, t) in pages.withIndex()) {
                if (i > 0) w.write("\n\n")
                w.write("— Page ${i + 1} —\n\n")
                w.write(t.trim())
            }
            w.write("\n")
        }
    }

    fun exportDocx(src: File, password: String?, title: String, out: OutputStream) {
        Docx.write(title, pageTexts(src, password), out)
    }
}
