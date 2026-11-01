package com.gitcove.app.data.log

import android.app.Application
import android.os.Build
import android.util.Log
import com.gitcove.app.BuildConfig
import com.gitcove.app.util.AppDirs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃捕获（魔改 ROM 排查用）。
 *
 * 电视 / 盒子等阉割手机系统上的闪退往往无法用 adb 复现、也没有系统日志可查。
 * 方案：接管默认未捕获异常处理器——闪退发生时把完整堆栈（含设备 / 系统版本 /
 * 应用版本信息）写入外部应用目录 crash/crash-<时间戳>.txt，同时在操作日志里追加
 * 一条摘要；写完把异常交还系统默认处理器（保持正常的崩溃退出行为，不影响系统
 * dialog）。
 *
 * 目录随 v0.4.8 迁到 /storage/emulated/0/Android/data/<包名>/files/crash/：
 * 用户无需导出功能也能用文件管理器直接取走；极少数 ROM 拿不到外部目录时
 * （AppDirs 回退内部 filesDir）仍可用应用内「设置 → 日志」查看 / 分享。
 *
 * 崩溃文件在「设置 → 日志」页可见、可查看全文、可分享、可删除；只保留最近 10 份防止占满磁盘。
 */
object CrashCapture {

    private const val TAG = "CrashCapture"
    private const val KEEP = 10

    /** 安装全局未捕获异常处理器（Application.onCreate 调用一次） */
    fun install(app: Application, opLog: OpLog) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val file = write(app, thread.name, throwable)
                opLog.append(
                    "CRASH",
                    "${throwable.javaClass.simpleName}: ${throwable.message ?: ""} → ${file.name}"
                )
            }
            // 交还系统默认处理（崩溃退出 / 系统对话框），捕获只做记录不做吞异常
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(app: Application, threadName: String, throwable: Throwable): File {
        val dir = AppDirs.crashDir(app).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash-$stamp.txt")
        file.writeText(
            buildString {
                appendLine("time:     $stamp")
                appendLine("device:   ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
                appendLine("app:      GitCove v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("thread:   $threadName")
                appendLine()
                appendLine(Log.getStackTraceString(throwable))
            }
        )
        // 只保留最近 KEEP 份
        dir.listFiles { f -> f.name.startsWith("crash-") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP)
            ?.forEach { runCatching { it.delete() } }
        return file
    }

    /** 全部崩溃文件（新的在前） */
    fun list(context: android.content.Context): List<File> =
        AppDirs.crashDir(context).listFiles { f -> f.name.startsWith("crash-") }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    /** 清空全部崩溃文件（日志页「清空」按钮） */
    fun clear(context: android.content.Context) {
        AppDirs.crashDir(context).listFiles()?.forEach { runCatching { it.delete() } }
    }

    /** 读取崩溃文件内容（查看全文） */
    fun read(file: File): String = runCatching { file.readText() }.getOrDefault("(读取失败)")
}
