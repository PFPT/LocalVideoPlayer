package com.pfpt.localvideoplayer.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.pfpt.localvideoplayer.ui.theme.LauncherIcon

object LauncherIconSwitcher {

    fun apply(context: Context, icon: LauncherIcon): Boolean {
        return try {
            val packageManager = context.packageManager
            val packageName = context.packageName
            LauncherIcon.entries.forEach { candidate ->
                val component = ComponentName(packageName, "$packageName.${candidate.componentSuffix}")
                val newState = if (candidate == icon) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                packageManager.setComponentEnabledSetting(
                    component,
                    newState,
                    PackageManager.DONT_KILL_APP,
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun current(context: Context): LauncherIcon {
        val packageManager = context.packageManager
        val packageName = context.packageName
        LauncherIcon.entries.forEach { candidate ->
            val component = ComponentName(packageName, "$packageName.${candidate.componentSuffix}")
            val state = packageManager.getComponentEnabledSetting(component)
            val enabled = when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
                else -> candidate == LauncherIcon.Default
            }
            if (enabled) return candidate
        }
        return LauncherIcon.Default
    }
}
