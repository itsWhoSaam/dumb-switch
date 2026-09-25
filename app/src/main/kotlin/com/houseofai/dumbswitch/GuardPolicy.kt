package com.houseofai.dumbswitch

/**
 * Pure decision logic for the overlay guard (v0.2 spec, feature 2): whether a foreground app
 * should be covered while dumb mode is active. [DumbGuardService] only applies what [decide]
 * returns — every rule lives here so the full decision table stays plain-JVM testable.
 *
 * The guard raises the cost of the feed; it does not hide it. Smart mode is the total escape,
 * and the service itself does nothing until the user enables it in Android's Accessibility
 * settings — the guard is bypassable by design (findings log F2).
 */
object GuardPolicy {

    /** What the foreground should get: render normally, or cover with the dumb overlay. */
    sealed interface Decision {
        data object Allow : Decision
        data object Cover : Decision
    }

    // Prefix rules rather than exact matches: system surfaces and keyboards ship as multiple
    // packages under one family (sub-processes, vendor IME variants), and covering any of them
    // traps the user in the overlay instead of the feed.
    private val EXEMPT_PREFIXES = listOf(
        "com.android.systemui", // status bar, recents, system dialogs
        "com.google.android.inputmethod", // keyboards — never cover the IME
        "com.android.inputmethod",
    )

    fun decide(
        foregroundPackage: String,
        mode: Mode,
        allowlisted: Set<String>,
        selfPackage: String,
    ): Decision = when {
        mode is Mode.Smart -> Decision.Allow // the escape is total
        foregroundPackage == selfPackage -> Decision.Allow // our own screens
        EXEMPT_PREFIXES.any(foregroundPackage::startsWith) -> Decision.Allow
        foregroundPackage in allowlisted -> Decision.Allow
        else -> Decision.Cover // everything else, in dumb
    }
}
