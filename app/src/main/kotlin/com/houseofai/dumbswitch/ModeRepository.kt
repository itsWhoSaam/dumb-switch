package com.houseofai.dumbswitch

import android.content.SharedPreferences
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** The two v0 modes. Dumb is the default; smart is a timed escape that always decays back. */
sealed interface Mode {
    data object Dumb : Mode
    data class Smart(val minutesRemaining: Int) : Mode
}

/**
 * The only stateful class in v0. Persists one timestamp — escape_until_epoch_ms — in
 * [SharedPreferences] and derives the mode from an injected [Clock] on every read.
 *
 * Dumb mode is not a stored flag: it is the absence of a valid escape window. That is what makes
 * reboot and process-death safety structural — the timestamp survives, and everything else is
 * re-derived on the next render.
 */
class ModeRepository(
    private val prefs: SharedPreferences,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun currentMode(now: Instant = clock.instant()): Mode {
        val escapeUntilMs = prefs.getLong(KEY_ESCAPE_UNTIL, 0L)
        if (now.toEpochMilli() >= escapeUntilMs) return Mode.Dumb

        val remaining = Duration.between(now, Instant.ofEpochMilli(escapeUntilMs))
        return Mode.Smart(minutesRemaining = remaining.toMinutes().toInt() + 1)
    }

    /**
     * Remaining escape window, for the countdown banner's second-level tick. Duration.ZERO when no
     * escape is stored or the window has elapsed — mirroring [currentMode]'s dumb boundary.
     */
    fun remainingEscape(now: Instant = clock.instant()): Duration {
        val escapeUntilMs = prefs.getLong(KEY_ESCAPE_UNTIL, 0L)
        val remaining = Duration.between(now, Instant.ofEpochMilli(escapeUntilMs))
        return if (remaining.isNegative || remaining.isZero) Duration.ZERO else remaining
    }

    fun startEscape(duration: Duration = DEFAULT_ESCAPE) {
        prefs.edit()
            .putLong(KEY_ESCAPE_UNTIL, clock.instant().plus(duration).toEpochMilli())
            .apply()
    }

    /** Completes the state model; the UI never offers an early cancel — the countdown is the friction (spec). */
    fun endEscape() = prefs.edit().putLong(KEY_ESCAPE_UNTIL, 0L).apply()

    companion object {
        private const val KEY_ESCAPE_UNTIL = "escape_until_epoch_ms"
        val DEFAULT_ESCAPE: Duration = Duration.ofMinutes(30)
    }
}
