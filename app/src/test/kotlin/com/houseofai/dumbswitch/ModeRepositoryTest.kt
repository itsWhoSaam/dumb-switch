package com.houseofai.dumbswitch

import android.content.SharedPreferences
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Mutable fake clock — tests advance it explicitly, so boundary instants are exact, never slept. */
private class FakeClock(start: Instant = Instant.EPOCH) : Clock() {
    private var now: Instant = start

    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this

    fun advanceBy(duration: Duration) {
        now = now.plus(duration)
    }
}

/**
 * In-memory stand-in for the on-disk prefs file. Writes are immediately visible to readers — like
 * the real in-memory cache behind SharedPreferences — and a fresh ModeRepository over the same
 * instance is exactly the process-death shape: all state lives in the store, none in the reader.
 */
private class FakePrefs : SharedPreferences {
    val store = LinkedHashMap<String, Any>()

    override fun getAll(): MutableMap<String, *> = LinkedHashMap(store)
    override fun getString(key: String?, defValue: String?): String? = store[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (store[key] as? Set<*>)?.mapTo(mutableSetOf()) { it.toString() } ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = store[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = store[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = store[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = store[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = store.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values)
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)
        override fun remove(key: String?): SharedPreferences.Editor = apply { store.remove(key) }
        override fun clear(): SharedPreferences.Editor = apply { store.clear() }
        override fun apply() = Unit
        override fun commit(): Boolean = true

        private fun put(key: String?, value: Any?): SharedPreferences.Editor = apply {
            if (value == null) store.remove(key) else store[key!!] = value
        }
    }
}

class ModeRepositoryTest {

    private val clock = FakeClock()
    private val prefs = FakePrefs()

    @Test
    fun `dumb when no escape is stored`() {
        assertEquals(Mode.Dumb, ModeRepository(prefs, clock).currentMode())
    }

    @Test
    fun `startEscape defaults to thirty minutes and stores the expiry under the spec key`() {
        ModeRepository(prefs, clock).startEscape()

        assertEquals(
            Instant.EPOCH.plus(Duration.ofMinutes(30)).toEpochMilli(),
            prefs.getLong("escape_until_epoch_ms", 0L),
        )
        assertEquals(setOf("escape_until_epoch_ms"), prefs.getAll().keys)
    }

    @Test
    fun `smart immediately after starting reads the full thirty minutes`() {
        val repo = ModeRepository(prefs, clock)
        repo.startEscape()
        clock.advanceBy(Duration.ofMillis(1)) // a real render happens after the clock has moved

        assertEquals(Mode.Smart(minutesRemaining = 30), repo.currentMode())
        assertEquals(Duration.ofMinutes(30).minusMillis(1), repo.remainingEscape())
    }

    @Test
    fun `minutesRemaining rounds a partial minute up to the next whole minute`() {
        val repo = ModeRepository(prefs, clock)
        repo.startEscape()
        clock.advanceBy(Duration.ofMinutes(5)) // exactly 25:00 remaining

        // Spec formula: toMinutes() + 1 — a whole-minute remainder still counts as one more.
        assertEquals(Mode.Smart(minutesRemaining = 26), repo.currentMode())
    }

    @Test
    fun `one second into a minute drops the reported count`() {
        val repo = ModeRepository(prefs, clock)
        repo.startEscape()
        clock.advanceBy(Duration.ofMinutes(5).plusSeconds(1)) // 24:59 remaining

        assertEquals(Mode.Smart(minutesRemaining = 25), repo.currentMode())
    }

    @Test
    fun `dumb at exactly escapeUntil`() {
        val repo = ModeRepository(prefs, clock)
        repo.startEscape()
        clock.advanceBy(Duration.ofMinutes(30))

        assertEquals(Mode.Dumb, repo.currentMode())
        assertEquals(Duration.ZERO, repo.remainingEscape())
    }

    @Test
    fun `dumb after escapeUntil`() {
        val repo = ModeRepository(prefs, clock)
        repo.startEscape()
        clock.advanceBy(Duration.ofMinutes(30).plusMillis(1))

        assertEquals(Mode.Dumb, repo.currentMode())
    }

    @Test
    fun `custom duration escapes for exactly that long`() {
        val repo = ModeRepository(prefs, clock)
        repo.startEscape(Duration.ofMinutes(10))
        clock.advanceBy(Duration.ofMinutes(9))

        assertEquals(Mode.Smart(minutesRemaining = 2), repo.currentMode()) // 1:00 remaining

        clock.advanceBy(Duration.ofMinutes(1))
        assertEquals(Mode.Dumb, repo.currentMode())
    }

    @Test
    fun `state survives a fresh repository reading the same persisted prefs`() {
        ModeRepository(prefs, clock).startEscape()
        clock.advanceBy(Duration.ofMinutes(10))

        // Same prefs store, brand-new repository and clock — the process-death / reboot shape.
        val afterRestart = ModeRepository(prefs, FakeClock(clock.instant()))
        assertEquals(Mode.Smart(minutesRemaining = 21), afterRestart.currentMode())
    }

    @Test
    fun `endEscape zeroes the stored window and lands dumb`() {
        val repo = ModeRepository(prefs, clock)
        repo.startEscape()
        clock.advanceBy(Duration.ofMinutes(5))
        assertEquals(Mode.Smart(minutesRemaining = 26), repo.currentMode()) // precondition: smart

        repo.endEscape()

        assertEquals(0L, prefs.getLong("escape_until_epoch_ms", -1L))
        assertEquals(Mode.Dumb, repo.currentMode())
        assertEquals(Duration.ZERO, repo.remainingEscape())
    }
}
