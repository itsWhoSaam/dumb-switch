package com.houseofai.dumbswitch

import android.content.SharedPreferences

/**
 * The persisted home-screen allowlist: what the dumb home renders, editable without a rebuild
 * (v0.2 spec, feature 1).
 */
interface AllowlistRepository {
    /** Entries to render; the v0 five when nothing is persisted yet. */
    fun entries(): List<AllowlistEntry>

    /** Persists the ordered list. Empty is rejected: the dumb home keeps at least one app. */
    fun save(entries: List<AllowlistEntry>)
}

/**
 * SharedPreferences-backed [AllowlistRepository], following the [ModeRepository] pattern: one
 * plain-text value, no new dependencies, plain-JVM testable.
 *
 * Encoding is one entry per line, `label|packageName`. Labels are captured at save time —
 * `getApplicationInfo` is not guaranteed for packages outside the manifest's targeted <queries>,
 * so the editor stores what it showed rather than re-resolving on render.
 */
class PrefsAllowlistRepository(private val prefs: SharedPreferences) : AllowlistRepository {

    override fun entries(): List<AllowlistEntry> {
        val raw = prefs.getString(KEY, null) ?: return Allowlist.ENTRIES
        return raw.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val (label, pkg) = line.split('|', limit = 2)
                AllowlistEntry(label = label, packageName = pkg)
            }
            .toList()
    }

    override fun save(entries: List<AllowlistEntry>) {
        require(entries.isNotEmpty()) { "the dumb home keeps at least one app" }
        prefs.edit()
            .putString(KEY, entries.joinToString("\n") { "${it.label}|${it.packageName}" })
            .apply()
    }

    private companion object {
        const val KEY = "allowlist_entries"
    }
}
