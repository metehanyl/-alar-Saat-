package com.metehanyl.calarsaat.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Xiaomi/Huawei/Oppo/Vivo/etc. kill backgrounded apps or block their lock-screen pop-ups
 * unless the user manually whitelists the app in a manufacturer-specific settings screen that
 * standard Android APIs can't query or grant. This opens the right screen for known OEMs so the
 * alarm can still show over the lock screen when the device's own battery/app manager would
 * otherwise suppress it.
 */
object OemPermissionHelper {

    private val knownIntents: List<Intent> by lazy {
        listOf(
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.securitycenter.permission.AppPermissionsEditorActivity"
            ),
            Intent().setClassName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            Intent().setClassName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            ),
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            ),
            Intent().setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            Intent().setClassName(
                "com.letv.android.letvsafe",
                "com.letv.android.letvsafe.AutobootManageActivity"
            ),
            Intent().setClassName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.autostart.AutoStartActivity"
            ),
            Intent().setClassName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            )
        )
    }

    /** Opens the first resolvable manufacturer-specific autostart/background-permission screen, or app settings as a fallback. */
    fun openAutoStartSettings(context: Context) {
        for (intent in knownIntents) {
            try {
                context.startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
        openAppDetailsSettings(context)
    }

    private fun openAppDetailsSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }

    /** True for manufacturers known to kill/restrict background apps beyond stock Android's Doze. */
    fun isRestrictiveOem(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return RESTRICTIVE_MANUFACTURERS.any { manufacturer.contains(it) }
    }

    private val RESTRICTIVE_MANUFACTURERS = listOf(
        "xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "vivo", "iqoo",
        "realme", "oneplus", "letv", "asus", "samsung"
    )
}
