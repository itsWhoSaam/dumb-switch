package com.houseofai.dumbswitch

import android.app.NotificationManager
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Recording stand-in for the real [NotificationDndController]. The mute cycle is simulated
 * faithfully: mute() captures whatever filter was current and applies PRIORITY; restore() puts
 * the passed filter back — so a user-set DND that survives a full cycle is observable.
 */
private class FakeDnd : DndController {
    val calls = mutableListOf<String>()
    var currentFilter = NotificationManager.INTERRUPTION_FILTER_ALL

    override fun mute(): Int {
        calls += "mute"
        val previous = currentFilter
        currentFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        return previous
    }

    override fun restore(previousFilter: Int) {
        calls += "restore"
        currentFilter = previousFilter
    }
}

/** Same shape as the other suites' fakes: in-memory, writes immediately visible to readers. */
private class FakeQuietPrefs : SharedPreferences {
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

/**
 * The quiet-mode transition table (v0.2 spec, feature 3) over a fake [DndController]:
 * no consent, no DND calls; mute exactly once per dumb landing while consented; restore of the
 * captured filter on entering smart mode — never a blind reset to ALL.
 */
class QuietModeTest {

    private val dnd = FakeDnd()
    private val controller = QuietModeController(dnd)

    @Test
    fun `toggle off produces no DND calls, ever`() {
        controller.onDumbEntered(quietEnabled = false)
        controller.onDumbEntered(quietEnabled = false) // repeated resumes too
        controller.onSmartEntered()

        assertEquals(emptyList<String>(), dnd.calls)
    }

    @Test
    fun `landing dumb with the toggle on mutes and captures the prior filter`() {
        dnd.currentFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS // a DND the user set

        controller.onDumbEntered(quietEnabled = true)

        assertEquals(listOf("mute"), dnd.calls)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, dnd.currentFilter)
    }

    @Test
    fun `entering smart restores the filter captured before muting, never a blind reset to ALL`() {
        dnd.currentFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS
        controller.onDumbEntered(quietEnabled = true)

        controller.onSmartEntered()

        assertEquals(listOf("mute", "restore"), dnd.calls)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, dnd.currentFilter)
    }

    @Test
    fun `repeated dumb resumes do not double-apply the mute`() {
        controller.onDumbEntered(quietEnabled = true)
        controller.onDumbEntered(quietEnabled = true)
        controller.onDumbEntered(quietEnabled = true)

        assertEquals(listOf("mute"), dnd.calls)
    }

    @Test
    fun `a second mute cycle captures afresh`() {
        controller.onDumbEntered(quietEnabled = true)
        controller.onSmartEntered()

        controller.onDumbEntered(quietEnabled = true)
        controller.onSmartEntered()

        assertEquals(listOf("mute", "restore", "mute", "restore"), dnd.calls)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, dnd.currentFilter)
    }

    @Test
    fun `withdrawing consent undoes a live mute`() {
        dnd.currentFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS
        controller.onDumbEntered(quietEnabled = true)

        controller.onDumbEntered(quietEnabled = false)

        assertEquals(listOf("mute", "restore"), dnd.calls)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, dnd.currentFilter)
    }

    @Test
    fun `the toggle defaults off and survives a fresh repository over the same prefs`() {
        val prefs = FakeQuietPrefs()
        assertEquals(false, QuietModeRepository(prefs).enabled())

        QuietModeRepository(prefs).setEnabled(true)

        // Fresh instance over the same store — the process-death shape.
        assertEquals(true, QuietModeRepository(prefs).enabled())
        assertEquals(setOf("quiet_dumb_mode_enabled"), prefs.getAll().keys)
    }
}
