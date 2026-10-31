package com.gitcove.app.util

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * 文件查看页（内容 / 差异 / 历史 三标签）使用的文件类别。
 * 决定内容页用什么方式呈现、差异页能否逐行/双图对比。
 */
enum class FileKind { IMAGE, VIDEO, AUDIO, TEXT, BINARY }

object FileTypes {

    val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "ico", "heic")
    val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v")
    val AUDIO_EXTS = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac", "opus")
    val MD_EXTS = setOf("md", "markdown")
    val HTML_EXTS = setOf("html", "htm")

    fun ext(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    /**
     * 按扩展名初步分类：图片 / 视频 / 音频优先；
     * 其余（含 svg、xml、陌生扩展名、无扩展名）一律按文本处理，
     * 是否为真正的二进制由 [classify] 内容嗅探兜底修正（陌生文件也始终先进代码编辑器）。
     */
    fun kindOf(name: String): FileKind {
        val e = ext(name)
        return when {
            e in IMAGE_EXTS -> FileKind.IMAGE
            e in VIDEO_EXTS -> FileKind.VIDEO
            e in AUDIO_EXTS -> FileKind.AUDIO
            else -> FileKind.TEXT
        }
    }

    /** 二进制嗅探：前 8KB 出现 NUL 字节即按二进制处理 */
    fun sniffBinary(f: File): Boolean {
        if (!f.exists() || f.isDirectory) return false
        return runCatching {
            val buf = ByteArray(8192)
            val n = f.inputStream().use { it.read(buf) }
            if (n <= 0) false else (0 until n).any { buf[it] == 0.toByte() }
        }.getOrDefault(false)
    }

    /** 扩展名 + 内容嗅探的完整分类（内容页 / 差异页共用） */
    fun classify(name: String, file: File?): FileKind {
        val byExt = kindOf(name)
        if (byExt != FileKind.TEXT) return byExt
        // 扩展名可识别为文本、或无扩展名的小文件 → 文本；嗅探到 NUL 则按二进制
        if (file != null && sniffBinary(file)) return FileKind.BINARY
        return FileKind.TEXT
    }

    /** 流式 SHA-1（大文件也不整块载入内存），hex 小写字符串 */
    fun sha1Of(ins: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 工作区文件的 (大小, SHA-1)，与 GitCore.blobStatAt 的结果配对比较判断"有无改变" */
    fun fileStat(f: File?): Pair<Long, String>? {
        if (f == null || !f.isFile) return null
        return runCatching { f.length() to f.inputStream().use { sha1Of(it) } }.getOrNull()
    }
}

/** 人类可读大小（B/KB/MB/GB，一位小数），文件列表与查看页共用 */
fun fileSizeText(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.1f GB", mb / 1024.0)
}
