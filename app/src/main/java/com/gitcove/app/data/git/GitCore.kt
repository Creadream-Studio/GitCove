package com.gitcove.app.data.git

import com.gitcove.app.data.log.OpLog
import com.gitcove.app.data.store.AuthStore
import com.gitcove.app.domain.model.Commit
import com.gitcove.app.domain.model.DiffEntryInfo
import com.gitcove.app.domain.model.DiffLine
import com.gitcove.app.domain.model.FileStatus
import com.gitcove.app.domain.model.LineType
import com.gitcove.app.domain.model.RepoStatus
import com.gitcove.app.domain.model.Status
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.ProgressMonitor
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 合并/拉取冲突异常 */
class GitConflictException(val files: List<String>) :
    Exception("存在 ${files.size} 个未解决冲突：${files.joinToString().take(120)}")

/**
 * Git 内核封装（JGit）。
 * 所有方法返回 Result，IO 操作由调用方放置在 Dispatchers.IO。
 *
 * 路线图：保持本层接口抽象，后期可平滑替换为 libgit2 + JNI（文档 6.3）。
 */
class GitCore(private val auth: AuthStore, private val log: OpLog) {

    // ────────────────────────── 通用辅助 ──────────────────────────

    /** 统一打开仓库执行并包装异常 */
    private fun <T> withRepo(repoDir: File, block: (Git) -> T): Result<T> = try {
        Git.open(repoDir).use { git -> Result.success(block(git)) }
    } catch (e: GitConflictException) {
        Result.failure(e)
    } catch (e: Exception) {
        log.append("git", "操作失败: ${e.message}")
        Result.failure(e)
    }

    /** 进度监控 → 字符串回调（克隆/拉取/推送进度） */
    private fun monitor(cb: (String) -> Unit): ProgressMonitor = object : ProgressMonitor {
        private var title = ""
        private var total = 0
        private var lastPct = -1

        override fun start(totalTasks: Int) {}
        override fun beginTask(taskTitle: String, totalWork: Int) {
            title = taskTitle
            total = totalWork
            lastPct = -1
            cb(if (totalWork <= 0) title else "$title 0%")
        }

        override fun update(done: Int) {
            if (total > 0) {
                val pct = done * 100 / total
                if (pct != lastPct && pct % 5 == 0) {
                    lastPct = pct
                    cb("$title $pct%")
                }
            }
        }

        override fun endTask() {}
        override fun isCancelled(): Boolean = false
    }

    private fun creds(url: String) = GitCredentials.providerFor(url, auth)

    /** 向仓库写入身份配置（clone/init 时调用） */
    private fun setIdentity(git: Git) {
        val cfg = git.repository.config
        if (auth.gitName.isNotBlank()) cfg.setString("user", null, "name", auth.gitName)
        if (auth.gitEmail.isNotBlank()) cfg.setString("user", null, "email", auth.gitEmail)
        cfg.save()
    }

    private fun pathOf(e: DiffEntry): String =
        if (e.changeType == DiffEntry.ChangeType.DELETE) e.oldPath else e.newPath

    /** 校验路径不越出仓库根目录 */
    private fun safeFile(repoDir: File, path: String): File? {
        val root = repoDir.canonicalPath + File.separator
        val f = File(repoDir, path)
        return if (f.canonicalPath.startsWith(root)) f else null
    }

    // ────────────────────────── A. 仓库管理 ──────────────────────────

    /** 功能 1/2：从远程克隆（https / ssh / git） */
    fun clone(
        url: String,
        targetDir: File,
        onProgress: (String) -> Unit = {}
    ): Result<File> = try {
        val cmd = Git.cloneRepository()
            .setURI(url.trim())
            .setDirectory(targetDir)
            .setCloneAllBranches(true)
            .setProgressMonitor(monitor(onProgress))
        creds(url)?.let { cmd.setCredentialsProvider(it) }
        cmd.call().use { git ->
            setIdentity(git)
            log.append("git", "克隆完成: $url")
        }
        Result.success(targetDir)
    } catch (e: Exception) {
        log.append("git", "克隆失败: ${e.message}")
        Result.failure(e)
    }

    /** 功能 3/4：初始化新仓库 */
    fun init(dir: File): Result<File> = try {
        Git.init().setDirectory(dir).setInitialBranch("main").call().use { git ->
            setIdentity(git)
            log.append("git", "初始化仓库: ${dir.name}")
        }
        Result.success(dir)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** 功能 3：导入本地已有目录（校验是否为有效 Git 仓库） */
    fun isValidRepo(dir: File): Boolean = runCatching { Git.open(dir).use { true } }.isSuccess

    // ────────────────────────── 状态查询 ──────────────────────────

    /** 综合状态：分支 / 暂存区 / 工作区 / 冲突 / 领先落后 */
    fun status(repoDir: File): Result<RepoStatus> = withRepo(repoDir) { git ->
        val repo = git.repository
        val detached = repo.repositoryState == RepositoryState.DETACHED_HEAD
        val branch = repo.branch
        val s = git.status().call()

        val staged = buildList {
            s.added.forEach { add(FileStatus(it, Status.ADDED, true)) }
            s.changed.forEach { add(FileStatus(it, Status.MODIFIED, true)) }
            s.removed.forEach { add(FileStatus(it, Status.DELETED, true)) }
        }
        val unstaged = buildList {
            s.modified.forEach { add(FileStatus(it, Status.MODIFIED, false)) }
            s.untracked.forEach { add(FileStatus(it, Status.UNTRACKED, false)) }
            s.missing.forEach { add(FileStatus(it, Status.DELETED, false)) }
        }
        val conflicts = s.conflicting.toList()
        val tracking = runCatching {
            if (!detached && branch.isNotBlank()) BranchTrackingStatus.of(repo, branch) else null
        }.getOrNull()

        RepoStatus(
            branch = branch,
            detached = detached,
            staged = staged,
            unstaged = unstaged,
            conflicts = conflicts,
            ahead = tracking?.aheadCount ?: 0,
            behind = tracking?.behindCount ?: 0
        )
    }

    // ────────────────────────── B. 日常提交 ──────────────────────────

    /** 功能 8：暂存（文件级） */
    fun stage(repoDir: File, paths: List<String>): Result<Unit> = withRepo(repoDir) { git ->
        if (paths.isNotEmpty()) {
            val add = git.add()
            paths.forEach { add.addFilepattern(it) }
            add.call()
        }
    }

    /** 功能 8：取消暂存（保留工作区内容） */
    fun unstage(repoDir: File, paths: List<String>): Result<Unit> = withRepo(repoDir) { git ->
        if (paths.isNotEmpty()) {
            val reset = git.reset()
            paths.forEach { reset.addPattern(it) }
            reset.call()
        }
    }

    /** 还原：未跟踪文件删除，跟踪文件恢复到 HEAD 版本 */
    fun discard(repoDir: File, paths: List<String>): Result<Unit> = withRepo(repoDir) { git ->
        paths.forEach { path ->
            val st = git.status().addPath(path).call()
            if (st.untracked.contains(path)) {
                safeFile(repoDir, path)?.delete()
            } else {
                git.checkout().setStartPoint("HEAD").addPath(path).call()
            }
        }
    }

    /** 功能 10/11/12/13：提交（支持 Amend 修改最近提交） */
    fun commit(repoDir: File, message: String, amend: Boolean = false): Result<String> = withRepo(repoDir) { git ->
        require(message.isNotBlank()) { "提交信息不能为空" }
        val rc = git.commit()
            .setMessage(message.trim())
            .setAmend(amend)
            .call()
        log.append("commit", rc.shortMessage)
        rc.name()
    }

    /** 功能 10：提交信息历史参考（候选词） */
    fun recentMessages(repoDir: File, limit: Int = 15): List<String> = runCatching {
        Git.open(repoDir).use { git ->
            git.log().setMaxCount(60).call()
                .map { it.shortMessage.trim() }
                .distinct()
                .take(limit)
        }
    }.getOrDefault(emptyList())

    /** 全部改动文件（暂存 + 未暂存 + 冲突） */
    fun allChangedPaths(repoDir: File): List<String> {
        val st = status(repoDir).getOrNull() ?: return emptyList()
        return (st.staged.map { it.path } + st.unstaged.map { it.path } + st.conflicts).distinct()
    }

    // ────────────────────────── L. 差异对比 ──────────────────────────

    /**
     * 差异条目列表。
     * cached=false：工作区 vs 暂存区；cached=true：暂存区 vs HEAD。
     * 注意：JGit diff 不含未跟踪文件，未跟踪文件由 UI 基于 status 单独呈现。
     */
    fun diffEntries(repoDir: File, cached: Boolean): Result<List<DiffEntryInfo>> = withRepo(repoDir) { git ->
        val cmd = git.diff().setShowNameAndStatusOnly(true)
        if (cached) cmd.setCached(true)
        cmd.call().map { DiffEntryInfo(pathOf(it), it.changeType.name, cached) }
    }

    /**
     * 单文件差异文本（带增删行着色标记）。
     * 未跟踪文件伪造成全行新增。
     */
    fun diffText(repoDir: File, path: String, cached: Boolean): Result<List<DiffLine>> = withRepo(repoDir) { git ->
        val st = git.status().addPath(path).call()
        if (!cached && st.untracked.contains(path)) {
            val f = safeFile(repoDir, path)
            val lines = if (f != null && f.exists()) f.readLines() else emptyList()
            listOf(DiffLine(LineType.HUNK, "@@ -0,0 +1,${lines.size} @@")) +
                lines.map { DiffLine(LineType.ADD, it) }
        } else {
            val repo = git.repository
            val out = ByteArrayOutputStream()
            val df = DiffFormatter(out).apply {
                setRepository(repo)
                setDetectRenames(true)
            }
            val cmd = git.diff()
            if (cached) cmd.setCached(true)
            val entry = cmd.call().firstOrNull { pathOf(it) == path }
            if (entry == null) emptyList()
            else {
                df.format(entry)
                df.flush()
                parseUnifiedDiff(out.toString("UTF-8"))
            }
        }
    }

    /** 解析 unified diff 文本为带类型的行 */
    private fun parseUnifiedDiff(text: String): List<DiffLine> {
        val result = mutableListOf<DiffLine>()
        var inHunk = false
        for (raw in text.split('\n')) {
            if (!inHunk) {
                when {
                    raw.startsWith("@@") -> { inHunk = true; result.add(DiffLine(LineType.HUNK, raw)) }
                    raw.startsWith("diff ") || raw.startsWith("index ") ||
                        raw.startsWith("--- ") || raw.startsWith("+++ ") ->
                        result.add(DiffLine(LineType.META, raw))
                }
            } else {
                when {
                    raw.startsWith("@@") -> result.add(DiffLine(LineType.HUNK, raw))
                    raw.startsWith("+") -> result.add(DiffLine(LineType.ADD, raw.substring(1)))
                    raw.startsWith("-") -> result.add(DiffLine(LineType.DEL, raw.substring(1)))
                    raw.startsWith("\\") -> result.add(DiffLine(LineType.META, raw))
                    else -> result.add(DiffLine(LineType.CONTEXT, raw.removePrefix(" ")))
                }
            }
        }
        return result
    }

    /** 某次提交的变更文件列表（提交详情用） */
    fun commitDiffEntries(repoDir: File, hash: String): Result<List<DiffEntryInfo>> = withRepo(repoDir) { git ->
        val repo = git.repository
        val walk = RevWalk(repo)
        walk.use { w ->
            val commit = w.parseCommit(repo.resolve(hash) ?: throw IllegalStateException("找不到提交 $hash"))
            if (commit.parentCount == 0) {
                // 初始提交：列出全部文件为新增
                TreeWalk(repo).use { t ->
                    t.addTree(commit.tree)
                    t.isRecursive = true
                    buildList {
                        while (t.next()) add(DiffEntryInfo(t.pathString, "ADD", true))
                    }
                }
            } else {
                val oldTree = w.parseTree(commit.getParent(0))
                val newTree = w.parseTree(commit.tree)
                val out = ByteArrayOutputStream()
                val df = DiffFormatter(out).apply {
                    setRepository(repo)
                    setDetectRenames(true)
                }
                df.scan(oldTree, newTree).map { DiffEntryInfo(pathOf(it), it.changeType.name, true) }
            }
        }
    }

    // ────────────────────────── I. 查看与编辑 ──────────────────────────

    /** 读取仓库内文本文件（编辑器/预览） */
    fun readFile(repoDir: File, path: String): Result<String> = withRepo(repoDir) { _ ->
        val f = safeFile(repoDir, path) ?: throw IllegalArgumentException("非法路径")
        if (f.exists()) f.readText() else ""
    }

    /** 保存仓库内文本文件 */
    fun writeFile(repoDir: File, path: String, content: String): Result<Unit> = withRepo(repoDir) { _ ->
        val f = safeFile(repoDir, path) ?: throw IllegalArgumentException("非法路径")
        f.parentFile?.mkdirs()
        f.writeText(content)
    }

    /** 删除仓库内文件（含目录） */
    fun deleteFile(repoDir: File, path: String): Result<Unit> = withRepo(repoDir) { _ ->
        val f = safeFile(repoDir, path) ?: throw IllegalArgumentException("非法路径")
        f.deleteRecursively()
    }

    /** 创建新文件（可含目录） */
    fun createFile(repoDir: File, path: String, content: String = ""): Result<Unit> = withRepo(repoDir) { _ ->
        val f = safeFile(repoDir, path) ?: throw IllegalArgumentException("非法路径")
        require(!f.exists()) { "文件已存在" }
        f.parentFile?.mkdirs()
        f.writeText(content)
    }

    /** 提交日期格式化 */
    fun formatDate(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
}
