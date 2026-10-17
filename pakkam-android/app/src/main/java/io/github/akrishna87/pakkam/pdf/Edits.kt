package io.github.akrishna87.pakkam.pdf

import android.graphics.PointF
import java.io.File

/**
 * A change drawn on a page that hasn't been written into the PDF yet. Points and rectangles are
 * in page space, so they stay put whatever the zoom. All of them are written as standard PDF
 * annotations (except white-out, which is painted onto the page), so other readers show them too.
 */
sealed interface Edit {
    val page: Int
}

enum class MarkupKind(val label: String) { HIGHLIGHT("Highlight"), UNDERLINE("Underline"), STRIKEOUT("Strikethrough") }

data class MarkupEdit(
    override val page: Int,
    val kind: MarkupKind,
    val rects: List<PdfRect>,
    val color: Int,
    val text: String,
    /** The reader's own comment on it; when there is none, the marked text is kept as the comment. */
    val note: String = "",
) : Edit

data class InkEdit(
    override val page: Int,
    val strokes: List<List<PointF>>,
    val color: Int,
    /** Line width in points. */
    val width: Float,
    val opacity: Float = 1f,
) : Edit

enum class ShapeKind(val label: String) { RECTANGLE("Rectangle"), OVAL("Oval"), LINE("Line"), ARROW("Arrow") }

data class ShapeEdit(
    override val page: Int,
    val kind: ShapeKind,
    val start: PointF,
    val end: PointF,
    val color: Int,
    val width: Float,
) : Edit {
    val bounds get() = PdfRect(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y))
}

data class NoteEdit(
    override val page: Int,
    val at: PointF,
    val text: String,
    val color: Int,
) : Edit

/** Typed text in a box. [rect] is in page space; [sizePt] is the font size in points. */
data class TextEdit(
    override val page: Int,
    val rect: PdfRect,
    val text: String,
    val sizePt: Float,
    val color: Int,
    /** The page's own turn, so the text can be kept upright. */
    val rotation: Int,
) : Edit

/** A picture placed on the page: a signature, initials or any image. */
data class ImageEdit(
    override val page: Int,
    val rect: PdfRect,
    val image: File,
    val rotation: Int,
    val label: String,
) : Edit

/** A white box painted over part of the page. */
data class WhiteoutEdit(
    override val page: Int,
    val rect: PdfRect,
) : Edit

/** Removes an annotation that is already in the file. */
data class DeleteAnnotEdit(
    override val page: Int,
    val annot: AnnotInfo,
) : Edit

/** An annotation already in the file, as listed by [PdfEditor.listAnnotations]. */
data class AnnotInfo(
    val page: Int,
    /** Position in the page's /Annots array when it was read. */
    val index: Int,
    val subtype: String,
    val rect: PdfRect,
    val contents: String,
    val author: String,
    val color: Int?,
    /** For highlights and other text markup: the box of each marked line. */
    val quads: List<PdfRect> = emptyList(),
) {
    val label: String
        get() = when (subtype) {
            "Highlight" -> "Highlight"
            "Underline" -> "Underline"
            "StrikeOut" -> "Strikethrough"
            "Squiggly" -> "Squiggly underline"
            "Ink" -> "Drawing"
            "Text" -> "Note"
            "FreeText" -> "Text"
            "Square" -> "Rectangle"
            "Circle" -> "Oval"
            "Line" -> "Line"
            "Stamp" -> "Stamp or signature"
            "Polygon" -> "Polygon"
            "PolyLine" -> "Lines"
            "FileAttachment" -> "Attached file"
            else -> subtype
        }
}
