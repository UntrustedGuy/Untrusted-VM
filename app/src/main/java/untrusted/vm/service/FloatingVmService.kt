package untrusted.vm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import untrusted.vm.core.VmManager
import untrusted.vm.ui.components.VmSurfaceView

/**
 * Foreground floating window service (Picture-in-Picture / Overlay mode).
 * Equivalent to VMOS Pro's WindowService: overlays a miniature interactive VM
 * window on top of other Android apps with drag and resize support.
 */
class FloatingVmService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var currentVmId: Int = 1

    companion object {
        const val ACTION_START_FLOATING = "untrusted.vm.action.START_FLOATING"
        const val ACTION_STOP_FLOATING = "untrusted.vm.action.STOP_FLOATING"
        const val EXTRA_VM_ID = "extra_vm_id"
        private const val CHANNEL_ID = "untrusted_vm_floating_channel"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_START_FLOATING -> {
                currentVmId = intent.getIntExtra(EXTRA_VM_ID, 1)
                startForeground(NOTIFICATION_ID, buildNotification())
                showFloatingWindow(currentVmId)
            }
            ACTION_STOP_FLOATING -> {
                removeFloatingWindow()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun showFloatingWindow(vmId: Int) {
        removeFloatingWindow()

        val instance = VmManager.getInstance(applicationContext).getVmInstance(vmId) ?: return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            600, // Width (px)
            1000, // Height (px)
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        val container = FrameLayout(this)
        val surfaceView = VmSurfaceView(this, instance)
        container.addView(surfaceView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // Dragging & Moving handler
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(container, params)
                    true
                }
                else -> false
            }
        }

        floatingView = container
        windowManager?.addView(container, params)
    }

    private fun removeFloatingWindow() {
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
            floatingView = null
        }
    }

    override fun onDestroy() {
        removeFloatingWindow()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Untrusted VM Floating Window",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Untrusted VM Active")
            .setContentText("Running VM in Floating Window mode")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
}
