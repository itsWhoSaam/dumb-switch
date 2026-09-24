package com.houseofai.dumbswitch

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.time.Duration
import java.util.Locale

/**
 * Home-role activity. Renders dumb mode — full-bleed clock, date, exactly the five allowlisted
 * entries — or the timed smart-mode escape: all launchable apps plus a countdown banner.
 *
 * The mode is re-derived from [ModeRepository] on every onResume, which is where v0 decay
 * applies: the running smart list never closes itself mid-session — the next home press lands
 * back in dumb mode. There is no early cancel; the countdown is the friction (spec).
 */
class LauncherActivity : Activity() {

    private val modeRepository by lazy {
        ModeRepository(getSharedPreferences(PREFS_NAME, MODE_PRIVATE))
    }

    private val allowlistRepository by lazy {
        PrefsAllowlistRepository(getSharedPreferences(ALLOWLIST_PREFS_NAME, MODE_PRIVATE))
    }

    private val tickHandler = Handler(Looper.getMainLooper())
    private lateinit var smartBanner: TextView
    private var ticking = false

    private val dumbViews = listOf(R.id.clock, R.id.date, R.id.allowlist, R.id.smart_mode_action)
    private val smartViews = listOf(R.id.smart_banner, R.id.smart_list_scroll)

    /** One tick per second: repaint the banner from the repository, never from drifting local state. */
    private val bannerTick = object : Runnable {
        override fun run() {
            if (!ticking) return
            smartBanner.text = bannerText(modeRepository.remainingEscape())
            tickHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        smartBanner = findViewById(R.id.smart_banner)
        HomeRoleGate(this).maybeRequestHomeRole()
        findViewById<TextView>(R.id.smart_mode_action).apply {
            text = SMART_MODE_ACTION
            setOnLongClickListener {
                confirmSmartMode()
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        renderCurrentMode()
    }

    override fun onPause() {
        super.onPause()
        stopBannerTicks()
    }

    private fun renderCurrentMode() {
        stopBannerTicks()
        when (modeRepository.currentMode()) {
            Mode.Dumb -> renderDumb()
            is Mode.Smart -> renderSmart()
        }
    }

    private fun renderDumb() {
        setVisible(dumbViews)
        renderAllowlist()
    }

    private fun renderSmart() {
        setVisible(smartViews)
        renderSmartList()
        ticking = true
        bannerTick.run() // first paint immediately, then once per second
    }

    private fun setVisible(visibleIds: List<Int>) {
        (dumbViews + smartViews).forEach { id ->
            findViewById<View>(id).visibility = if (id in visibleIds) View.VISIBLE else View.GONE
        }
    }

    private fun renderAllowlist() {
        val container = findViewById<LinearLayout>(R.id.allowlist)
        container.removeAllViews()
        AllowlistResolver(packageManager).resolve(allowlistRepository.entries()).forEach { resolved ->
            container.addView(
                TextView(this).apply {
                    text = resolved.label
                    textSize = ROW_TEXT_SP
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(ROW_PADDING_DP), 0, dp(ROW_PADDING_DP))
                    setOnClickListener { resolved.launchIntent?.let(::startActivity) }
                },
            )
        }
    }

    private fun renderSmartList() {
        val container = findViewById<LinearLayout>(R.id.smart_list)
        container.removeAllViews()
        renderSettingsRow(container)
        val apps = SmartAppsResolver(packageManager, packageName).resolve()
        if (apps.isEmpty()) {
            container.addView(
                TextView(this).apply {
                    text = EMPTY_SMART_LIST
                    textSize = EMPTY_STATE_TEXT_SP
                    setTextColor(EMPTY_STATE_COLOR)
                    gravity = Gravity.CENTER
                },
            )
            return
        }
        apps.forEach { app ->
            container.addView(
                TextView(this).apply {
                    text = app.label
                    textSize = SMART_ROW_TEXT_SP
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(SMART_ROW_PADDING_DP), 0, dp(SMART_ROW_PADDING_DP))
                    setOnClickListener { startActivity(app.launchIntent) }
                },
            )
        }
    }

    /**
     * The only door to configuration: the Settings row lives atop the smart list, so it appears
     * exclusively while smart mode is active (spec — the dumb home never grows UI).
     */
    private fun renderSettingsRow(container: LinearLayout) {
        container.addView(
            TextView(this).apply {
                text = SETTINGS_ROW
                textSize = SMART_ROW_TEXT_SP
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(SMART_ROW_PADDING_DP), 0, dp(SMART_ROW_PADDING_DP))
                setOnClickListener {
                    startActivity(Intent(this@LauncherActivity, AllowlistEditorActivity::class.java))
                }
            },
        )
    }

    /** Long-press opens the confirm step; only confirming starts the escape (spec: no early cancel). */
    private fun confirmSmartMode() {
        AlertDialog.Builder(this)
            .setTitle(SMART_MODE_ACTION)
            .setMessage(
                "Unlock every app for ${ModeRepository.DEFAULT_ESCAPE.toMinutes()} minutes?\n\n" +
                    "The countdown cannot be stopped early. When it ends, the next home press " +
                    "returns to dumb mode.",
            )
            .setPositiveButton("Start timer") { _, _ ->
                modeRepository.startEscape()
                renderCurrentMode()
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun bannerText(remaining: Duration): String =
        if (remaining.isZero) {
            EXPIRED_BANNER
        } else {
            // Ceil the second so the banner reads 30:00 at the start and 00:00 only once the
            // window is genuinely spent.
            val totalSeconds = (remaining.toMillis() + 999) / 1000
            String.format(Locale.ROOT, SMART_BANNER_FORMAT, totalSeconds / 60, totalSeconds % 60)
        }

    private fun stopBannerTicks() {
        ticking = false
        tickHandler.removeCallbacks(bannerTick)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PREFS_NAME = "dumb_switch_mode"
        const val ALLOWLIST_PREFS_NAME = "dumb_switch_allowlist"
        const val SETTINGS_ROW = "Settings"
        const val TICK_INTERVAL_MS = 1000L
        const val SMART_BANNER_FORMAT = "Smart mode · %d:%02d"
        const val EXPIRED_BANNER = "Smart mode over — next home press returns to dumb mode"
        const val SMART_MODE_ACTION = "Smart mode"
        const val EMPTY_SMART_LIST = "No launchable apps found"
        const val ROW_TEXT_SP = 24f
        const val ROW_PADDING_DP = 18
        const val SMART_ROW_TEXT_SP = 20f
        const val SMART_ROW_PADDING_DP = 14
        const val EMPTY_STATE_TEXT_SP = 16f
        val EMPTY_STATE_COLOR = 0x66FFFFFF.toInt()
    }
}
