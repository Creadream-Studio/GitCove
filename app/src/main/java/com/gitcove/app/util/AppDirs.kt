package com.gitcove.app.util

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * 应用统一目录规划（v0.4.8 起）。
 *
 * 背景：仓库与日志原先存在内部私有目录（/data/data/<pkg>/files），普通文件管理器
 * 无法取用——闪退日志导不出去、仓库文件也难以配合其它工具（编辑器 / MT 管理器）使用。
 *
 * 规划：
 * - 外部应用专属目录 /storage/emulated/0/Android/data/com.gitcove.app/files/：
 *   - repos/   默认仓库根（克隆 / 新建的默认父目录，无需任何存储权限即可读写）
 *   - log/     操作日志（按月分文件）
 *   - crash/   崩溃堆栈
 *   外部应用目录同样只有本应用可写，但用户通过数据线 / 文件管理器可见可导出。
 * - 内部私有目录（filesDir）保留敏感数据：访问令牌、SSH 私钥、AI 配置加密件、
 *   仓库元数据 repos.json 等，权限约束更严格。
 *
 * [externalFiles] 首选 Context.getExternalFilesDir；个别魔改 ROM 返回 null 时手工拼
 * 出 /storage/emulated/0/Android/data/<包名>/files（主外部存储约定路径），目录建不出
 * 再整体回退内部 filesDir——目录规划失败不应让应用崩溃。
 */
object AppDirs {

    /** 外部应用专属根目录（/storage/emulated/0/Android/data/<包名>/files） */
    fun externalFiles(context: Context): File {
        context.getExternalFilesDir(null)?.let { return it }
        val manual = File(
            Environment.getExternalStorageDirectory(),
            "Android/data/${context.packageName}/files"
        )
        if (manual.isDirectory || manual.mkdirs()) return manual
        return context.filesDir
    }

    /** 默认仓库根：外部应用目录/repos */
    fun reposRoot(context: Context): File = File(externalFiles(context), "repos")

    /** 操作日志目录：外部应用目录/log */
    fun logDir(context: Context): File = File(externalFiles(context), "log")

    /** 崩溃堆栈目录：外部应用目录/crash */
    fun crashDir(context: Context): File = File(externalFiles(context), "crash")

    /**
     * 旧版数据一次性迁移（v0.4.7 及之前装过才有效果；IO 线程调用）。
     *
     * - log / crash：逐文件搬移（量小，rename 同分区瞬时完成，跨分区退化为复制删除）；
     * - repos：整目录 rename；成功后通过 [onRepoMoved] 回调通知上层重写 repos.json
     *   里的仓库绝对路径（旧前缀 → 新前缀）。rename 失败（仓库正被打开 / 跨存储）
     *   则原目录保留，旧仓库照常可用，仅新仓库默认走新目录。
     *
     * 返回值：实际搬移的仓库数量（用于日志记录）。
     */
    fun migrate(context: Context, onRepoMoved: (oldPrefix: String, newPrefix: String) -> Unit): Int {
        val ext = externalFiles(context)
        val movedLogs = moveChildren(File(context.filesDir, "log"), File(ext, "log"))
        val movedCrashes = moveChildren(File(context.filesDir, "crash"), File(ext, "crash"))

        var movedRepos = 0
        val oldRepos = File(context.filesDir, "repos")
        val newRepos = File(ext, "repos")
        if (oldRepos.isDirectory && oldRepos.listFiles()?.isNotEmpty() == true) {
            val target = if (newRepos.isDirectory) {
                // 目标已有内容（异常场景）：并入而非覆盖
                moveChildren(oldRepos, newRepos).also { oldRepos.delete() }
                null
            } else {
                newRepos.parentFile?.mkdirs()
                if (oldRepos.renameTo(newRepos)) newRepos else null
            }
            if (target != null) {
                onRepoMoved(oldRepos.absolutePath, newRepos.absolutePath)
                movedRepos = 1
            }
        }
        return movedRepos + movedLogs + movedCrashes
    }

    /** 把 src 目录下的直接子项全部搬入 dst（不递归合并，文件同名时跳过），返回搬移数量 */
    private fun moveChildren(src: File, dst: File): Int {
        if (!src.isDirectory) return 0
        var moved = 0
        dst.mkdirs()
        src.listFiles()?.forEach { f ->
            val target = File(dst, f.name)
            if (!target.exists() && (f.renameTo(target) || (f.copyRecursively(target) && f.delete()))) {
                moved++
            }
        }
        if (src.listFiles()?.isEmpty() == true) src.delete()
        return moved
    }
}
