package com.houseofai.dumbswitch

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * In-memory stand-in for the on-disk prefs file, same shape as ModeRepositoryTest's: writes are
 * immediately visible to readers, and a fresh repository over the same instance is exactly the
 * process-death shape — all state lives in the store, none in the reader.
 */
private class FakeAllowlistPrefs : SharedPreferences {
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

class AllowlistRepositoryTest {

    private val prefs = FakeAllowlistPrefs()
    private val twoEntries = listOf(
        AllowlistEntry("Maps", "com.google.android.apps.maps"),
        AllowlistEntry("Signal", "org.thoughtcrime.securesms"),
    )

    @Test
    fun `returns the v0 five defaults when nothing is persisted`() {
        assertEquals(Allowlist.ENTRIES, PrefsAllowlistRepository(prefs).entries())
        assertTrue(prefs.store.isEmpty()) // defaults are not written back — they are the fallback
    }

    @Test
    fun `round-trips entries through save and entries`() {
        val repo = PrefsAllowlistRepository(prefs)

        repo.save(twoEntries)

        assertEquals(twoEntries, repo.entries())
    }

    @Test
    fun `persists across a fresh repository instance over the same prefs`() {
        PrefsAllowlistRepository(prefs).save(twoEntries)

        // Brand-new repository, same store — the process-death / reboot shape.
        assertEquals(twoEntries, PrefsAllowlistRepository(prefs).entries())
    }

    @Test
    fun `persists the saved order, not the list order of the defaults`() {
        PrefsAllowlistRepository(prefs).save(twoEntries.reversed())

        assertEquals(twoEntries.reversed(), PrefsAllowlistRepository(prefs).entries())
    }

    @Test
    fun `saving over a previous list replaces it`() {
        val repo = PrefsAllowlistRepository(prefs)
        repo.save(twoEntries)

        val replacement = listOf(AllowlistEntry("Clock", "com.google.android.deskclock"))
        repo.save(replacement)

        assertEquals(replacement, repo.entries())
    }

    @Test
    fun `rejects saving an empty list and stores nothing`() {
        val repo = PrefsAllowlistRepository(prefs)

        try {
            repo.save(emptyList())
            fail("saving an empty list must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("the dumb home keeps at least one app", expected.message)
        }
        assertTrue(prefs.store.isEmpty())
    }

    @Test
    fun `blank lines are ignored when reading a persisted value`() {
        prefs.store["allowlist_entries"] =
            "\nPhone|com.google.android.dialer\n\n   \nClock|com.google.android.deskclock\n"

        assertEquals(
            listOf(
                AllowlistEntry("Phone", "com.google.android.dialer"),
                AllowlistEntry("Clock", "com.google.android.deskclock"),
            ),
            PrefsAllowlistRepository(prefs).entries(),
        )
    }
}
