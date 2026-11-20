package com.habittags.app

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Process
import android.provider.Settings

object Permissions {
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlay(context: Context) = Settings.canDrawOverlays(context)

    fun hasDndAccess(context: Context) =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun usageAccessIntent(context: Context) = settingsIntent(context, Settings.ACTION_USAGE_ACCESS_SETTINGS)
    fun overlayIntent(context: Context) = settingsIntent(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
    fun dndAccessIntent(context: Context) =
        settingsIntent(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, detailAction = DND_DETAIL_ACTION)

    /** Not in the public SDK, but present in AOSP Settings: opens Mise's own Do Not Disturb toggle. */
    private const val DND_DETAIL_ACTION = "android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS"

    /**
     * Opens Mise's own toggle where the phone supports it ([detailAction] or the list action with a
     * package URI); otherwise the list, with the Settings extras that scroll to and highlight Mise.
     */
    private fun settingsIntent(context: Context, listAction: String, detailAction: String? = null): Intent {
        val pkg = Uri.parse("package:${context.packageName}")
        val candidates = listOfNotNull(
            detailAction?.let { Intent(it, pkg) },
            Intent(listAction, pkg),
        )
        return candidates.firstOrNull { it.resolveActivity(context.packageManager) != null }
            ?: Intent(listAction).apply {
                putExtra(":settings:fragment_args_key", context.packageName)
                putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", context.packageName) })
            }
    }
}
