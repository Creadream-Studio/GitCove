package com.gitcove.app.util

import android.content.Context
import com.gitcove.app.data.log.OpLog
import com.gitcove.app.data.store.RepoStore
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 内置示例仓库（assets/sample/GitCoveDemo.zip）。
 *
 * 首次启动自动解压到默认仓库根并注册进仓库列表，新用户点进去即可在真实仓库里
 * 体验全部功能：提交图谱与多身份头像、文件管理、图片查看 / 图片对比、Markdown
 * 预览，以及一个特意保留的冲突现场（1 文本 + 1 图片，含未完成合并的索引）。
 *
 * 与普通仓库同一条通道（repoStore.add），所以同样可以随时删除；
 * 删除后不重装——装没装以标记为准，只装一次。
 */
object SampleRepoInstaller {

    private const val PREFS = "sample_repo"
    private const val KEY_DONE = "installed"

    /** 仓库目录名（默认仓库根下）与列表显示名 */
    const val DIR_NAME = "GitCoveDemo"
    const val DISPLAY_NAME = "GitCove 示例仓库"

    fun installIfNeeded(context: Context, repoStore: RepoStore, opLog: OpLog) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return
        val root = AppDirs.reposRoot(context)
        try {
            root.mkdirs()
            // 1. 解压到同分区临时目录。zip 内含完整 .git（未完成合并的冲突索引 / MERGE_HEAD），
            //    必须整目录落位，不能用 clone 之类的手段重建
            val tmp = File(root, "$DIR_NAME.installing")
            tmp.deleteRecursively()
            tmp.mkdirs()
            context.assets.open("sample/$DIR_NAME.zip").use { zip ->
                ZipInputStream(zip).use { zs ->
                    while (true) {
                        val e = zs.nextEntry ?: break
                        val out = File(tmp, e.name)
                        // 防路径穿越（Zip Slip）
                        if (!out.canonicalPath.startsWith(tmp.canonicalPath)) {
                            zs.closeEntry(); continue
                        }
                        if (e.isDirectory) out.mkdirs() else {
                            out.parentFile?.mkdirs()
                            zs.copyTo(out.outputStream())
                        }
                        zs.closeEntry()
                    }
                }
            }
            // 2. 原子落位：临时目录整体 rename 成正式目录（同分区瞬时完成）
            val target = File(root, DIR_NAME)
            target.deleteRecursively()
            if (!File(tmp, DIR_NAME).renameTo(target)) error("rename failed")
            tmp.deleteRecursively()
            // 3. 注册进仓库列表（与克隆 / 新建同一条通道，天然可删除）
            runBlocking {
                repoStore.add(name = DISPLAY_NAME, path = target.absolutePath, remoteUrl = null)
            }
            // 4. 最后才打标记：中途被杀则下次启动重试，不会留下半截仓库
            prefs.edit().putBoolean(KEY_DONE, true).apply()
            opLog.append("repo", "Sample repo installed: $DIR_NAME")
        } catch (t: Throwable) {
            runCatching { File(root, "$DIR_NAME.installing").deleteRecursively() }
            opLog.append("repo", "Sample repo install failed: ${t.message}")
        }
    }
}
