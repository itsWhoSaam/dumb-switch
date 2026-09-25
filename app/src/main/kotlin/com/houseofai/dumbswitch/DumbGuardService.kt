package com.houseofai.dumbswitch

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/**
 * The overlay guard (v0.2 spec, feature 2). Listens for window changes only — never window
 * content — and, while dumb mode is active, covers a non-allowlisted foreground app with a
 * TYPE_ACCESSIBILITY_OVERLAY black screen: clock, "Dumb mode", and one button home.
 *
 * All decisions come from [GuardPolicy]; this class only applies what it returns. Android keeps
 * the service disabled until the user enables it in Settings → Accessibility — the consent gate,
 * and the by-design bypass (findings log F2). The overlay's single button performs the same
 * global home action the launcher answers, so the dumb home stays one press away (spec).
 */
class DumbGuardService : AccessibilityService() {

    private val modeRepository by lazy {
        ModeRepository(getSharedPreferences(MODE_PREFS_NAME, MODE_PRIVATE))
    }

    private val allowlistRepository by lazy {
        PrefsAllowlistRepository(getSharedPreferences(ALLOWLIST_PREFS_NAME, MODE_PRIVATE))
    }

    private val windowManager by lazy { getSystemService(WindowManager::class.java) }

    /** Built once on connect, attached to the window manager only while covering. */
    private var overlay: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = buildOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        evaluate(event.packageName?.toString())
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        // A guard disabled mid-cover must not leave its window behind.
        hideOverlay()
        overlay = null
        super.onDestroy()
    }

    /**
     * Applies [GuardPolicy] to the window change just received. Mode and allowlist are
     * re-derived from prefs on every event — never cached state (the v0 decay model).
     */
    private fun evaluate(foreground: String?) {
        // A window change that reports no package (some system surfaces) is ambiguous, and the
        // service has no better source: with canRetrieveWindowContent=false the active window is
        // unreadable by design. Keeping the current state beats guessing either way.
        if (foreground == null) return

        val decision = GuardPolicy.decide(
            foregroundPackage = foreground,
            mode = modeRepository.currentMode(),
            allowlisted = allowlistRepository.entries().map { it.packageName }.toSet(),
            selfPackage = packageName,
        )
        when (decision) {
            GuardPolicy.Decision.Allow -> hideOverlay()
            GuardPolicy.Decision.Cover -> showOverlay()
        }
    }

    private fun showOverlay() {
        val view = overlay ?: return
        if (view.windowToken == null) {
            windowManager.addView(view, overlayParams())
        }
        view.visibility = View.VISIBLE
    }

    private fun hideOverlay() {
        val view = overlay ?: return
        if (view.windowToken != null) {
            windowManager.removeView(view)
        }
    }

    /**
     * TYPE_ACCESSIBILITY_OVERLAY is the sanctioned overlay channel for accessibility services —
     * no system alert permission, and it draws above app windows. Focusable and touchable on
     * purpose: the home button must receive taps, while everything behind stays covered.
     */
    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.OPAQUE,
    )

    /** Mirrors the dumb home's visual language: black, TextClock, one action. All UI in code. */
    private fun buildOverlay(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.BLACK)
        // Swallow touches so the covered app never receives them through the overlay.
        isClickable = true
        isFocusable = true

        addView(
            TextClock(this@DumbGuardService).apply {
                format12Hour = CLOCK_FORMAT_12
                format24Hour = CLOCK_FORMAT_24
                setTextColor(Color.WHITE)
                textSize = CLOCK_TEXT_SP
                gravity = Gravity.CENTER
            },
        )
        addView(
            TextView(this@DumbGuardService).apply {
                text = TITLE
                setTextColor(TITLE_COLOR)
                textSize = TITLE_TEXT_SP
                gravity = Gravity.CENTER
                setPadding(0, dp(TITLE_TOP_PADDING_DP), 0, 0)
            },
        )
        addView(
            Button(this@DumbGuardService).apply {
                text = HOME_ACTION
                textSize = HOME_ACTION_TEXT_SP
                setOnClickListener {
                    // Hide first so the home press never renders one covered frame.
                    hideOverlay()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            },
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MODE_PREFS_NAME = "dumb_switch_mode"
        const val ALLOWLIST_PREFS_NAME = "dumb_switch_allowlist"
        const val TITLE = "Dumb mode"
        const val HOME_ACTION = "Home"
        const val CLOCK_FORMAT_12 = "h:mm"
        const val CLOCK_FORMAT_24 = "HH:mm"
        const val CLOCK_TEXT_SP = 88f
        const val TITLE_TEXT_SP = 22f
        const val HOME_ACTION_TEXT_SP = 20f
        const val TITLE_TOP_PADDING_DP = 24
        val TITLE_COLOR = 0xB3FFFFFF.toInt()
    }
}
