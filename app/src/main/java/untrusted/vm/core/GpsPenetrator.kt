package untrusted.vm.core

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * GPS & Location Penetrator.
 * Equivalent to VMOS Pro's GpsPenetrator / LocationManagerUtil.
 * Provides two modes:
 * 1. REAL PASS-THROUGH: Streams host GPS location updates into guest HAL.
 * 2. MOCK / SPOOFED LOCATION: Injects custom coordinates (latitude, longitude, altitude)
 *    and generates valid NMEA sentences ($GPGGA, $GPRMC) into guest /dev/ttyS0 / GPS socket.
 */
class GpsPenetrator(
    private val context: Context,
    private val vmId: Int,
    private val filesystem: VmFilesystem,
    private val halBoxManager: HalBoxManager
) {
    companion object {
        private const val TAG = "GpsPenetrator"

        val PRESET_LOCATIONS = mapOf(
            "New York City" to Pair(40.7128, -74.0060),
            "Tokyo" to Pair(35.6762, 139.6503),
            "London" to Pair(51.5074, -0.1278),
            "Paris" to Pair(48.8566, 2.3522),
            "San Francisco" to Pair(37.7749, -122.4194),
            "Singapore" to Pair(1.3521, 103.8198),
            "Sydney" to Pair(-33.8688, 151.2093)
        )
    }

    var isPassThroughEnabled = false
        private set

    var isMockEnabled = false
        private set

    var mockLatitude = 37.7749
    var mockLongitude = -122.4194
    var mockAltitude = 15.0

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var mockLocationTimer: Timer? = null

    init {
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    @SuppressLint("MissingPermission")
    fun enablePassThrough() {
        isPassThroughEnabled = true
        isMockEnabled = false
        stopMockTimer()

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                injectLocation(location.latitude, location.longitude, location.altitude, location.speed, location.bearing)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            locationManager?.let { lm ->
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 1.0f, locationListener!!)
                } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 1.0f, locationListener!!)
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission not granted; falling back to mock location")
            enableMockLocation(mockLatitude, mockLongitude, mockAltitude)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting host location updates", e)
        }
    }

    fun disablePassThrough() {
        isPassThroughEnabled = false
        locationListener?.let {
            try {
                locationManager?.removeUpdates(it)
            } catch (_: Exception) {}
        }
        locationListener = null
    }

    fun enableMockLocation(lat: Double, lng: Double, alt: Double = 10.0) {
        disablePassThrough()
        isMockEnabled = true
        mockLatitude = lat
        mockLongitude = lng
        mockAltitude = alt

        stopMockTimer()
        mockLocationTimer = Timer("UntrustedVM-MockGps-$vmId", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    // Small micro-jitter (±0.00002 deg) to simulate real satellite noise
                    val jitterLat = mockLatitude + (Math.random() - 0.5) * 0.00004
                    val jitterLng = mockLongitude + (Math.random() - 0.5) * 0.00004
                    injectLocation(jitterLat, jitterLng, mockAltitude, 1.2f, 90f)
                }
            }, 0L, 1000L)
        }
    }

    fun disableMockLocation() {
        isMockEnabled = false
        stopMockTimer()
    }

    private fun stopMockTimer() {
        mockLocationTimer?.cancel()
        mockLocationTimer = null
    }

    private fun injectLocation(lat: Double, lng: Double, alt: Double, speed: Float, bearing: Float) {
        // 1. Update native HalBox
        halBoxManager.setGpsLocation(lat, lng, alt)

        // 2. Generate standard NMEA sentences and write to guest /dev/gps_pipe or /data/vendor/gps/nmea
        val nmeaGga = formatNmeaGga(lat, lng, alt)
        val nmeaRmc = formatNmeaRmc(lat, lng, speed, bearing)

        val gpsPipe = filesystem.resolveGuestFile("/dev/gps_pipe")
        try {
            gpsPipe.parentFile?.mkdirs()
            gpsPipe.appendText("$nmeaGga\r\n$nmeaRmc\r\n")
            // Prevent unbounded file growth
            if (gpsPipe.length() > 500 * 1024) {
                gpsPipe.writeText("$nmeaGga\r\n$nmeaRmc\r\n")
            }
        } catch (_: Exception) {}
    }

    private fun formatNmeaGga(lat: Double, lng: Double, alt: Double): String {
        val timeStr = SimpleDateFormat("HHmmss.SS", Locale.US).format(Date())
        val latDeg = lat.toInt()
        val latMin = (Math.abs(lat) - Math.abs(latDeg)) * 60.0
        val latDir = if (lat >= 0) "N" else "S"

        val lngDeg = lng.toInt()
        val lngMin = (Math.abs(lng) - Math.abs(lngDeg)) * 60.0
        val lngDir = if (lng >= 0) "E" else "W"

        val body = String.format(
            Locale.US,
            "GPGGA,%s,%02d%07.4f,%s,%03d%07.4f,%s,1,08,1.0,%.1f,M,0.0,M,,",
            timeStr, Math.abs(latDeg), latMin, latDir, Math.abs(lngDeg), lngMin, lngDir, alt
        )
        return "\$" + body + "*" + computeChecksum(body)
    }

    private fun formatNmeaRmc(lat: Double, lng: Double, speedKnots: Float, bearing: Float): String {
        val timeStr = SimpleDateFormat("HHmmss.SS", Locale.US).format(Date())
        val dateStr = SimpleDateFormat("ddMMyy", Locale.US).format(Date())
        val latDeg = lat.toInt()
        val latMin = (Math.abs(lat) - Math.abs(latDeg)) * 60.0
        val latDir = if (lat >= 0) "N" else "S"

        val lngDeg = lng.toInt()
        val lngMin = (Math.abs(lng) - Math.abs(lngDeg)) * 60.0
        val lngDir = if (lng >= 0) "E" else "W"

        val body = String.format(
            Locale.US,
            "GPRMC,%s,A,%02d%07.4f,%s,%03d%07.4f,%s,%.1f,%.1f,%s,,,A",
            timeStr, Math.abs(latDeg), latMin, latDir, Math.abs(lngDeg), lngMin, lngDir, speedKnots, bearing, dateStr
        )
        return "\$" + body + "*" + computeChecksum(body)
    }

    private fun computeChecksum(sentence: String): String {
        var checksum = 0
        for (c in sentence) {
            checksum = checksum xor c.code
        }
        return String.format("%02X", checksum)
    }
}
