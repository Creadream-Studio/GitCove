package com.gitcove.app.domain.model

import kotlinx.serialization.Serializable

/** 文件改动状态 */
enum class Status { ADDED, MODIFIED, DELETED, UNTRACKED, CONFLICT }

/**
 * 仓库元数据（文档 4.3 核心数据模型）
 */
@Serializable
data class Repository(
    val id: Long,
    val name: String,
    val path: String,
    val remoteUrl: String? = null,
    val currentBranch: String = "",
    val lastSync: Long? = null,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * 提交记录
 */
data class Commit(
    val hash: String,
    val message: String,
    val author: String,
    val email: String,
    val date: Long,
    val parents: List<String>
) {
    val shortHash: String get() = hash.take(8)
}

/**
 * 文件状态条目
 */
data class FileStatus(
    val path: String,
    val status: Status,
    val staged: Boolean
)

/**
 * 分支
 */
data class Branch(
    val name: String,
    val isRemote: Boolean,
    val isCurrent: Boolean,
    val lastCommitHash: String? = null
)

/** 标签 */
data class Tag(
    val name: String,
    val hash: String? = null
)

/** 差异行类型 */
enum class LineType { ADD, DEL, CONTEXT, HUNK, META }

data class DiffLine(
    val type: LineType,
    val text: String
)

/** 差异条目（列表级） */
data class DiffEntryInfo(
    val path: String,
    val changeType: String,   // ADD / MODIFY / DELETE / RENAME
    val staged: Boolean
)

/** 文件树节点 */
data class FileNode(
    val name: String,
    val path: String,          // 相对仓库根路径
    val isDir: Boolean,
    val status: Status? = null  // Git 状态徽章
)

/** 内容搜索命中 */
data class SearchHit(
    val path: String,
    val line: Int,
    val text: String
)

/**
 * 仓库综合状态（一次 status 调用的聚合结果）
 */
data class RepoStatus(
    val branch: String,
    val detached: Boolean,
    val staged: List<FileStatus> = emptyList(),
    val unstaged: List<FileStatus> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val ahead: Int = 0,
    val behind: Int = 0
) {
    val dirtyCount: Int get() = staged.size + unstaged.size
    val isClean: Boolean get() = dirtyCount == 0 && conflicts.isEmpty()
}

/** 操作日志条目 */
data class LogEntry(
    val time: Long,
    val tag: String,
    val message: String
)

/** 冲突解决动作 */
enum class ConflictAction { OURS, THEIRS }
