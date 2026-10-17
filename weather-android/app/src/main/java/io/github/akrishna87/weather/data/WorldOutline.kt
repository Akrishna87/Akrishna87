package io.github.akrishna87.weather.data

import android.content.Context
import android.graphics.Path
import java.io.DataInputStream
import java.io.EOFException

/** A place name on the map. [minZoom] is the scale (dp per degree) from which it shows. */
class MapLabel(val kind: Kind, val minZoom: Float, val lat: Double, val lon: Double, val name: String) {
    enum class Kind { Country, State, Capital, City }

    /** Text width in px, measured once with the paint for its kind. */
    var width = -1f
}

/**
 * Coastlines, borders and place names for the wind map, from Natural Earth (public domain),
 * bundled in the app so the map needs no tile server. `tools/pack_map.py` makes the files: each
 * line is a big-endian u16 point count, then that many (i16 lon, i16 lat) pairs in hundredths of a
 * degree. Borders follow India's official view; [states] has India's states and union territories.
 *
 * The paths are in degrees with y = -latitude, so the map draws them with a single scale + translate.
 */
class WorldOutline(val land: Path, val borders: Path, val states: Path, val labels: List<MapLabel>) {
    companion object {
        @Volatile private var loaded: WorldOutline? = null

        fun load(context: Context): WorldOutline = loaded ?: synchronized(this) {
            loaded ?: WorldOutline(
                read(context, "land.bin", close = true),
                read(context, "borders.bin", close = false),
                read(context, "states.bin", close = false),
                readLabels(context),
            ).also { loaded = it }
        }

        /** labels.tsv: kind, min zoom, lat, lon, name; most important first. */
        private fun readLabels(context: Context): List<MapLabel> =
            context.assets.open("labels.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.mapNotNull { line ->
                    val f = line.split('\t')
                    if (f.size < 5) return@mapNotNull null
                    val kind = when (f[0]) {
                        "C" -> MapLabel.Kind.Country
                        "S" -> MapLabel.Kind.State
                        "K" -> MapLabel.Kind.Capital
                        else -> MapLabel.Kind.City
                    }
                    MapLabel(kind, f[1].toFloatOrNull() ?: return@mapNotNull null, f[2].toDouble(), f[3].toDouble(), f[4])
                }.toList()
            }

        private fun read(context: Context, asset: String, close: Boolean): Path {
            val path = Path()
            DataInputStream(context.assets.open(asset).buffered()).use { input ->
                while (true) {
                    val n = try { input.readUnsignedShort() } catch (e: EOFException) { break }
                    for (k in 0 until n) {
                        val x = input.readShort() / 100f
                        val y = -input.readShort() / 100f
                        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    if (close) path.close()
                }
            }
            path.fillType = Path.FillType.EVEN_ODD
            return path
        }
    }
}
