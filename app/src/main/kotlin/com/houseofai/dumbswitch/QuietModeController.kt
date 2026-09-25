package com.houseofai.dumbswitch

import android.content.SharedPreferences

/**
 * Pure transition logic for quiet dumb mode (v0.2 spec, feature 3): every decision lives here so
 * it stays plain-JVM testable — callers only apply what this computes.
 *
 * Mute is a consequence of the derived mode plus the toggle, never stored state of its own: the
 * captured filter lives only in memory, and everything else is re-derived on the next resume —
 * the same re-derive-on-resume decay model v0 shipped. After a process death mid-mute the next
 * dumb landing re-captures (the pre-mute filter is then our own applied filter), which is the
 * honest cost of keeping no quiet-mode state machine.
 */
class QuietModeController(private val dnd: DndController) {

    /** Filter captured by the live mute; null while unmuted. In-memory by design — see above. */
    private var mutedFilter: Int? = null

    /**
     * Landing on the dumb home (every [LauncherActivity] resume). Mutes exactly once per mute
     * cycle when the toggle is on; repeated resumes never double-apply. With the toggle off, no
     * DND call is ever initiated — and a mute left live when consent is withdrawn is undone.
     */
    fun onDumbEntered(quietEnabled: Boolean) {
        if (!quietEnabled) {
            unmute()
            return
        }
        if (mutedFilter == null) mutedFilter = dnd.mute()
    }

    /**
     * Entering smart mode — the escape confirmation. Always restores the filter captured before
     * muting, never a blind reset to ALL, so a DND the user set themselves survives.
     */
    fun onSmartEntered() = unmute()

    private fun unmute() {
        mutedFilter?.let(dnd::restore)
        mutedFilter = null
    }
}

/**
 * Persisted consent for quiet dumb mode, following the [ModeRepository] pattern: SharedPreferences,
 * no new dependencies, plain-JVM testable. Default off — DND is never touched without consent.
 */
class QuietModeRepository(private val prefs: SharedPreferences) {

    fun enabled(): Boolean = prefs.getBoolean(KEY, DEFAULT_ENABLED)

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY, value).apply()
    }

    private companion object {
        const val KEY = "quiet_dumb_mode_enabled"
        const val DEFAULT_ENABLED = false
    }
}
