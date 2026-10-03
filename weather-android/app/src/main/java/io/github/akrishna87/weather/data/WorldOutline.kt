package io.github.akrishna87.weather.data

import android.content.Context
import android.graphics.Path
import java.io.DataInputStream
import java.io.EOFException

/**
 * Coastlines and country borders for the wind map, from Natural Earth (public domain), bundled
 * in the app so the map needs no tile server. `tools/pack_map.py` makes the files: each line is a
 * big-endian u16 point count, then that many (i16 lon, i16 lat) pairs in hundredths of a degree.
 *
 * The paths are in degrees with y = -latitude, so the map draws them with a single scale + translate.
 */
class WorldOutline(val land: Path, val borders: Path) {
    companion object {
        @Volatile private var loaded: WorldOutline? = null

        fun load(context: Context): WorldOutline = loaded ?: synchronized(this) {
            loaded ?: WorldOutline(
                read(context, "land.bin", close = true),
                read(context, "borders.bin", close = false),
            ).also { loaded = it }
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
