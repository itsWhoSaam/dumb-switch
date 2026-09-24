package com.houseofai.dumbswitch

import android.content.Intent
import android.content.pm.PackageManager

/** One hardcoded home-screen entry. The five essentials are fixed for v0 (spec, locked decisions). */
data class AllowlistEntry(val label: String, val packageName: String)

/** A resolved entry: what the dumb home shows and what a tap launches. */
data class ResolvedEntry(
    val entry: AllowlistEntry,
    /** Installed app's label when resolvable, otherwise the hardcoded label. */
    val label: String,
    /** Launch intent for the real app; null means the tap is a no-op. */
    val launchIntent: Intent?,
)

object Allowlist {
    /** Spec table: Phone, Messages, Maps, Camera, Clock — in home-screen order. */
    val ENTRIES: List<AllowlistEntry> = listOf(
        AllowlistEntry("Phone", "com.google.android.dialer"),
        AllowlistEntry("Messages", "com.google.android.apps.messaging"),
        AllowlistEntry("Maps", "com.google.android.apps.maps"),
        AllowlistEntry("Camera", "com.google.android.GoogleCamera"),
        AllowlistEntry("Clock", "com.google.android.deskclock"),
    )
}

/**
 * Resolves the hardcoded allowlist against [PackageManager]. Visibility comes from the manifest's
 * targeted <queries> declarations — no QUERY_ALL_PACKAGES. Resolution never throws: an entry that
 * cannot be resolved still renders (with its hardcoded label) but launches nothing.
 */
class AllowlistResolver(private val packageManager: PackageManager) {

    fun resolve(entries: List<AllowlistEntry> = Allowlist.ENTRIES): List<ResolvedEntry> =
        entries.map(::resolveEntry)

    private fun resolveEntry(entry: AllowlistEntry): ResolvedEntry = ResolvedEntry(
        entry = entry,
        label = installedLabel(entry) ?: entry.label,
        launchIntent = packageManager.getLaunchIntentForPackage(entry.packageName),
    )

    /**
     * The deprecated int-flag variant of getApplicationInfo is intentional: the API-33
     * ApplicationInfoFlags variant cannot be exercised in plain-JVM unit tests, and this app has
     * no reason to take on Robolectric for one label lookup. Works unchanged on API 33+.
     */
    @Suppress("DEPRECATION")
    private fun installedLabel(entry: AllowlistEntry): String? = runCatching {
        val appInfo = packageManager.getApplicationInfo(entry.packageName, 0) ?: return@runCatching null
        packageManager.getApplicationLabel(appInfo)?.toString()
    }.getOrNull()
}
