package com.houseofai.dumbswitch

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The full guard decision table (v0.2 spec, feature 2): smart mode allows everything; in dumb
 * mode our own package, the SystemUI/IME prefixes, and allowlisted packages allow while
 * everything else covers.
 */
class GuardPolicyTest {

    private val self = "com.houseofai.dumbswitch"
    private val allowlisted = setOf(
        "com.google.android.dialer",
        "com.google.android.apps.messaging",
        "com.google.android.deskclock",
    )

    private fun decide(
        foregroundPackage: String,
        mode: Mode = Mode.Dumb,
        allowlisted: Set<String> = this.allowlisted,
    ): GuardPolicy.Decision =
        GuardPolicy.decide(
            foregroundPackage = foregroundPackage,
            mode = mode,
            allowlisted = allowlisted,
            selfPackage = self,
        )

    // Smart mode: the escape is total.

    @Test
    fun `smart mode allows a non-allowlisted app`() {
        assertEquals(GuardPolicy.Decision.Allow, decide("com.google.android.youtube", Mode.Smart(5)))
    }

    @Test
    fun `smart mode allows our own package`() {
        assertEquals(GuardPolicy.Decision.Allow, decide(self, Mode.Smart(12)))
    }

    @Test
    fun `smart mode allows system surfaces`() {
        assertEquals(GuardPolicy.Decision.Allow, decide("com.android.systemui", Mode.Smart(1)))
    }

    // Dumb mode, allow rules.

    @Test
    fun `our own package is allowed in dumb mode`() {
        assertEquals(GuardPolicy.Decision.Allow, decide(self))
    }

    @Test
    fun `the system ui prefix is allowed in dumb mode`() {
        assertEquals(GuardPolicy.Decision.Allow, decide("com.android.systemui"))
    }

    @Test
    fun `system ui subpackages are covered by the prefix`() {
        assertEquals(GuardPolicy.Decision.Allow, decide("com.android.systemui.recents"))
    }

    @Test
    fun `the google keyboard prefix is allowed in dumb mode`() {
        assertEquals(GuardPolicy.Decision.Allow, decide("com.google.android.inputmethod.latin"))
    }

    @Test
    fun `the aosp keyboard prefix is allowed in dumb mode`() {
        assertEquals(GuardPolicy.Decision.Allow, decide("com.android.inputmethod.latin"))
    }

    @Test
    fun `an allowlisted app is allowed in dumb mode`() {
        assertEquals(GuardPolicy.Decision.Allow, decide("com.google.android.dialer"))
        assertEquals(GuardPolicy.Decision.Allow, decide("com.google.android.deskclock"))
    }

    // Dumb mode, cover rules.

    @Test
    fun `a non-allowlisted app is covered in dumb mode`() {
        assertEquals(GuardPolicy.Decision.Cover, decide("com.google.android.youtube"))
        assertEquals(GuardPolicy.Decision.Cover, decide("org.mozilla.firefox"))
    }

    @Test
    fun `a package that merely shares an allowlisted prefix is covered`() {
        // Membership is exact: only the exemption families are prefix rules.
        assertEquals(GuardPolicy.Decision.Cover, decide("com.google.android.dialerx"))
    }

    @Test
    fun `removing an app from the allowlist flips it to covered`() {
        // The editor controls the guard: the same package before and after a save.
        val withExtra = allowlisted + "com.google.android.youtube"
        assertEquals(GuardPolicy.Decision.Allow, decide("com.google.android.youtube", allowlisted = withExtra))
        assertEquals(GuardPolicy.Decision.Cover, decide("com.google.android.youtube"))
    }

    @Test
    fun `an empty allowlist still covers everything not exempt`() {
        // Unreachable through the repository (the dumb home keeps at least one app), but the
        // guard degrades to covering rather than allowing if the set is ever empty.
        assertEquals(GuardPolicy.Decision.Cover, decide("com.google.android.youtube", allowlisted = emptySet()))
    }

    @Test
    fun `the exemption check is prefix-based, including lookalikes`() {
        // Deliberate fail-open: "com.android.systemuix" starts with the SystemUI family prefix,
        // so it allows. A false cover traps the user; a false allow just shows the feed.
        assertEquals(GuardPolicy.Decision.Allow, decide("com.android.systemuix"))
    }
}
