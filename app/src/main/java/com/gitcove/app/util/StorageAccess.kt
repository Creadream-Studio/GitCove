package com.gitcove.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * 「所有文件访问」权限辅助（MANAGE_EXTERNAL_STORAGE）。
 *
 * GitCove 需要在任意目录执行 Git 操作（JGit 直接读写 File）。
 * 目录选择不再走系统 SAF 选择器（ACTION_OPEN_DOCUMENT_TREE）：
 * 其树 URI 在多级目录下换算真实路径会被截断成顶层目录，
 * 且系统选择器界面默认隐藏以 . 开头的文件/文件夹；
 * 现改用应用内目录浏览器（ui/components/DirectoryPickerDialog），
 * 直接以 File API 列目录，因此本对象只负责权限检查与授权引导。
 *
 * Android 10 及以下传统外部存储无需「所有文件访问」授权。
 */
object StorageAccess {

    /** 是否已拥有「所有文件访问」权限 */
    fun hasAllFilesAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            Environment.isExternalStorageManager()

    /** 跳转系统设置页请求「所有文件访问」权限 */
    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.recoverCatching {
            // 个别 ROM 不支持带包名的页面，退回总开关页
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
