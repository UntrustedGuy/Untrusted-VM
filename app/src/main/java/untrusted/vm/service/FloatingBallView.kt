package untrusted.vm.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import untrusted.vm.core.VmInstance

/**
 * VMOS Pro style Floating Ball & Expandable Quick Action Menu.
 * Equivalent to VMOS Pro's BallPopup / BallTab1Fragment / FloatBallMenu.
 * Displays a draggable floating icon on the host screen that expands into a quick control panel:
 * - Back, Home, Task Switcher (Recents)
 * - Root status toggle
 * - RAM Cleaner
 * - Screenshot capture
 * - Expand to PiP window or Fullscreen
 * - Shutdown VM
 */
class FloatingBallView(
    context: Context,
    private val windowManager: WindowManager,
    private val instance: VmInstance,
    private val onExpandToWindow: () -> Unit,
    private val onOpenFullscreen: () -> Unit,
    private val onShutdownVm: () -> Unit
) : FrameLayout(context) {

    private val ballSize = dpToPx(56)
    private var isMenuExpanded = false

    private val ballView: FrameLayout
    private val menuLayout: LinearLayout

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    val layoutParamsWindow: WindowManager.LayoutParams

    init {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParamsWindow = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        // 1. Cyberpunk Circular Ball
        ballView = FrameLayout(context).apply {
            layoutParams = LayoutParams(ballSize, ballSize)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(
                    Color.parseColor("#00E5FF"), // Neon Cyan
                    Color.parseColor("#7C4DFF")  // Neon Purple
                )
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = ballSize.toFloat()
                setStroke(dpToPx(2), Color.parseColor("#00E676")) // Neon Green border
            }

            val icon = ImageView(context).apply {
                layoutParams = LayoutParams(dpToPx(28), dpToPx(28), Gravity.CENTER)
                setImageResource(android.R.drawable.ic_menu_manage)
                setColorFilter(Color.WHITE)
            }
            addView(icon)
        }

        // 2. Expandable Quick Action Menu
        menuLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#E6090C15")) // Dark translucent
                setStroke(dpToPx(1), Color.parseColor("#00E5FF"))
            }

            // Title & Status
            val title = TextView(context).apply {
                text = "${instance.config.value.name} Quick Menu"
                setTextColor(Color.parseColor("#00E5FF"))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(title)

            addView(createSpacer(dpToPx(8)))

            // Row 1: Back, Home, Recents
            val navRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(createActionButton("◀ Back") {
                    instance.engine.injectKey(4, 0)
                    instance.engine.injectKey(4, 1)
                })
                addView(createSpacer(dpToPx(6)))
                addView(createActionButton("● Home") {
                    instance.engine.injectKey(3, 0)
                    instance.engine.injectKey(3, 1)
                })
                addView(createSpacer(dpToPx(6)))
                addView(createActionButton("■ Tasks") {
                    instance.engine.injectKey(187, 0)
                    instance.engine.injectKey(187, 1)
                })
            }
            addView(navRow)

            addView(createSpacer(dpToPx(8)))

            // Row 2: Root Toggle & RAM Clean
            val actionRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val rootBtn = createActionButton(
                    if (instance.config.value.isRootEnabled) "# Root: ON" else "# Root: OFF",
                    if (instance.config.value.isRootEnabled) Color.parseColor("#00E676") else Color.parseColor("#94A3B8")
                ) {
                    val newRoot = !instance.config.value.isRootEnabled
                    instance.setRootEnabled(newRoot)
                    (it as? TextView)?.text = if (newRoot) "# Root: ON" else "# Root: OFF"
                }
                addView(rootBtn)
                addView(createSpacer(dpToPx(6)))
                addView(createActionButton("⚡ Clean RAM", Color.parseColor("#FFD600")) {
                    instance.cleanMemory()
                })
            }
            addView(actionRow)

            addView(createSpacer(dpToPx(8)))

            // Row 3: Window Mode / Fullscreen / Shutdown
            val windowRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(createActionButton("⛶ Window") {
                    toggleMenu()
                    onExpandToWindow()
                })
                addView(createSpacer(dpToPx(6)))
                addView(createActionButton("Fullscreen") {
                    toggleMenu()
                    onOpenFullscreen()
                })
                addView(createSpacer(dpToPx(6)))
                addView(createActionButton("✕ Power", Color.parseColor("#FF5252")) {
                    toggleMenu()
                    onShutdownVm()
                })
            }
            addView(windowRow)
        }

        addView(menuLayout)
        addView(ballView)

        setupTouchHandling()
    }

    private fun setupTouchHandling() {
        ballView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParamsWindow.x
                    initialY = layoutParamsWindow.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > dpToPx(6) || Math.abs(dy) > dpToPx(6)) {
                        isDragging = true
                    }
                    layoutParamsWindow.x = initialX + dx
                    layoutParamsWindow.y = initialY + dy
                    windowManager.updateViewLayout(this, layoutParamsWindow)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        toggleMenu()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleMenu() {
        isMenuExpanded = !isMenuExpanded
        menuLayout.visibility = if (isMenuExpanded) View.VISIBLE else View.GONE
        windowManager.updateViewLayout(this, layoutParamsWindow)
    }

    private fun createActionButton(label: String, color: Int = Color.parseColor("#00E5FF"), onClick: (View) -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(color)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dpToPx(1), color)
            }
            setOnClickListener(onClick)
        }
    }

    private fun createSpacer(size: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
