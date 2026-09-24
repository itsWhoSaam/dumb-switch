package com.houseofai.dumbswitch

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/** One row of the smart list: what it shows and what a tap launches. */
data class SmartApp(val label: String, val launchIntent: Intent)

/**
 * The smart list is every launchable app the manifest's targeted <queries> MAIN/LAUNCHER
 * declaration makes visible — no QUERY_ALL_PACKAGES. The launcher itself is excluded: a home
 * screen that lists itself is noise.
 */
class SmartAppsResolver(
    private val packageManager: PackageManager,
    private val selfPackage: String,
) {

    fun resolve(): List<SmartApp> =
        (packageManager.queryIntentActivities(launchableQuery(), 0) ?: emptyList())
            .mapNotNull(::toSmartApp)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    // Statement form inside apply: the fluent setters' return values are discarded, which keeps
    // this plain-JVM testable — the mockable android.jar nulls out chained fluent returns.
    private fun launchableQuery(): Intent = Intent().apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    /**
     * Launch intents come from getLaunchIntentForPackage rather than being built off the query
     * result — the same mockable pattern AllowlistResolver uses, which keeps this resolver
     * plain-JVM testable. An entry that yields no launch intent is dropped (omitting a broken row
     * beats rendering one that does nothing); the dumb home's allowlist is the path that renders
     * unresolvable entries.
     */
    private fun toSmartApp(info: ResolveInfo): SmartApp? {
        val pkg = info.activityInfo?.packageName ?: return null
        if (pkg == selfPackage) return null
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg) ?: return null
        val label = info.loadLabel(packageManager)?.toString().orEmpty().ifEmpty { pkg }
        return SmartApp(label = label, launchIntent = launchIntent)
    }
}
