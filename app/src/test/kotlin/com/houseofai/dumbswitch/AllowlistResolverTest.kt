package com.houseofai.dumbswitch

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AllowlistResolverTest {

    private val pm = mock(PackageManager::class.java)

    @Test
    fun `allowlist holds exactly the five spec entries in order`() {
        assertEquals(
            listOf(
                "Phone" to "com.google.android.dialer",
                "Messages" to "com.google.android.apps.messaging",
                "Maps" to "com.google.android.apps.maps",
                "Camera" to "com.google.android.GoogleCamera",
                "Clock" to "com.google.android.deskclock",
            ),
            Allowlist.ENTRIES.map { it.label to it.packageName },
        )
    }

    @Test
    fun `installed entries resolve with launch intents and hardcoded labels`() {
        Allowlist.ENTRIES.forEach { entry ->
            `when`(pm.getLaunchIntentForPackage(entry.packageName)).thenReturn(mock(Intent::class.java))
        }

        val resolved = AllowlistResolver(pm).resolve()

        assertEquals(5, resolved.size)
        resolved.forEach { result ->
            assertNotNull("${result.entry.label} should resolve a launch intent", result.launchIntent)
            assertEquals(result.entry.label, result.label)
        }
    }

    @Test
    fun `uninstalled entry still renders with its hardcoded label and no launch intent`() {
        val missing = AllowlistEntry("Maps", "com.google.android.apps.maps")
        `when`(pm.getLaunchIntentForPackage(missing.packageName)).thenReturn(null)

        val resolved = AllowlistResolver(pm).resolve(listOf(missing))

        assertEquals(1, resolved.size)
        assertEquals("Maps", resolved[0].label)
        assertNull(resolved[0].launchIntent)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `installed label is preferred over the hardcoded label`() {
        val entry = AllowlistEntry("Phone", "com.google.android.dialer")
        val appInfo = mock(ApplicationInfo::class.java)
        `when`(pm.getLaunchIntentForPackage(entry.packageName)).thenReturn(mock(Intent::class.java))
        `when`(pm.getApplicationInfo(entry.packageName, 0)).thenReturn(appInfo)
        `when`(pm.getApplicationLabel(appInfo)).thenReturn("Phone by Google")

        val resolved = AllowlistResolver(pm).resolve(listOf(entry))

        assertEquals("Phone by Google", resolved[0].label)
        assertNotNull(resolved[0].launchIntent)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `label lookup failure falls back to the hardcoded label`() {
        val entry = AllowlistEntry("Clock", "com.google.android.deskclock")
        `when`(pm.getLaunchIntentForPackage(entry.packageName)).thenReturn(mock(Intent::class.java))
        `when`(pm.getApplicationInfo(entry.packageName, 0)).thenThrow(PackageManager.NameNotFoundException())

        val resolved = AllowlistResolver(pm).resolve(listOf(entry))

        assertEquals("Clock", resolved[0].label)
        assertNotNull(resolved[0].launchIntent)
    }
}
