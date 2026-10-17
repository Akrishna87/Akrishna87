package io.github.akrishna87.weather.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/** The phone's rough position, without Google Play services. Needs ACCESS_COARSE_LOCATION. */
object DeviceLocation {
    /** Where the phone is, named with the phone's geocoder: for choosing the forecast place. */
    suspend fun find(context: Context): Place {
        val at = position(context)
        return describe(context, at.lat, at.lon)
    }

    /** Where the phone is (a fix up to 30 minutes old will do), without naming it. */
    @SuppressLint("MissingPermission")
    suspend fun position(context: Context): LatLon {
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
        val fix = recent ?: withTimeoutOrNull(20_000) { firstFix(lm, providers - LocationManager.PASSIVE_PROVIDER) }
            ?: providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
            ?: throw IOException("Couldn't get a location fix right now; check that location is on and try again")
        return LatLon(fix.latitude, fix.longitude)
    }

    /** Asks every provider at once and takes the first answer, so one slow provider can't hold things up. */
    private suspend fun firstFix(lm: LocationManager, providers: List<String>): Location? = coroutineScope {
        val pending = providers.map { p -> async { runCatching { current(lm, p) }.getOrNull() } }.toMutableList()
        var found: Location? = null
        while (found == null && pending.isNotEmpty()) {
            val (done, loc) = select<Pair<Deferred<Location?>, Location?>> {
                pending.forEach { d -> d.onAwait { d to it } }
            }
            pending.remove(done)
            found = loc
        }
        pending.forEach { it.cancel() }
        found
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
