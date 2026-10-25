package untrusted.vm.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager

/**
 * HAL Box Hardware Bridge.
 * Inspired by VMOS Pro's libhalbox.so: forwards host sensor events
 * (accelerometer, gyroscope, light) and battery/GPS data to the VM engine.
 */
class HalBoxManager(
    private val context: Context,
    val vmId: Int,
    private val engine: VmEngine
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var batteryReceiver: BroadcastReceiver? = null

    private var accelX = 0f
    private var accelY = 9.8f
    private var accelZ = 0f
    private var gyroX = 0f
    private var gyroY = 0f
    private var gyroZ = 0f

    fun start() {
        startSensorSync()
        startBatteryMonitoring()
    }

    fun stop() {
        stopSensorSync()
        stopBatteryMonitoring()
    }

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

    private fun startBatteryMonitoring() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                status == BatteryManager.BATTERY_STATUS_FULL
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300)
                updateVirtualBattery(level, isCharging, temp / 10)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        try {
            context.registerReceiver(batteryReceiver, filter)
        } catch (_: Exception) {}
    }

    private fun stopBatteryMonitoring() {
        batteryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        batteryReceiver = null
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

    fun setGpsLocation(latitude: Double, longitude: Double, altitude: Double = 10.0) {
        updateVirtualLocation(latitude, longitude, altitude)
    }

    fun updateVirtualLocation(latitude: Double, longitude: Double, altitude: Double = 10.0) {
        engine.setVirtualGps(latitude, longitude, altitude)
    }

    fun updateVirtualBattery(level: Int, isCharging: Boolean, tempC: Int = 30) {
        engine.setVirtualBattery(level, isCharging, tempC * 10)
    }
}
