package com.gitcove.app.data.log

import com.gitcove.app.domain.model.LogEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 操作日志（功能 79：日志文件）
 * 按月分文件存储于 files/log/，支持查看、清空。
 */
class OpLog(private val dir: File) {

    private val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
    private val fileFmt = SimpleDateFormat("yyyy-MM", Locale.CHINA)

    fun append(tag: String, message: String) {
        runCatching {
            dir.mkdirs()
            val file = File(dir, "gitcove-${fileFmt.format(Date())}.log")
            file.appendText("[${timeFmt.format(Date())}] $tag: ${message.replace('\n', ' ')}\n")
        }
    }

    /** 读取全部日志（新条目在前），最多 limit 条 */
    fun entries(limit: Int = 500): List<LogEntry> {
        val files = dir.listFiles { f -> f.name.endsWith(".log") }?.sortedByDescending { it.name } ?: return emptyList()
        val result = mutableListOf<LogEntry>()
        for (file in files) {
            val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
            for (line in lines.asReversed()) {
                result.add(parseLine(line))
                if (result.size >= limit) return result
            }
        }
        return result
    }

    private fun parseLine(line: String): LogEntry {
        // 格式：[MM-dd HH:mm:ss] tag: message
        return runCatching {
            val m = Regex("""^\[([^\]]+)]\s+(\S+):\s(.*)$""").find(line)!!
            LogEntry(time = timeFmt.parse(m.groupValues[1])?.time ?: 0L, tag = m.groupValues[2], message = m.groupValues[3])
        }.getOrDefault(LogEntry(0L, "log", line))
    }

    fun clear() {
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }
}
