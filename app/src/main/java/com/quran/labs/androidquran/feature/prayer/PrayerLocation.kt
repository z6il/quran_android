package com.quran.labs.androidquran.feature.prayer

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Finding where the reader is, by the phone's location or a city name. */
object PrayerLocation {

  /** the phone's position now, or the last one known; null without permission or a fix */
  @SuppressLint("MissingPermission")
  suspend fun current(context: Context): Location? {
    val manager = context.getSystemService<LocationManager>() ?: return null
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
      .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    try {
      for (provider in providers) {
        val fresh = withTimeoutOrNull(15_000) {
          suspendCancellableCoroutine<Location?> { cont ->
            val signal = androidx.core.os.CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) {
              if (cont.isActive) cont.resume(it)
            }
          }
        }
        if (fresh != null) return fresh
      }
      return (providers + LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    } catch (_: SecurityException) {
      return null
    }
  }

  /** a place for these coordinates, named by the geocoder where it can */
  suspend fun describe(context: Context, latitude: Double, longitude: Double): Place = withContext(Dispatchers.IO) {
    val address = if (Geocoder.isPresent()) {
      @Suppress("DEPRECATION")
      runCatching { Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
    } else {
      null
    }
    Place(latitude, longitude, address?.let(::nameOf) ?: String.format(Locale.US, "%.3f, %.3f", latitude, longitude), address?.countryCode)
  }

  /** places matching [query]; empty when nothing matches or the phone has no geocoder */
  suspend fun search(context: Context, query: String): List<Place> = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent() || query.isBlank()) return@withContext emptyList()
    @Suppress("DEPRECATION")
    runCatching { Geocoder(context, Locale.getDefault()).getFromLocationName(query, 6) }.getOrNull().orEmpty()
      .filter { it.hasLatitude() && it.hasLongitude() }
      .map { Place(it.latitude, it.longitude, nameOf(it), it.countryCode) }
      .distinctBy { it.name }
  }

  private fun nameOf(address: Address): String =
    listOfNotNull(address.locality ?: address.subAdminArea ?: address.adminArea, address.countryName)
      .distinct().joinToString(", ").ifBlank { address.featureName.orEmpty() }
}
