package io.github.akrishna87.lumen.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons in the spirit of Apple's SF Symbols: rounded strokes on a 24dp grid, with filled variants
 * for the tab bar and toggled states. Tinted like any other icon via Icon(tint = …).
 */
object AppIcons {
    val ChevronLeft = icon("ChevronLeft") { stroke(2.4f, "M15,5L8,12L15,19") }
    val ChevronRight = icon("ChevronRight") { stroke(2.2f, "M9.5,6L15.5,12L9.5,18") }
    val Close = icon("Close") { stroke(2.2f, "M6.5,6.5L17.5,17.5M17.5,6.5L6.5,17.5") }
    val Checkmark = icon("Checkmark") { stroke(2.6f, "M5.5,12.5L10,17L18.5,7.5") }
    val Ellipsis = icon("Ellipsis") { stroke(2.6f, "M6,12h0.01M12,12h0.01M18,12h0.01") }

    /** Tab bar: photo.fill.on.rectangle.fill */
    val Library = icon("Library") {
        stroke(1.8f, "M6.5,4.5H19.5A2,2 0 0 1 21.5,6.5V15.5")
        fill("M4.5,7.5H16.5A2,2 0 0 1 18.5,9.5V18.5A2,2 0 0 1 16.5,20.5H4.5A2,2 0 0 1 2.5,18.5V9.5A2,2 0 0 1 4.5,7.5Z" +
            "M4.8,18.2L8.6,13.8Q9.2,13.1 9.8,13.8L12.3,16.6L13.6,15.2Q14.2,14.6 14.8,15.2L16.2,16.8V18.2Z" +
            "M13.6,9.6A1.4,1.4 0 1 0 13.59,9.6Z")
    }

    /** Tab bar: rectangle.stack.fill */
    val Albums = icon("Albums") {
        stroke(1.8f, "M7.5,3.5H16.5", "M5,6.5H19")
        fill("M5,9.5H19A2,2 0 0 1 21,11.5V18.5A2,2 0 0 1 19,20.5H5A2,2 0 0 1 3,18.5V11.5A2,2 0 0 1 5,9.5Z")
    }

    val Search = icon("Search") { stroke(2.2f, "M10.5,4A6.5,6.5 0 1 1 10.49,4Z", "M15.5,15.5L20,20") }
    val SearchSmall = icon("SearchSmall") { stroke(2f, "M10.5,5A5.5,5.5 0 1 1 10.49,5Z", "M14.8,14.8L18.5,18.5") }

    val Heart = icon("Heart") { stroke(1.8f, HEART) }
    val HeartFill = icon("HeartFill") { fill(HEART) }
    val Info = icon("Info") { stroke(1.8f, "M12,3A9,9 0 1 1 11.99,3Z"); stroke(2f, "M12,11V16.5"); stroke(2.6f, "M12,7.8h0.01") }
    val Share = icon("Share") {
        stroke(1.8f, "M12,3.5V14.5", "M8,7.5L12,3.5L16,7.5", "M8.5,10.5H6.5A1.5,1.5 0 0 0 5,12V19.5A1.5,1.5 0 0 0 6.5,21H17.5A1.5,1.5 0 0 0 19,19.5V12A1.5,1.5 0 0 0 17.5,10.5H15.5")
    }
    val Trash = icon("Trash") {
        stroke(1.8f, "M4.5,6.5H19.5", "M9.5,6.5V4.5H14.5V6.5", "M6.5,6.5L7.3,19.6A1.5,1.5 0 0 0 8.8,21H15.2A1.5,1.5 0 0 0 16.7,19.6L17.5,6.5", "M10,10.5V17", "M14,10.5V17")
    }
    val Recover = icon("Recover") { stroke(1.9f, "M9,14.5L4.5,10L9,5.5", "M4.5,10H14.5A5,5 0 0 1 14.5,20H11") }

    val Play = icon("Play") { fill("M8.6,5.1Q7.5,4.5 7.5,5.8V18.2Q7.5,19.5 8.6,18.9L18.6,12.7Q19.7,12 18.6,11.3Z") }
    val Pause = icon("Pause") {
        fill("M7.5,5H9.5A1,1 0 0 1 10.5,6V18A1,1 0 0 1 9.5,19H7.5A1,1 0 0 1 6.5,18V6A1,1 0 0 1 7.5,5Z" +
            "M14.5,5H16.5A1,1 0 0 1 17.5,6V18A1,1 0 0 1 16.5,19H14.5A1,1 0 0 1 13.5,18V6A1,1 0 0 1 14.5,5Z")
    }
    val Speaker = icon("Speaker") { stroke(1.8f, "M4,9.5H7.5L12,5.5V18.5L7.5,14.5H4Z", "M15.5,9A4,4 0 0 1 15.5,15", "M18,6.5A7.5,7.5 0 0 1 18,17.5") }
    val Muted = icon("Muted") { stroke(1.8f, "M4,9.5H7.5L12,5.5V18.5L7.5,14.5H4Z", "M16,9.5L20,13.5M20,9.5L16,13.5") }

    // Media types, as listed under Albums.
    val Photo = icon("Photo") {
        stroke(1.8f, "M5,5H19A1.5,1.5 0 0 1 20.5,6.5V17.5A1.5,1.5 0 0 1 19,19H5A1.5,1.5 0 0 1 3.5,17.5V6.5A1.5,1.5 0 0 1 5,5Z",
            "M4,17L9,11.5L13,15.5L15.5,13L20,17.5", "M15.5,8.2A1.3,1.3 0 1 1 15.49,8.2Z")
    }
    val Video = icon("Video") {
        stroke(1.8f, "M5,6.5H13.5A1.5,1.5 0 0 1 15,8V16A1.5,1.5 0 0 1 13.5,17.5H5A1.5,1.5 0 0 1 3.5,16V8A1.5,1.5 0 0 1 5,6.5Z", "M15,10.5L20.5,7.5V16.5L15,13.5")
    }
    val VideoFill = icon("VideoFill") {
        fill("M4.5,6.5H13A1.5,1.5 0 0 1 14.5,8V16A1.5,1.5 0 0 1 13,17.5H4.5A1.5,1.5 0 0 1 3,16V8A1.5,1.5 0 0 1 4.5,6.5Z" +
            "M15.5,10.2L20,7.6Q21,7.1 21,8.2V15.8Q21,16.9 20,16.4L15.5,13.8Z")
    }
    val Screenshot = icon("Screenshot") {
        stroke(1.8f, "M4,8.5V6A2,2 0 0 1 6,4H8.5", "M15.5,4H18A2,2 0 0 1 20,6V8.5", "M20,15.5V18A2,2 0 0 1 18,20H15.5", "M8.5,20H6A2,2 0 0 1 4,18V15.5")
        stroke(1.6f, "M10,8H14A1,1 0 0 1 15,9V15A1,1 0 0 1 14,16H10A1,1 0 0 1 9,15V9A1,1 0 0 1 10,8Z")
    }
    val Panorama = icon("Panorama") { stroke(1.8f, "M3.5,7Q12,4.8 20.5,7V17Q12,14.8 3.5,17Z") }
    val Animated = icon("Animated") {
        stroke(1.8f, "M9.5,8.5H18.5A1.5,1.5 0 0 1 20,10V18A1.5,1.5 0 0 1 18.5,19.5H9.5A1.5,1.5 0 0 1 8,18V10A1.5,1.5 0 0 1 9.5,8.5Z")
        stroke(1.8f, "M5,15.5V6.5A1.5,1.5 0 0 1 6.5,5H15.5")
    }

    private const val HEART =
        "M12,20C12,20 3.5,14.8 3.5,8.9C3.5,6.2 5.6,4.3 8,4.3C9.8,4.3 11.2,5.3 12,6.6C12.8,5.3 14.2,4.3 16,4.3C18.4,4.3 20.5,6.2 20.5,8.9C20.5,14.8 12,20 12,20Z"

    private class Builder(name: String) {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)

        fun stroke(width: Float, vararg paths: String) = paths.forEach { d ->
            b.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }

        // Even-odd so inner shapes (the hills in the Library icon) are cut out.
        fun fill(d: String) {
            b.addPath(pathData = addPathNodes(d), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
        }
    }

    private fun icon(name: String, block: Builder.() -> Unit): ImageVector = Builder(name).apply(block).b.build()
}
