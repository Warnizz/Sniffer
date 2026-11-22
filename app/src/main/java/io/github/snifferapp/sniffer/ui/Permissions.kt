package io.github.snifferapp.sniffer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

fun Context.has(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

fun Context.hasLocation() = has(Manifest.permission.ACCESS_FINE_LOCATION)

/** Notifications need a runtime permission from Android 13; before that they are always allowed. */
fun Context.hasNotifications() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || has(Manifest.permission.POST_NOTIFICATIONS)
