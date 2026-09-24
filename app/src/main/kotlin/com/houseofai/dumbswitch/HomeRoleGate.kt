package com.houseofai.dumbswitch

import android.app.Activity
import android.app.role.RoleManager

/**
 * First-run gate: asks the system to make Dumb Switch the default home via ROLE_HOME.
 * One dialog while the role is not held; accepting means it never prompts again.
 * Declining leaves the app openable from the drawer, so the next launch re-prompts.
 */
class HomeRoleGate(private val activity: Activity) {

    fun maybeRequestHomeRole() {
        val roleManager = activity.getSystemService(RoleManager::class.java) ?: return
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return
        if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return
        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        activity.startActivityForResult(intent, REQUEST_HOME_ROLE)
    }

    companion object {
        const val REQUEST_HOME_ROLE = 1
    }
}
