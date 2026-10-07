package com.gitcove.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * 「所有文件访问」权限辅助（MANAGE_EXTERNAL_STORAGE）+ 原生 SAF 目录选择结果转换。
 *
 * GitCove 需要在任意目录执行 Git 操作（JGit 直接读写 File），
 * 目录选择使用安卓原生 SAF 选择器（ACTION_OPEN_DOCUMENT_TREE，系统文件管理器界面），
 * 其返回 content:// 树 URI；本对象负责把它换算回真实文件路径供 JGit 使用。
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

    /**
     * 将原生目录选择器（ACTION_OPEN_DOCUMENT_TREE）返回的树 URI
     * 换算为真实文件路径，例如：
     *   content://com.android.externalstorage.documents/tree/primary%3ADocuments
     *     → /storage/emulated/0/Documents
     *   content://com.android.externalstorage.documents/tree/6E45-9E41%3AGit
     *     → /storage/6E45-9E41/Git（SD 卡等二级卷）
     *
     * 仅支持本地存储卷（externalstorage provider，即系统文件管理器中的
     * 内部存储 / SD 卡 / U 盘）；网盘等第三方 provider 无法换算，返回 null。
     */
    fun treeUriToDir(uri: Uri): File? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val treeId = uri.path
            ?.removePrefix("/tree/")
            ?.substringBefore('/')   // 去掉可能跟随的 /document/… 段
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val decoded = runCatching { java.net.URLDecoder.decode(treeId, "UTF-8") }
            .getOrDefault(treeId)
        val volume = decoded.substringBefore(':', "")
        val subPath = decoded.substringAfter(':', "")
        if (volume.isEmpty()) return null
        val volumeRoot = when (volume) {
            "primary" -> Environment.getExternalStorageDirectory()?.absolutePath
                ?: "/storage/emulated/0"
            else -> "/storage/$volume"
        }
        return File(volumeRoot, subPath)
    }

    /**
     * 将真实路径换算为 externalstorage 文档 URI，作为原生目录选择器的
     * 初始位置提示（EXTRA_INITIAL_URI，API 26+），让系统文件管理器直接
     * 打开到当前目录。路径不属于本地存储卷时返回 null（选择器用默认位置）。
     */
    fun pathToDocumentUri(path: String): Uri? {
        val normalized = File(path).absoluteFile.normalize().path
        val primaryRoot = Environment.getExternalStorageDirectory()?.absolutePath
            ?: "/storage/emulated/0"
        val documentId = when {
            normalized == primaryRoot -> "primary:"
            normalized.startsWith("$primaryRoot/") ->
                "primary:" + normalized.removePrefix("$primaryRoot/")
            normalized.startsWith("/storage/") -> {
                val volume = normalized.removePrefix("/storage/").substringBefore('/')
                if (volume.isEmpty() || volume == "emulated") return null
                val rest = normalized.removePrefix("/storage/$volume")
                if (rest.isEmpty()) "$volume:" else "$volume:$rest"
            }
            else -> return null
        }
        return runCatching {
            android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", documentId
            )
        }.getOrNull()
    }
}
