package untrusted.vm.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import untrusted.vm.core.VmInstance
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance virtual display SurfaceView.
 * Equivalent to VMOS Pro's VMOSSurfaceView: blits the native framebuffer
 * directly to the hardware display at 60 FPS and captures touch events.
 */
class VmSurfaceView(
    context: Context,
    private val instance: VmInstance
) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private val isRunning = AtomicBoolean(false)
    private var renderThread: Thread? = null
    private var virtualBitmap: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val dstRect = Rect()

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val config = instance.config.value
        val width = config.resolutionWidth
        val height = config.resolutionHeight

        virtualBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        srcRect.set(0, 0, width, height)

        isRunning.set(true)
        renderThread = Thread(this, "VmRenderThread-${instance.id}").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        dstRect.set(0, 0, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning.set(false)
        try {
            renderThread?.join(500)
        } catch (ignored: InterruptedException) {}
        renderThread = null
        virtualBitmap?.recycle()
        virtualBitmap = null
    }

    override fun run() {
        val engine = instance.engine
        val frameDurationMs = 16L // ~60 FPS

        while (isRunning.get()) {
            val startTime = System.currentTimeMillis()
            val bitmap = virtualBitmap ?: break

            // 1. Pull latest frame from native container
            engine.renderToBitmap(bitmap)

            // 2. Lock surface canvas and blit frame
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (ignored: Exception) {}
                }
            }

            // 3. Frame rate pacing
            val elapsed = System.currentTimeMillis() - startTime
            val sleepTime = frameDurationMs - elapsed
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime)
                } catch (ignored: InterruptedException) {
                    break
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val config = instance.config.value
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (viewWidth <= 0 || viewHeight <= 0) return super.onTouchEvent(event)

        // Map touch coordinates to guest virtual resolution
        val scaleX = config.resolutionWidth.toFloat() / viewWidth
        val scaleY = config.resolutionHeight.toFloat() / viewHeight

        val action = event.actionMasked
        val pointerCount = event.pointerCount

        for (i in 0 until pointerCount) {
            val pointerId = event.getPointerId(i)
            val guestX = event.getX(i) * scaleX
            val guestY = event.getY(i) * scaleY

            instance.engine.injectTouch(action, guestX, guestY, pointerId)
        }

        return true
    }
}
