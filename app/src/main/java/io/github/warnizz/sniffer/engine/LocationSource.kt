package io.github.warnizz.sniffer.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import io.github.warnizz.sniffer.geo.LatLon
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface LocationSource {
    /** Emits null until the first fix (or forever, without permission). */
    fun locations(intervalMs: Long): Flow<LatLon?>
}

/** Plain LocationManager (no Google services). Uses GPS, network if present, and passive providers. */
class AndroidLocationSource(private val context: Context) : LocationSource {
    @SuppressLint("MissingPermission")
    override fun locations(intervalMs: Long): Flow<LatLon?> = callbackFlow {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) { trySend(null); awaitClose { }; return@callbackFlow }
        val lm = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.allProviders.contains(it) }.getOrDefault(false) }
        var best: Location? = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        trySend(best?.let { LatLon(it.latitude, it.longitude) })
        val listener = locationListener { loc ->
            val b = best
            // Prefer fresh fixes; ignore a much less accurate fix arriving shortly after a good one.
            if (b == null || loc.time - b.time > 10_000 || loc.accuracy <= b.accuracy * 1.5f) {
                best = loc
                trySend(LatLon(loc.latitude, loc.longitude))
            }
        }
        providers.forEach { p ->
            runCatching { lm.requestLocationUpdates(p, intervalMs, 5f, listener, Looper.getMainLooper()) }
        }
        awaitClose { lm.removeUpdates(listener) }
    }
}

/** An object, not a SAM lambda: on API 29 the provider callbacks are abstract and a lambda crashes. */
fun locationListener(onLocation: (Location) -> Unit): LocationListener = object : LocationListener {
    override fun onLocationChanged(location: Location) = onLocation(location)
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
}
