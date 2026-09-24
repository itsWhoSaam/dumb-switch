package com.houseofai.dumbswitch

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SmartAppsResolverTest {

    private val pm = mock(PackageManager::class.java)

    private fun resolveInfo(pkg: String, activity: String): ResolveInfo = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            packageName = pkg
            name = activity
        }
    }

    @Test
    fun `lists every launchable app except the launcher itself, sorted case-insensitively`() {
        // Plain-JVM stubs cannot exercise loadLabel (mockable android.jar returns null), so labels
        // fall back to package names — which still pins the ordering and exclusion logic.
        `when`(pm.queryIntentActivities(any(), anyInt())).thenReturn(
            listOf(
                resolveInfo("com.self.launcher", "com.self.launcher.Home"),
                resolveInfo("com.example.Zebra", "com.example.Zebra.Zebra"),
                resolveInfo("com.example.alpha", "com.example.alpha.Main"),
            ),
        )
        `when`(pm.getLaunchIntentForPackage("com.example.Zebra")).thenReturn(mock(Intent::class.java))
        `when`(pm.getLaunchIntentForPackage("com.example.alpha")).thenReturn(mock(Intent::class.java))

        val resolved = SmartAppsResolver(pm, selfPackage = "com.self.launcher").resolve()

        assertEquals(
            listOf("com.example.alpha", "com.example.Zebra"),
            resolved.map { it.label },
        )
    }

    @Test
    fun `entries that yield no launch intent are dropped rather than rendered dead`() {
        `when`(pm.queryIntentActivities(any(), anyInt())).thenReturn(
            listOf(
                resolveInfo("com.example.live", "com.example.live.Main"),
                resolveInfo("com.example.broken", "com.example.broken.Main"),
            ),
        )
        `when`(pm.getLaunchIntentForPackage("com.example.live")).thenReturn(mock(Intent::class.java))
        `when`(pm.getLaunchIntentForPackage("com.example.broken")).thenReturn(null)

        val resolved = SmartAppsResolver(pm, selfPackage = "com.self.launcher").resolve()

        assertEquals(listOf("com.example.live"), resolved.map { it.label })
    }

    @Test
    fun `no launchable apps resolve to an empty list`() {
        `when`(pm.queryIntentActivities(any(), anyInt())).thenReturn(emptyList())

        assertTrue(SmartAppsResolver(pm, selfPackage = "com.self.launcher").resolve().isEmpty())
    }
}
