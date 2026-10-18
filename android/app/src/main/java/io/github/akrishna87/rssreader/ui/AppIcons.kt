package io.github.akrishna87.rssreader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line icons in the spirit of Apple's SF Symbols: thin rounded strokes on a
 * 24dp grid. Tinted like any other icon via Icon(tint = …).
 */
object AppIcons {
    val ChevronLeft = stroke("ChevronLeft", 2.4f, "M15,5L8,12L15,19")
    val ChevronRight = stroke("ChevronRight", 2.2f, "M9.5,6L15.5,12L9.5,18")
    val ChevronDown = stroke("ChevronDown", 2.2f, "M6,9.5L12,15.5L18,9.5")
    val Plus = stroke("Plus", 2.2f, "M12,5V19M5,12H19")
    val Star = stroke("Star", 1.8f, STAR)
    val StarFill = fill("StarFill", STAR)
    val Envelope = stroke("Envelope", 1.8f, "M4.5,6.5H19.5A1.5,1.5 0 0 1 21,8V17A1.5,1.5 0 0 1 19.5,18.5H4.5A1.5,1.5 0 0 1 3,17V8A1.5,1.5 0 0 1 4.5,6.5Z", "M3.5,7.5L12,13.5L20.5,7.5")
    val EnvelopeBadge = stroke("EnvelopeBadge", 1.8f, "M14,6.5H4.5A1.5,1.5 0 0 0 3,8V17A1.5,1.5 0 0 0 4.5,18.5H19.5A1.5,1.5 0 0 0 21,17V11.5", "M3.5,7.5L12,13.5L15,11.4", "M19,4.5A2.5,2.5 0 1 1 18.99,4.5Z")
    val Share = stroke("Share", 1.8f, "M12,3.5V14.5", "M8,7.5L12,3.5L16,7.5", "M8.5,10.5H6.5A1.5,1.5 0 0 0 5,12V19.5A1.5,1.5 0 0 0 6.5,21H17.5A1.5,1.5 0 0 0 19,19.5V12A1.5,1.5 0 0 0 17.5,10.5H15.5")
    val Compass = stroke("Compass", 1.8f, "M12,3A9,9 0 1 1 11.99,3Z", "M15.5,8.5L13.5,13.5L8.5,15.5L10.5,10.5Z")
    val Safari = Compass
    val Folder = stroke("Folder", 1.8f, "M3.5,7.5A1.5,1.5 0 0 1 5,6H9.5L11.5,8H19A1.5,1.5 0 0 1 20.5,9.5V17.5A1.5,1.5 0 0 1 19,19H5A1.5,1.5 0 0 1 3.5,17.5Z")
    val Tray = stroke("Tray", 1.8f, "M4,13.5L6.5,5.5H17.5L20,13.5V18A1.5,1.5 0 0 1 18.5,19.5H5.5A1.5,1.5 0 0 1 4,18Z", "M4,13.5H8.5L9.5,15.5H14.5L15.5,13.5H20")
    val Tag = stroke("Tag", 1.8f, "M3.5,5V11.2L12.8,20.5L20.5,12.8L11.2,3.5H5A1.5,1.5 0 0 0 3.5,5Z", "M8,7.5A0.5,0.5 0 1 1 7.99,7.5Z")
    val Filter = stroke("Filter", 2f, "M4,7H20", "M7,12H17", "M10,17H14")
    val Trash = stroke("Trash", 1.8f, "M4.5,6.5H19.5", "M9.5,6.5V4.5H14.5V6.5", "M6.5,6.5L7.3,19.6A1.5,1.5 0 0 0 8.8,21H15.2A1.5,1.5 0 0 0 16.7,19.6L17.5,6.5", "M10,10.5V17", "M14,10.5V17")
    val Pencil = stroke("Pencil", 1.8f, "M16.5,4.5L19.5,7.5L8.5,18.5L4.5,19.5L5.5,15.5Z", "M14.5,6.5L17.5,9.5")
    val Checkmark = stroke("Checkmark", 2.2f, "M5,12.5L10,17.5L19,7")
    val CheckCircle = stroke("CheckCircle", 1.8f, "M12,3A9,9 0 1 1 11.99,3Z", "M8,12.5L11,15.5L16.5,9.5")
    val Magnifier = stroke("Magnifier", 2f, "M10.5,4A6.5,6.5 0 1 1 10.49,4Z", "M15.5,15.5L20,20")
    val Ellipsis = stroke("Ellipsis", 1.8f, "M12,3A9,9 0 1 1 11.99,3Z", "M8,12h0.01M12,12h0.01M16,12h0.01")
    val Refresh = stroke("Refresh", 2f, "M19.5,12A7.5,7.5 0 1 1 17.3,6.7", "M18,3V7H14")
    val ImportDoc = stroke("Import", 1.8f, "M12,4V14.5", "M8,10.5L12,14.5L16,10.5", "M5,16V18.5A1.5,1.5 0 0 0 6.5,20H17.5A1.5,1.5 0 0 0 19,18.5V16")
    val ExportDoc = stroke("Export", 1.8f, "M12,14.5V4", "M8,8L12,4L16,8", "M5,16V18.5A1.5,1.5 0 0 0 6.5,20H17.5A1.5,1.5 0 0 0 19,18.5V16")
    val MinusCircle = fill("MinusCircle", "M12,2.5A9.5,9.5 0 1 1 11.99,2.5ZM7.5,11V13H16.5V11Z")
    val Close = stroke("Close", 2.2f, "M6.5,6.5L17.5,17.5M17.5,6.5L6.5,17.5")
    val Muted = stroke("Muted", 1.8f, "M4,9.5H7.5L12,5.5V18.5L7.5,14.5H4Z", "M16,9.5L20,13.5M20,9.5L16,13.5")

    private const val STAR = "M12,3.6L14.5,8.9L20.3,9.6L16,13.6L17.1,19.4L12,16.6L6.9,19.4L8,13.6L3.7,9.6L9.5,8.9Z"

    private fun builder(name: String) =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)

    private fun stroke(name: String, width: Float, vararg paths: String): ImageVector {
        val b = builder(name)
        paths.forEach { d ->
            b.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private fun fill(name: String, d: String): ImageVector =
        // Even-odd so inner shapes (e.g. the bar in MinusCircle) are cut out.
        builder(name).addPath(pathData = addPathNodes(d), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black)).build()
}
