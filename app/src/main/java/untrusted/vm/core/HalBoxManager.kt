package untrusted.vm.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * HAL Box Hardware Bridge.
 * Inspired by VMOS Pro's libhalbox.so: forwards host sensor events
 * (accelerometer, gyroscope, light) and virtual battery/GPS data to the VM engine.
 */
class HalBoxManager(
    private val context: Context,
    private val engine: VmEngine
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var accelX = 0f
    private var accelY = 9.8f
    private var accelZ = 0f
    private var gyroX = 0f
    private var gyroY = 0f
    private var gyroZ = 0f

    fun startSensorSync() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopSensorSync() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroX = event.values[0]
                gyroY = event.values[1]
                gyroZ = event.values[2]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun updateVirtualLocation(latitude: Double, longitude: Double, altitude: Double = 10.0) {
        engine.setVirtualGps(latitude, longitude, altitude)
    }

    fun updateVirtualBattery(level: Int, isCharging: Boolean, tempC: Int = 30) {
        engine.setVirtualBattery(level, isCharging, tempC * 10)
    }
}
