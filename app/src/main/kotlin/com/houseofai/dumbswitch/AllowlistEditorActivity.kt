package com.houseofai.dumbswitch

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The smart-list settings surface: a checkbox list over every launchable app, the current
 * allowlist pre-checked. Saving persists the checked rows in listed order, and labels are
 * captured at save time — `getApplicationInfo` is not guaranteed for packages outside the
 * manifest's targeted <queries>, so the dumb home renders what the editor showed rather than
 * re-resolving.
 *
 * Reachable only from the smart list (spec: the dumb home never grows UI). An empty save is
 * rejected here before the repository's require can fire: the dumb home keeps at least one app.
 */
class AllowlistEditorActivity : Activity() {

    private val allowlistRepository by lazy {
        PrefsAllowlistRepository(getSharedPreferences(PREFS_NAME, MODE_PRIVATE))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rows = buildRows()
        setContentView(buildEditorView(rows))
    }

    /** One row per editable entry: what persists if checked, and the checkbox itself. */
    private data class Row(val entry: AllowlistEntry, val checkBox: CheckBox)

    /**
     * Launchable apps in resolver order (label-sorted), then persisted entries the resolver can
     * no longer see. The latter keeps an uninstalled or disabled allowlisted app visible and
     * removable deliberately — saving must never silently drop an entry the user did not touch.
     */
    private fun buildRows(): List<Row> {
        val current = allowlistRepository.entries()
        val currentPackages = current.map { it.packageName }.toSet()
        val launchable = SmartAppsResolver(packageManager, packageName).resolve()
            .map { AllowlistEntry(label = it.label, packageName = it.packageName) }
        val notLaunchable = current.filter { it.packageName !in launchable.map(AllowlistEntry::packageName) }
        return (launchable + notLaunchable).map { entry ->
            Row(entry, CheckBox(this).apply {
                text = entry.label
                textSize = ROW_TEXT_SP
                setTextColor(Color.WHITE)
                isChecked = entry.packageName in currentPackages
                setPadding(dp(ROW_PADDING_DP), dp(ROW_PADDING_DP), 0, dp(ROW_PADDING_DP))
            })
        }
    }

    private fun buildEditorView(rows: List<Row>): View {
        val status = TextView(this).apply {
            textSize = STATUS_TEXT_SP
            setTextColor(STATUS_COLOR)
            gravity = Gravity.CENTER
            setPadding(0, dp(STATUS_PADDING_DP), 0, 0)
            visibility = View.GONE
        }

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rows.forEach { row -> list.addView(row.checkBox) }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(
                dp(SCREEN_PADDING_DP),
                dp(TITLE_TOP_PADDING_DP),
                dp(SCREEN_PADDING_DP),
                dp(SCREEN_PADDING_DP),
            )
            addView(
                TextView(this@AllowlistEditorActivity).apply {
                    text = EDITOR_TITLE
                    textSize = TITLE_TEXT_SP
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                },
            )
            addView(
                ScrollView(this@AllowlistEditorActivity).apply { addView(list) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            addView(status)
            addView(
                TextView(this@AllowlistEditorActivity).apply {
                    text = SAVE_ACTION
                    textSize = SAVE_TEXT_SP
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(SAVE_PADDING_DP), 0, dp(SAVE_PADDING_DP))
                    setOnClickListener {
                        val selected = rows.filter { it.checkBox.isChecked }.map { it.entry }
                        if (selected.isEmpty()) {
                            status.text = EMPTY_SAVE_MESSAGE
                            status.visibility = View.VISIBLE
                            return@setOnClickListener
                        }
                        allowlistRepository.save(selected)
                        finish()
                    }
                },
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PREFS_NAME = "dumb_switch_allowlist"
        const val EDITOR_TITLE = "Home screen apps"
        const val SAVE_ACTION = "Save"
        const val EMPTY_SAVE_MESSAGE = "Keep at least one app — the dumb home is never empty"
        const val TITLE_TEXT_SP = 28f
        const val ROW_TEXT_SP = 20f
        const val STATUS_TEXT_SP = 14f
        const val SAVE_TEXT_SP = 22f
        const val SCREEN_PADDING_DP = 24
        const val TITLE_TOP_PADDING_DP = 48
        const val ROW_PADDING_DP = 14
        const val STATUS_PADDING_DP = 16
        const val SAVE_PADDING_DP = 24
        val STATUS_COLOR = 0x66FFFFFF.toInt()
    }
}
