package io.github.akrishna87.weather.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/** The phone's rough position, without Google Play services. Needs ACCESS_COARSE_LOCATION. */
object DeviceLocation {
    @SuppressLint("MissingPermission")
    suspend fun find(context: Context): Place {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(lm)) throw IOException("Location is turned off on this phone")
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 30 * 60_000 }
            .maxByOrNull { it.time }
        val fix = recent ?: withTimeoutOrNull(20_000) {
            providers.firstNotNullOfOrNull { p -> if (p == LocationManager.PASSIVE_PROVIDER) null else current(lm, p) }
        } ?: providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        ?: throw IOException("Couldn't find where you are; try searching for your town instead")
        return describe(context, fix.latitude, fix.longitude)
    }

    @SuppressLint("MissingPermission")
    private suspend fun current(lm: LocationManager, provider: String): Location? = suspendCancellableCoroutine { cont ->
        val cancel = CancellationSignal()
        cont.invokeOnCancellation { cancel.cancel() }
        LocationManagerCompat.getCurrentLocation(lm, provider, cancel, Executor { it.run() }) { loc ->
            if (cont.isActive) cont.resume(loc)
        }
    }

    /** Names the spot using the phone's own geocoder, when it has one. */
    @Suppress("DEPRECATION")
    private suspend fun describe(context: Context, lat: Double, lon: Double): Place = withContext(Dispatchers.IO) {
        val a = runCatching {
            if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull() else null
        }.getOrNull()
        Place(
            name = a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: "My location",
            region = a?.adminArea ?: "",
            country = a?.countryName ?: "",
            countryCode = a?.countryCode ?: "",
            lat = lat,
            lon = lon,
        )
    }
}
