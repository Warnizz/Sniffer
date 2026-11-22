package io.github.snifferapp.sniffer.ui

import android.Manifest
import android.app.Application
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import io.github.snifferapp.sniffer.engine.foregroundServiceTypes
import io.github.snifferapp.sniffer.engine.locationListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun hasLocation_coarseOnly_isTrue() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertTrue(app.hasLocation())
    }

    @Test fun hasLocation_none_isFalse() = assertFalse(app.hasLocation())

    @Test fun locationPermissions_requestsFineAndCoarse() =
        assertEquals(setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSIONS.toSet())

    @Test fun fgsTypes_withoutLocation_mediaOnly() =
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, foregroundServiceTypes(hasLocation = false))

    @Test fun fgsTypes_withLocation_both() = assertEquals(
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        foregroundServiceTypes(hasLocation = true),
    )
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LocationListenerApi29Test {
    @Test fun listener_providerToggle_noAbstractMethodError() {
        val l = locationListener { }
        l.onProviderEnabled("gps")
        l.onProviderDisabled("gps")
    }
}
