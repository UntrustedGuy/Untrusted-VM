package untrusted.vm.core

import android.util.Log
import java.io.File
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pedometer & Step Counter Simulator.
 * Equivalent to VMOS Pro's Step Scale / Step Counter spoofer.
 * Simulates real-time walking/running steps by periodically updating the guest
 * sensor sysfs (/sys/class/sensors/step_counter/step_count).
 * Supports multipliers: 1x (standard walking), 2x (jogging), 5x (running), 10x (rapid simulation).
 */
class StepCounterSimulator(
    private val vmId: Int,
    private val filesystem: VmFilesystem
) {
    companion object {
        private const val TAG = "StepCounterSimulator"
    }

    private val isRunning = AtomicBoolean(false)
    private var timer: Timer? = null
    var stepMultiplier: Int = 1
        private set
    var currentSteps: Long = 0
        private set

    val stepSysfsFile: File
        get() = filesystem.resolveGuestFile("/sys/class/sensors/step_counter/step_count")

    fun start(multiplier: Int = 1) {
        if (isRunning.get()) stop()

        this.stepMultiplier = multiplier.coerceIn(1, 20)
        isRunning.set(true)

        // Read initial step count if exists
        try {
            if (stepSysfsFile.exists()) {
                currentSteps = stepSysfsFile.readText().trim().toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {}

        timer = Timer("UntrustedVM-StepSim-$vmId", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (!isRunning.get()) return
                    // Add steps based on multiplier (approx 2 steps per second for 1x walking)
                    val stepIncrement = (2 * stepMultiplier) + (Math.random() * 2).toInt()
                    currentSteps += stepIncrement
                    writeStepCount(currentSteps)
                }
            }, 0L, 1000L)
        }
        Log.i(TAG, "Step simulator started for VM #$vmId at ${stepMultiplier}x speed")
    }

    fun stop() {
        isRunning.set(false)
        timer?.cancel()
        timer = null
    }

    fun setMultiplier(multiplier: Int) {
        this.stepMultiplier = multiplier.coerceIn(1, 20)
    }

    fun resetSteps() {
        currentSteps = 0L
        writeStepCount(0L)
    }

    private fun writeStepCount(steps: Long) {
        try {
            stepSysfsFile.parentFile?.mkdirs()
            stepSysfsFile.writeText("$steps\n")
        } catch (_: Exception) {}
    }
}
