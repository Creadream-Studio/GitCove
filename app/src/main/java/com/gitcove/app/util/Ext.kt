package com.gitcove.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 相对时间显示：刚刚 / n 分钟前 / n 小时前 / n 天前 / yyyy-MM-dd */
fun relativeTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val diff = System.currentTimeMillis() - epochMillis
    val sec = diff / 1000
    val min = sec / 60
    val hour = min / 60
    val day = hour / 24
    return when {
        min < 1 -> "刚刚"
        min < 60 -> "$min 分钟前"
        hour < 24 -> "$hour 小时前"
        day < 30 -> "$day 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMillis))
    }
}

/** 全格式日期时间 */
fun fullTime(epochMillis: Long): String =
    if (epochMillis <= 0L) "" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))

/** 状态单字符徽标：A/M/D/U/C */
fun statusLetter(s: com.gitcove.app.domain.model.Status): Char = when (s) {
    com.gitcove.app.domain.model.Status.ADDED -> 'A'
    com.gitcove.app.domain.model.Status.MODIFIED -> 'M'
    com.gitcove.app.domain.model.Status.DELETED -> 'D'
    com.gitcove.app.domain.model.Status.UNTRACKED -> 'U'
    com.gitcove.app.domain.model.Status.CONFLICT -> 'C'
}

/** 判断是否疑似文本文件（可编辑/可预览） */
fun isLikelyTextFile(name: String, size: Long): Boolean {
    if (size > 2 * 1024 * 1024) return false
    val textExts = listOf(
        "kt", "java", "xml", "md", "txt", "json", "yml", "yaml", "gradle", "kts", "py",
        "js", "ts", "css", "html", "htm", "sh", "c", "cpp", "h", "hpp", "go", "rs", "rb",
        "php", "sql", "toml", "ini", "cfg", "conf", "properties", "pro", "csv", "svg",
        "gitignore", "gitattributes", "editorconfig"
    )
    val ext = name.substringAfterLast('.', "").lowercase()
    return name.startsWith(".") || ext.isBlank() || ext in textExts
}
