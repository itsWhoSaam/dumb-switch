package com.houseofai.dumbswitch

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
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

    private val quietModeRepository by lazy {
        QuietModeRepository(getSharedPreferences(QUIET_PREFS_NAME, MODE_PRIVATE))
    }

    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
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
            addView(buildQuietModeToggle())
            addView(
                ScrollView(this@AllowlistEditorActivity).apply { addView(list) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            addView(
                TextView(this@AllowlistEditorActivity).apply {
                    text = GUARD_ROW
                    textSize = GUARD_ROW_TEXT_SP
                    setTextColor(GUARD_ROW_COLOR)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(GUARD_ROW_TOP_PADDING_DP), 0, 0)
                    // The system exposes no per-service deep link, so this opens the
                    // Accessibility list; the row names the service label to look for.
                    setOnClickListener {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                },
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

    /**
     * Quiet dumb mode consent (v0.2 spec, feature 3). Persists immediately on check — a toggle
     * that waits for the allowlist Save button would surprise. Enabling needs the notification
     * policy special-access grant: without it the user is sent to the grant screen and the
     * checkbox reverts, so a persisted "on" never exists without the grant (a DND toggle that
     * silently did nothing would be worse).
     */
    private fun buildQuietModeToggle(): View = CheckBox(this).apply {
        text = QUIET_MODE_LABEL
        textSize = QUIET_TEXT_SP
        setTextColor(Color.WHITE)
        isChecked = quietModeRepository.enabled()
        setPadding(dp(ROW_PADDING_DP), dp(TITLE_TOP_PADDING_DP), 0, 0)
        setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                quietModeRepository.setEnabled(false)
                return@setOnCheckedChangeListener
            }
            if (notificationManager.isNotificationPolicyAccessGranted) {
                quietModeRepository.setEnabled(true)
            } else {
                isChecked = false
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "dumb_switch_allowlist"
        const val QUIET_PREFS_NAME = "dumb_switch_quiet"
        const val EDITOR_TITLE = "Home screen apps"
        const val QUIET_MODE_LABEL = "Quiet dumb mode — silence notifications on the dumb home"
        const val QUIET_TEXT_SP = 16f
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
        const val GUARD_ROW = "Dumb mode guard — enable in Accessibility settings"
        const val GUARD_ROW_TEXT_SP = 16f
        const val GUARD_ROW_TOP_PADDING_DP = 28
        val STATUS_COLOR = 0x66FFFFFF.toInt()
        val GUARD_ROW_COLOR = 0xB3FFFFFF.toInt()
    }
}
