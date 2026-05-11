package me.rerere.rikkahub.ui.components.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 后台定位权限辅助类
 *
 * Android 10 (API 29): 可以直接通过 ActivityResultContracts.RequestPermission 申请
 * Android 11+ (API 30+): 系统拒绝在请求对话框中显示"始终允许"，用户必须手动去设置页开启
 *
 * 推荐流程:
 * 1. 先申请前台定位权限 (ACCESS_FINE_LOCATION)
 * 2. 授权后，引导用户去系统设置页手动开启后台定位
 */
object LocationPermissionHelper {

    /**
     * 前台定位权限是否已授权
     */
    fun hasForegroundLocation(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 后台定位权限是否已授权
     *
     * Android 10 以下不需要后台定位权限（安装时自动授权）
     */
    fun hasBackgroundLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 前台已授权但后台未授权（需要引导用户去设置）
     */
    fun needsBackgroundLocationGuide(context: Context): Boolean {
        return hasForegroundLocation(context) && !hasBackgroundLocation(context)
    }

    /**
     * 跳转到系统应用详情页，让用户手动开启"始终允许"
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
