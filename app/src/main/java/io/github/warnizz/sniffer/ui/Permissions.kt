package io.github.warnizz.sniffer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

fun Context.has(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** Fine and coarse together: on Android 12+ a fine-only request is ignored. */
val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** Approximate location is enough for GPS position and tower lookup; live cell IDs need precise. */
fun Context.hasLocation() = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)

fun Context.hasPreciseLocation() = has(Manifest.permission.ACCESS_FINE_LOCATION)

/** Notifications need a runtime permission from Android 13; before that they are always allowed. */
fun Context.hasNotifications() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || has(Manifest.permission.POST_NOTIFICATIONS)
