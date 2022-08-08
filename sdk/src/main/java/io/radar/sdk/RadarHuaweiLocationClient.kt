package io.radar.sdk

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.location.Location
import android.os.Looper
import com.huawei.hms.location.*

@SuppressLint("MissingPermission")
internal class RadarHuaweiLocationClient(
    context: Context,
    private val logger: RadarLogger
): RadarAbstractLocationClient() {

    @SuppressLint("VisibleForTests")
    val locationClient = FusedLocationProviderClient(context)
    val geofenceService = GeofenceService(context)

    override fun getCurrentLocation(desiredAccuracy: RadarTrackingOptions.RadarTrackingOptionsDesiredAccuracy, block: (location: Location?) -> Unit) {
        val priority = when(desiredAccuracy) {
            RadarTrackingOptions.RadarTrackingOptionsDesiredAccuracy.HIGH -> LocationRequest.PRIORITY_HIGH_ACCURACY
            RadarTrackingOptions.RadarTrackingOptionsDesiredAccuracy.MEDIUM -> LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            RadarTrackingOptions.RadarTrackingOptionsDesiredAccuracy.LOW -> LocationRequest.PRIORITY_LOW_POWER
            RadarTrackingOptions.RadarTrackingOptionsDesiredAccuracy.NONE -> LocationRequest.PRIORITY_NO_POWER
        }

        logger.d("Requesting location")

        val locationRequest = LocationRequest().apply {
            this.priority = priority
            this.numUpdates = 1
        }

        locationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                block(locationResult.lastLocation)
            }
        }, Looper.getMainLooper())
    }

    override fun requestLocationUpdates(
        desiredAccuracy: RadarTrackingOptions.RadarTrackingOptionsDesiredAccuracy,
        interval: Int,
        fastestInterval: Int,
        pendingIntent: PendingIntent
    ) {
        val priority = when(desiredAccuracy) {
            RadarTrackingOptions.RadarTrackingOptionsDesiredAccuracy.HIGH -> LocationRequest.PRIORITY_HIGH_ACCURACY
            RadarTrackingOptions.RadarTrackingOptionsDesiredAccuracy.MEDIUM -> LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            RadarTrackingOptions.RadarTrackingOptionsDesiredAccuracy.LOW -> LocationRequest.PRIORITY_LOW_POWER
            RadarTrackingOptions.RadarTrackingOptionsDesiredAccuracy.NONE -> LocationRequest.PRIORITY_NO_POWER
        }

        val locationRequest = LocationRequest().apply {
            this.priority = priority
            this.interval = interval * 1000L
            this.fastestInterval = fastestInterval * 1000L
        }

        locationClient.requestLocationUpdates(locationRequest, pendingIntent)
    }

    override fun removeLocationUpdates(pendingIntent: PendingIntent) {
        locationClient.removeLocationUpdates(pendingIntent)
    }

    override fun getLastLocation(block: (location: Location?) -> Unit) {
        locationClient.lastLocation.addOnSuccessListener { location ->
            block(location)
        }.addOnFailureListener {
            block(null)
        }
    }

    override fun addGeofences(
        abstractGeofences: Array<RadarAbstractGeofence>,
        abstractGeofenceRequest: RadarAbstractGeofenceRequest,
        pendingIntent: PendingIntent,
        block: (success: Boolean) -> Unit
    ) {
        val geofences = mutableListOf<Geofence>()
        abstractGeofences.forEach { abstractGeofence ->
            var geofenceBuilder = Geofence.Builder()
                .setUniqueId(abstractGeofence.requestId)
                .setRoundArea(abstractGeofence.latitude, abstractGeofence.longitude, abstractGeofence.radius)
            var conversions = 0
            if (abstractGeofence.transitionEnter) {
                conversions = conversions or Geofence.ENTER_GEOFENCE_CONVERSION
            }
            if (abstractGeofence.transitionExit) {
                conversions = conversions or Geofence.EXIT_GEOFENCE_CONVERSION
            }
            if (abstractGeofence.transitionDwell) {
                conversions = conversions or Geofence.DWELL_GEOFENCE_CONVERSION
            }
            geofenceBuilder = geofenceBuilder.setConversions(conversions)

            val geofence = geofenceBuilder.build()
            geofences.add(geofence)
        }

        var requestBuilder = GeofenceRequest.Builder()
            .createGeofenceList(geofences)
        var initConversions = 0
        if (abstractGeofenceRequest.initialTriggerEnter) {
            initConversions = initConversions or Geofence.ENTER_GEOFENCE_CONVERSION
        }
        if (abstractGeofenceRequest.initialTriggerExit) {
            initConversions = initConversions or Geofence.EXIT_GEOFENCE_CONVERSION
        }
        if (abstractGeofenceRequest.initialTriggerDwell) {
            initConversions = initConversions or Geofence.DWELL_GEOFENCE_CONVERSION
        }
        requestBuilder = requestBuilder.setInitConversions(initConversions)

        val request = requestBuilder.build()

        geofenceService.createGeofenceList(request, pendingIntent).run {
            addOnSuccessListener {
                block(true)
            }
            addOnFailureListener {
                block(false)
            }
        }
    }

    override fun removeGeofences(
        pendingIntent: PendingIntent
    ) {
        geofenceService.deleteGeofenceList(pendingIntent)
    }

}