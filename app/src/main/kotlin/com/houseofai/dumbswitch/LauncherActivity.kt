package com.houseofai.dumbswitch

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Home-role activity. v0 renders dumb mode only: a full-bleed clock and date plus exactly the
 * five allowlisted entries — no drawer, no search, no feed. The timed smart-mode escape is a
 * scaffold follow-up and does not exist yet.
 */
class LauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        HomeRoleGate(this).maybeRequestHomeRole()
        renderAllowlist()
    }

    private fun renderAllowlist() {
        val container = findViewById<LinearLayout>(R.id.allowlist)
        container.removeAllViews()
        AllowlistResolver(packageManager).resolve().forEach { resolved ->
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val ROW_TEXT_SP = 24f
        const val ROW_PADDING_DP = 18
    }
}
