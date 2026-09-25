package com.houseofai.dumbswitch

import android.app.NotificationManager

/**
 * Apply/restore for the quiet-dumb-mode interruption filter, behind an interface so transitions
 * test plain-JVM with a fake (v0.2 spec, feature 3).
 */
interface DndController {
    /** Activates the quiet filter and returns the interruption filter that was active. */
    fun mute(): Int

    fun restore(previousFilter: Int)
}

/**
 * [DndController] over the real [NotificationManager].
 *
 * On targetSdk 35 `setInterruptionFilter` no longer flips global DND — it creates an implicit
 * AutomaticZenRule (findings log F5). The exact Android 15 semantics are verified on device
 * during this feature's checklist; the named fallback is an app-owned AutomaticZenRule.
 */
class NotificationDndController(
    private val notifications: NotificationManager,
) : DndController {
    override fun mute(): Int {
        val previous = notifications.currentInterruptionFilter
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        return previous
    }

    override fun restore(previousFilter: Int) {
        notifications.setInterruptionFilter(previousFilter)
    }
}
