package io.github.warnizz.sniffer.ar

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlin.math.atan
import kotlin.math.tan

/**
 * Horizontal field of view of the back camera as seen in a portrait PreviewView that fills
 * the view height (FILL_CENTER crops the sides). Falls back to 60°.
 */
fun portraitHorizontalFovDeg(context: Context, viewW: Int, viewH: Int): Double = runCatching {
    val cm = context.getSystemService(CameraManager::class.java)
    val id = cm.cameraIdList.first {
        cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }
    val ch = cm.getCameraCharacteristics(id)
    val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!!.first()
    val size = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)!!
    val vFov = 2 * atan(size.width / (2.0 * focal)) // sensor long side runs along the portrait view's height
    Math.toDegrees(2 * atan(tan(vFov / 2) * viewW / viewH))
}.getOrDefault(60.0).takeIf { it in 20.0..120.0 } ?: 60.0
