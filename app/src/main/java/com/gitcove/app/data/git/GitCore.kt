package com.gitcove.app.data.git

import com.gitcove.app.data.ai.AiCommit
import com.gitcove.app.data.log.OpLog
import com.gitcove.app.data.store.AuthStore
import com.gitcove.app.domain.model.Branch
import com.gitcove.app.domain.model.Commit
import com.gitcove.app.domain.model.CommitRef
import com.gitcove.app.domain.model.CommitStat
import com.gitcove.app.domain.model.ConflictAction
import com.gitcove.app.domain.model.DiffEntryInfo
import com.gitcove.app.domain.model.DiffLine
import com.gitcove.app.domain.model.FileStatus
import com.gitcove.app.domain.model.LineType
import com.gitcove.app.domain.model.RepoStatus
import com.gitcove.app.domain.model.ResetMode
import com.gitcove.app.domain.model.Status
import com.gitcove.app.domain.model.Tag
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.CherryPickResult
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.RefDatabase
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
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
 * ⚠️ 边界统一捕获 Throwable 而非 Exception：JGit 内部可能抛出 Error 类型
 * （NoSuchMethodError / NoClassDefFoundError / OutOfMemoryError 等），
 * 若只捕 Exception 会直接穿透到协程导致应用闪退，这里将其转为 Result.failure。
 *
 * 路线图：保持本层接口抽象，后期可平滑替换为 libgit2 + JNI（文档 6.3）。
 */
class GitCore(private val auth: AuthStore, private val log: OpLog) {

    // ────────────────────────── 通用辅助 ──────────────────────────

    /**
     * Repository 实例缓存：Git.open 每次都要重新解析 config / HEAD / 探测仓库布局，
     * 在手机存储上是每次操作几十到几百毫秒的固定开销。打开一次后全局复用
     * （JGit 的 Repository 线程安全，官方支持共享），提交/暂存/状态等热路径全部受益。
     */
    private val repoCache = java.util.concurrent.ConcurrentHashMap<String, Repository>()

    /** 取缓存 Repository（incrementOpen 保住引用计数，永不释放；文件快照机制自动感知外部修改） */
    private fun cachedRepo(dir: File): Repository {
        val key = dir.absolutePath
        return repoCache.getOrPut(key) {
            Git.open(dir).repository.also { it.incrementOpen() }
        }
    }

    /** Git 外观对象：use{} 结束只释放包装层，底层 Repository 留在缓存里复用 */
    private fun gitFor(dir: File): Git = Git.wrap(cachedRepo(dir))

    /** 统一打开仓库执行并包装异常 */
    private fun <T> withRepo(repoDir: File, block: (Git) -> T): Result<T> = try {
        gitFor(repoDir).use { git -> Result.success(block(git)) }
    } catch (e: GitConflictException) {
        Result.failure(e)
    } catch (e: Throwable) {
        log.append("git", "操作失败: ${e.message ?: e.javaClass.simpleName}")
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
        // 注：showDuration(enabled) 为 JGit 6.x 新增接口方法，5.13 无此方法，不能覆写
    }

    private fun creds(url: String) = GitCredentials.providerFor(url, auth)

    /** 向仓库写入身份配置（clone/init 时调用）：使用默认 Git 身份 */
    private fun setIdentity(git: Git) {
        val cfg = git.repository.config
        val identity = auth.defaultIdentity()
        if (identity != null) {
            if (identity.name.isNotBlank()) cfg.setString("user", null, "name", identity.name)
            if (identity.email.isNotBlank()) cfg.setString("user", null, "email", identity.email)
        }
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
    } catch (e: Throwable) {
        log.append("git", "克隆失败: ${e.message ?: e.javaClass.simpleName}")
        Result.failure(e)
    }

    /** 功能 3/4：初始化新仓库 */
    fun init(dir: File): Result<File> = try {
        Git.init().setDirectory(dir).setInitialBranch("main").call().use { git ->
            setIdentity(git)
            log.append("git", "初始化仓库: ${dir.name}")
        }
        Result.success(dir)
    } catch (e: Throwable) {
        Result.failure(e)
    }

    /**
     * 功能 3：判定目录是否为有效 Git 仓库。
     *
     * 覆盖三种形态：
     *   1. 常规仓库 —— .git 为目录；
     *   2. worktree / submodule —— .git 为文件，内容以 "gitdir:" 指向真实仓库；
     *   3. bare 仓库等特殊形态 —— 交给 JGit 判定。
     *
     * 注意：权限不足（Android 11+ 未授予「所有文件访问」）时 listFiles/读取
     * 会静默失败，此时只能依赖 JGit 的最终判定。
     */
    fun isValidRepo(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val dotGit = File(dir, ".git")
        if (dotGit.isDirectory) return true
        if (dotGit.isFile) {
            val isGitdirPointer = runCatching {
                dotGit.readText().trimStart().startsWith("gitdir:", ignoreCase = true)
            }.getOrDefault(false)
            if (isGitdirPointer) return true
        }
        return runCatching { Git.open(dir).use { true } }.isSuccess
    }

    // ────────────────────────── 状态查询 ──────────────────────────

    /** 综合状态：分支 / 暂存区 / 工作区 / 冲突 / 领先落后 */
    fun status(repoDir: File): Result<RepoStatus> = withRepo(repoDir) { git ->
        val repo = git.repository
        // detached HEAD：fullBranch 不是 refs/heads/ 引用
        val detached = repo.fullBranch?.startsWith("refs/heads/") != true
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

    /**
     * 功能 8：暂存（文件级）。
     * 注意：JGit 的 add() 对工作区已删除的文件不会暂存删除（实测 5.13 静默忽略），
     * 必须按「存在→add / 不存在→rm --cached」分流，删除才能进入暂存区。
     */
    fun stage(repoDir: File, paths: List<String>): Result<Unit> = withRepo(repoDir) { git ->
        if (paths.isNotEmpty()) {
            val add = git.add()
            val rm = git.rm().setCached(true)
            val inIndex = runCatching {
                val dc = git.repository.readDirCache()
                buildSet {
                    for (i in 0 until dc.entryCount) add(dc.getEntry(i).pathString)
                }
            }.getOrDefault(emptySet())
            var hasAdd = false
            var hasRm = false
            paths.forEach { p ->
                when {
                    safeFile(repoDir, p)?.exists() == true -> { add.addFilepattern(p); hasAdd = true }
                    p in inIndex -> { rm.addFilepattern(p); hasRm = true }
                    // 既不存在也不在索引：陈旧路径，静默跳过
                }
            }
            if (hasAdd) add.call()
            if (hasRm) rm.call()
        }
    }

    /** 功能 8：取消暂存（保留工作区内容） */
    fun unstage(repoDir: File, paths: List<String>): Result<Unit> = withRepo(repoDir) { git ->
        if (paths.isNotEmpty()) {
            val reset = git.reset()
            paths.forEach { reset.addPath(it) }
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

    /**
     * 功能 10/11/12/13：提交（支持 Amend 修改最近提交）。
     * [identityId] 指定本次提交身份（提交区选择器）；null = 当前默认身份。
     * 找不到对应身份（已删除等）时回退默认身份。
     */
    fun commit(
        repoDir: File,
        message: String,
        amend: Boolean = false,
        identityId: Long? = null
    ): Result<String> = withRepo(repoDir) { git ->
        require(message.isNotBlank()) { "提交信息不能为空" }
        val cmd = git.commit()
            .setMessage(message.trim())
            .setAmend(amend)
        resolveIdentity(identityId)?.let { id ->
            if (id.name.isNotBlank()) {
                cmd.setAuthor(id.name, id.email)
                cmd.setCommitter(id.name, id.email)
            }
        }
        val rc = cmd.call()
        log.append("commit", rc.shortMessage)
        rc.name()
    }

    /** 身份解析：按 id 精确取，取不到回退默认身份（含首条兜底） */
    private fun resolveIdentity(identityId: Long?): com.gitcove.app.data.store.GitIdentity? {
        if (identityId == null) return auth.defaultIdentity()
        return auth.identities().firstOrNull { it.id == identityId }
            ?: auth.defaultIdentity()
    }

    /** 功能 10：提交信息历史参考（候选词） */
    fun recentMessages(repoDir: File, limit: Int = 15): List<String> = runCatching {
        gitFor(repoDir).use { git ->
            git.log().setMaxCount(60).call()
                .map { it.shortMessage.trim() }
                .distinct()
                .take(limit)
        }
    }.getOrDefault(emptyList())

    /**
     * 挂起的合并提交信息（.git/MERGE_MSG）：合并产生冲突时 Git 写入
     * （如「Merge branch 'x' into y」），冲突全部解决后的提交应默认使用它。
     * 过滤注释行（# Conflicts: …）与首尾空行；无合并状态/空白时返回 null。
     */
    fun pendingMergeMessage(repoDir: File): String? = runCatching {
        gitFor(repoDir).use { git ->
            // JGit readMergeCommitMsg() 直接返回 String?（无合并状态时 null）
            git.repository.readMergeCommitMsg()
                ?.lineSequence()
                ?.filterNot { it.startsWith("#") }
                ?.joinToString("\n")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

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
                setPathFilter(PathFilter.create(path))
            }
            // ⚠️ 不能用 git.diff() + format()：JGit 5.13 对工作区侧条目会按对象库读取
            // 新内容哈希，而工作区内容并不在对象库中，必然抛 MissingObjectException，
            // 表现为"有改动但差异为空"。必须用 scan(旧树, 新树) 建立正确的两侧内容源。
            val oldTree: AbstractTreeIterator
            val newTree: AbstractTreeIterator
            if (cached) {
                oldTree = headTreeIterator(repo) ?: EmptyTreeIterator()
                newTree = DirCacheIterator(repo.readDirCache())
            } else {
                oldTree = DirCacheIterator(repo.readDirCache())
                newTree = FileTreeIterator(repo)
            }
            val entries = df.scan(oldTree, newTree)
            if (entries.isEmpty()) emptyList()
            else {
                df.format(entries)
                df.flush()
                parseUnifiedDiff(out.toString("UTF-8"))
            }
        }
    }

    /** HEAD 的树迭代器；仓库尚无提交时返回 null */
    private fun headTreeIterator(repo: Repository): AbstractTreeIterator? {
        val head = repo.resolve("HEAD^{tree}") ?: return null
        return CanonicalTreeParser().apply { reset(repo.newObjectReader(), head) }
    }

    /**
     * 功能 4.6：AI 生成提交信息取材 —— 暂存区 diff 文本（Index vs HEAD），
     * 暂存区无改动时回退工作区（Worktree vs HEAD，未暂存修改也可见）。
     * 返回 (diff 文本[超长截断], 变更文件路径列表)；两者皆空 = 无任何改动。
     *
     * ⚠️ 树迭代器是有状态游标，首次 scan 会将其耗尽：回退分支必须重建
     * head 迭代器，否则旧侧等效空树，所有文件都显示为 new file
     * （JVM 语义验证：scripts/aicheck）。
     */
    fun aiDiffText(repoDir: File): Result<Pair<String, List<String>>> = withRepo(repoDir) { git ->
        val repo = git.repository
        val out = ByteArrayOutputStream()
        DiffFormatter(out).use { df ->
            df.setRepository(repo)
            df.setDetectRenames(true)
            // 第一优先：暂存区（Index vs HEAD）
            var entries = df.scan(headTreeIterator(repo) ?: EmptyTreeIterator(), DirCacheIterator(repo.readDirCache()))
            // 回退：工作区（Worktree vs HEAD）——head 迭代器重建，不可复用
            if (entries.isEmpty())
                entries = df.scan(headTreeIterator(repo) ?: EmptyTreeIterator(), FileTreeIterator(repo))
            val files = entries.map { pathOf(it) }
            if (files.isEmpty()) return@withRepo "" to emptyList()
            df.format(entries)
            df.flush()
            val text = out.toString("UTF-8")
            val capped = if (text.length > AiCommit.MAX_DIFF_CHARS)
                text.take(AiCommit.MAX_DIFF_CHARS) + "\n…（diff 过长已截断）"
            else text
            capped to files
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

    /** 图谱行统计：单提交变更文件数与增删行数（Working Copy 4.5 参考） */
    fun commitStats(repoDir: File, hash: String): CommitStat? = runCatching {
        withRepo(repoDir) { git ->
            val repo = git.repository
            RevWalk(repo).use { w ->
                val commit = w.parseCommit(repo.resolve(hash) ?: throw IllegalStateException("找不到提交 $hash"))
                val out = ByteArrayOutputStream()
                DiffFormatter(out).use { df ->
                    df.setRepository(repo)
                    var ins = 0
                    var del = 0
                    val entries = if (commit.parentCount == 0) {
                        // 初始提交：与空树比较，全部视为新增
                        df.scan(
                            EmptyTreeIterator(),
                            CanonicalTreeParser().apply { reset(repo.newObjectReader(), commit.tree) }
                        )
                    } else {
                        df.scan(w.parseTree(commit.getParent(0)), w.parseTree(commit.tree))
                    }
                    var files = 0
                    for (e in entries) {
                        files++
                        df.toFileHeader(e).toEditList().forEach { edit ->
                            del += edit.endA - edit.beginA
                            ins += edit.endB - edit.beginB
                        }
                    }
                    CommitStat(files, ins, del)
                }
            }
        }.getOrThrow()
    }.getOrNull()

    /** 分享补丁：整次提交的 unified diff 文本（图谱长按 → 分享） */
    fun commitPatch(repoDir: File, hash: String): String? = runCatching {
        withRepo(repoDir) { git ->
            val repo = git.repository
            RevWalk(repo).use { w ->
                val commit = w.parseCommit(repo.resolve(hash) ?: throw IllegalStateException("找不到提交 $hash"))
                val out = ByteArrayOutputStream()
                DiffFormatter(out).use { df ->
                    df.setRepository(repo)
                    df.setDetectRenames(true)
                    if (commit.parentCount == 0) {
                        df.format(EmptyTreeIterator(), CanonicalTreeParser().apply {
                            reset(repo.newObjectReader(), commit.tree)
                        })
                    } else {
                        df.format(w.parseTree(commit.getParent(0)), w.parseTree(commit.tree))
                    }
                    out.toString("UTF-8")
                }
            }
        }.getOrThrow()
    }.getOrNull()

    // ────────────────────────── 文件查看页：任意修订的内容读取 ──────────────────────────

    /**
     * 读取指定修订（提交哈希 / 分支名 / HEAD 等）中某文件的字节内容。
     * 该版本不存在此文件（新增前 / 删除后）返回 null。
     */
    fun readBlobAt(repoDir: File, rev: String, path: String): Result<ByteArray?> = withRepo(repoDir) { git ->
        val repo = git.repository
        val id = repo.resolve(rev) ?: throw IllegalStateException("找不到修订 $rev")
        RevWalk(repo).use { walk ->
            val commit = walk.parseCommit(id)
            TreeWalk.forPath(repo, path, commit.tree)?.use { tw ->
                repo.open(tw.getObjectId(0)).openStream().use { it.readBytes() }
            } // forPath 为 null：该版本没有此文件
        }
    }

    /**
     * 从暂存区（index）读取指定 stage 的 blob：stage 1 = base（共同祖先）、
     * stage 2 = ours（当前分支）、stage 3 = theirs（对方分支）。
     *
     * 合并冲突时 index 会保留三阶段信息，普通 `readBlobAt("HEAD")` 只能看到当前版本，
     * 拿不到对方版本，所以对冲突文件要用 Stage 读两侧的正确内容（二进制/图片适用）。
     */
    fun readBlobAtStage(repoDir: File, path: String, stage: Int): Result<ByteArray?> = withRepo(repoDir) { git ->
        val repo = git.repository
        val cache = repo.readDirCache()
        (0 until cache.entryCount)
            .asSequence()
            .map { cache.getEntry(it) }
            .firstOrNull { it.pathString == path && it.stage == stage }
            ?.let { repo.open(it.objectId).bytes }
    }

    /** 指定修订中某文件的 (大小, 流式 SHA-1)；不整块载入内存，用于大文件"有无改变"判断。不存在返回 null */
    fun blobStatAt(repoDir: File, rev: String, path: String): Result<Pair<Long, String>?> = withRepo(repoDir) { git ->
        val repo = git.repository
        val id = repo.resolve(rev) ?: throw IllegalStateException("找不到修订 $rev")
        RevWalk(repo).use { walk ->
            val commit = walk.parseCommit(id)
            TreeWalk.forPath(repo, path, commit.tree)?.use { tw ->
                val loader = repo.open(tw.getObjectId(0))
                val sha = com.gitcove.app.util.FileTypes.sha1Of(loader.openStream())
                loader.size to sha
            }
        }
    }

    /**
     * 某次提交对单个文件的变更：changeType（ADD / MODIFY / DELETE / RENAME，未改动为 null）
     * + 与其第一父提交对比的逐行差异（初始提交全为新增）。
     */
    fun commitFileDiff(repoDir: File, hash: String, path: String): Result<Pair<String?, List<DiffLine>>> =
        withRepo(repoDir) { git ->
            val repo = git.repository
            val id = repo.resolve(hash) ?: throw IllegalStateException("找不到提交 $hash")
            RevWalk(repo).use { walk ->
                val commit = walk.parseCommit(id)
                val reader = repo.newObjectReader()
                val newTree = CanonicalTreeParser().apply { reset(reader, commit.tree) }
                val oldTree: AbstractTreeIterator = if (commit.parentCount == 0) EmptyTreeIterator()
                else CanonicalTreeParser().apply { reset(reader, walk.parseTree(commit.getParent(0))) }
                val out = ByteArrayOutputStream()
                val df = DiffFormatter(out).apply {
                    setRepository(repo)
                    setDetectRenames(false)
                    setPathFilter(PathFilter.create(path))
                }
                val entries = df.scan(oldTree, newTree)
                if (entries.isEmpty()) null to emptyList()
                else {
                    df.format(entries)
                    df.flush()
                    entries.first().changeType.name to parseUnifiedDiff(out.toString("UTF-8"))
                }
            }
        }

    // ────────────────────────── I. 查看与编辑 ──────────────────────────

    /**
     * 以下文件操作不依赖 Git 仓库（普通目录同样可用），
     * 因此不走 withRepo（其内部 Git.open 对非 Git 目录会失败）。
     */

    /** 读取仓库内文本文件（编辑器/预览） */
    fun readFile(repoDir: File, path: String): Result<String> = try {
        val f = safeFile(repoDir, path) ?: throw IllegalArgumentException("非法路径")
        Result.success(if (f.exists()) f.readText() else "")
    } catch (e: Throwable) {
        Result.failure(e)
    }

    /** 保存仓库内文本文件 */
    fun writeFile(repoDir: File, path: String, content: String): Result<Unit> = try {
        val f = safeFile(repoDir, path) ?: throw IllegalArgumentException("非法路径")
        f.parentFile?.mkdirs()
        f.writeText(content)
        Result.success(Unit)
    } catch (e: Throwable) {
        Result.failure(e)
    }

    /** 删除仓库内文件（含目录） */
    fun deleteFile(repoDir: File, path: String): Result<Unit> = try {
        val f = safeFile(repoDir, path) ?: throw IllegalArgumentException("非法路径")
        f.deleteRecursively()
        Result.success(Unit)
    } catch (e: Throwable) {
        Result.failure(e)
    }

    /** 创建新文件（仅当前层级，不通过 / 自动创建多级目录） */
    fun createFile(repoDir: File, path: String, content: String = ""): Result<Unit> = try {
        val f = safeFile(repoDir, path) ?: throw IllegalArgumentException("非法路径")
        require(!f.exists()) { "文件已存在" }
        // 不递归创建父目录：上级目录必须已存在（需多级目录请逐层创建）
        require(f.parentFile?.isDirectory == true) { "上级目录不存在（不支持通过 / 创建多级目录）" }
        f.writeText(content)
        Result.success(Unit)
    } catch (e: Throwable) {
        Result.failure(e)
    }

    /** 创建新目录（仅单级，不递归创建多级） */
    fun createDirectory(repoDir: File, path: String): Result<Unit> = try {
        val f = safeFile(repoDir, path) ?: throw IllegalArgumentException("非法路径")
        require(!f.exists()) { "目录已存在" }
        // mkdir()：父目录缺失时不会递归创建（mkdirs() 才会），保证一次只建一层
        require(f.mkdir()) { "目录创建失败（上级目录可能不存在，不支持通过 / 创建多级目录）" }
        require(f.isDirectory) { "目录创建失败（权限不足或路径非法）" }
        Result.success(Unit)
    } catch (e: Throwable) {
        Result.failure(e)
    }

    /** 重命名 / 移动（同仓库内，文件或目录） */
    fun renameEntry(repoDir: File, oldPath: String, newPath: String): Result<Unit> = try {
        val src = safeFile(repoDir, oldPath) ?: throw IllegalArgumentException("非法路径")
        require(src.exists()) { "原文件不存在" }
        val dst = safeFile(repoDir, newPath) ?: throw IllegalArgumentException("非法路径")
        require(!dst.exists()) { "目标名称已存在" }
        require(src.renameTo(dst)) { "重命名失败" }
        Result.success(Unit)
    } catch (e: Throwable) {
        Result.failure(e)
    }

    /**
     * 递归复制多个条目到目标目录（MP 管理器式粘贴）。
     * 目标重名时自动改名为「名称 (1)」「名称 (2)」…，绝不覆盖已有文件。
     * 返回成功复制的条目数。
     */
    fun copyEntries(repoDir: File, paths: List<String>, targetDirRel: String): Result<Int> = try {
        val targetBase = dirOrThrow(repoDir, targetDirRel)
        var copied = 0
        for (p in paths) {
            val src = safeFile(repoDir, p) ?: throw IllegalArgumentException("非法路径")
            require(src.exists()) { "文件不存在：$p" }
            require(!targetBase.canonicalPath.startsWith(src.canonicalPath + File.separator) &&
                targetBase.canonicalPath != src.canonicalPath
            ) { "不能把文件夹粘贴到其自身内部" }
            copyRecursive(src, uniqueChild(targetBase, src.name))
            copied++
        }
        Result.success(copied)
    } catch (e: Throwable) {
        Result.failure(e)
    }

    /**
     * 移动（剪切粘贴）多个条目到目标目录，规则同 [copyEntries]：
     * 重名自动改名，禁止把文件夹移进其自身内部。返回成功移动的条目数。
     */
    fun moveEntries(repoDir: File, paths: List<String>, targetDirRel: String): Result<Int> = try {
        val targetBase = dirOrThrow(repoDir, targetDirRel)
        var moved = 0
        for (p in paths) {
            val src = safeFile(repoDir, p) ?: throw IllegalArgumentException("非法路径")
            require(src.exists()) { "文件不存在：$p" }
            require(!targetBase.canonicalPath.startsWith(src.canonicalPath + File.separator) &&
                targetBase.canonicalPath != src.canonicalPath
            ) { "不能把文件夹移动到其自身内部" }
            val dst = uniqueChild(targetBase, src.name)
            // renameTo 同仓库目录内必在同一存储卷；万一失败退化为复制+删除
            if (!src.renameTo(dst)) {
                copyRecursive(src, dst)
                src.deleteRecursively()
            }
            moved++
        }
        Result.success(moved)
    } catch (e: Throwable) {
        Result.failure(e)
    }

    private fun dirOrThrow(repoDir: File, rel: String): File {
        val d = if (rel.isBlank()) repoDir else safeFile(repoDir, rel) ?: throw IllegalArgumentException("非法目标目录")
        require(d.isDirectory) { "目标不是文件夹" }
        return d
    }

    /** 目标目录内的唯一名称：重名自动追加 (1)、(2)…（保留扩展名） */
    private fun uniqueChild(dir: File, name: String): File {
        if (!File(dir, name).exists()) return File(dir, name)
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (File(dir, "$base ($n)$ext").exists()) n++
        return File(dir, "$base ($n)$ext")
    }

    private fun copyRecursive(src: File, dst: File) {
        if (src.isDirectory) {
            require(dst.mkdirs() || dst.isDirectory) { "无法创建目录：${dst.name}" }
            src.listFiles()?.forEach { copyRecursive(it, File(dst, it.name)) }
        } else {
            src.copyTo(dst, overwrite = false)
        }
    }

    // ────────────────────────── C. 同步 ──────────────────────────

    /** 远端 origin URL */
    fun remoteUrl(repoDir: File): String? = runCatching {
        gitFor(repoDir).use { git ->
            git.repository.config.getString("remote", "origin", "url")
        }
    }.getOrNull()

    /** 功能 15/18：Fetch */
    fun fetch(repoDir: File, onProgress: (String) -> Unit = {}): Result<Unit> = try {
        gitFor(repoDir).use { git ->
            val url = git.repository.config.getString("remote", "origin", "url")
                // 与 pull/push 一致明确报错：静默返回会提示「完成」，用户误以为拉取成功
                ?: throw IllegalStateException("未配置远端 origin")
            val cmd = git.fetch().setProgressMonitor(monitor(onProgress))
            creds(url)?.let { cmd.setCredentialsProvider(it) }
            cmd.call()
        }
        log.append("sync", "fetch 完成: ${repoDir.name}")
        Result.success(Unit)
    } catch (e: Throwable) {
        log.append("sync", "fetch 失败: ${e.message ?: e.javaClass.simpleName}")
        Result.failure(e)
    }

    /** 功能 17/22：Pull（冲突时抛 GitConflictException） */
    fun pull(repoDir: File): Result<Int> = try {
        val pulled = gitFor(repoDir).use { git ->
            val url = git.repository.config.getString("remote", "origin", "url")
                ?: throw IllegalStateException("未配置远端 origin")
            val cmd = git.pull()
            creds(url)?.let { cmd.setCredentialsProvider(it) }
            val result = cmd.call()
            val conflicts = result.mergeResult?.conflicts?.keys?.toList()
            if (!conflicts.isNullOrEmpty()) throw GitConflictException(conflicts)
            result.fetchResult?.trackingRefUpdates?.size ?: 0
        }
        log.append("sync", "pull 完成: ${repoDir.name}")
        Result.success(pulled)
    } catch (e: GitConflictException) {
        log.append("sync", "pull 冲突: ${e.files}")
        Result.failure(e)
    } catch (e: Throwable) {
        log.append("sync", "pull 失败: ${e.message ?: e.javaClass.simpleName}")
        Result.failure(e)
    }

    /** 功能 11/27：Push（自动补齐 upstream；force 支持强制推送） */
    fun push(repoDir: File, force: Boolean = false, onProgress: (String) -> Unit = {}): Result<Unit> = try {
        gitFor(repoDir).use { git ->
            val repo = git.repository
            val url = repo.config.getString("remote", "origin", "url")
                ?: throw IllegalStateException("未配置远端 origin")
            val branch = repo.branch
            val cmd = git.push()
                .setRemote("origin")
                .setProgressMonitor(monitor(onProgress))
                .add("refs/heads/$branch:refs/heads/$branch")
            if (force) cmd.setForce(true)
            creds(url)?.let { cmd.setCredentialsProvider(it) }
            val results = cmd.call()
            val rejected = results.flatMap { it.remoteUpdates }
                .any { it.status != RemoteRefUpdate.Status.OK && it.status != RemoteRefUpdate.Status.UP_TO_DATE }
            if (rejected) throw IllegalStateException("推送被拒绝（远端有新提交，先拉取或使用强制推送）")
            // 首次推送后补齐 upstream 追踪
            val cfg = repo.config
            if (cfg.getString("branch", branch, "merge") == null) {
                cfg.setString("branch", branch, "remote", "origin")
                cfg.setString("branch", branch, "merge", "refs/heads/$branch")
                cfg.save()
            }
        }
        log.append("sync", "push 完成${if (force) "（强制）" else ""}: ${repoDir.name}")
        Result.success(Unit)
    } catch (e: Throwable) {
        log.append("sync", "push 失败: ${e.message ?: e.javaClass.simpleName}")
        Result.failure(e)
    }

    /** 功能 5：关联外部远端 */
    fun addRemote(repoDir: File, name: String, url: String): Result<Unit> = withRepo(repoDir) { git ->
        git.remoteAdd().setName(name).setUri(URIish(url.trim())).call()
    }

    fun removeRemote(repoDir: File, name: String): Result<Unit> = withRepo(repoDir) { git ->
        git.remoteRemove().setRemoteName(name).call()
    }

    // ────────────────────────── D. 分支 ──────────────────────────

    /** 功能 23/29：分支列表（本地 + 远程） */
    fun branches(repoDir: File): Result<List<Branch>> = withRepo(repoDir) { git ->
        val repo = git.repository
        val current = repo.branch
        val locals = git.branchList().call().map { ref ->
            val name = Repository.shortenRefName(ref.name)
            Branch(name, false, name == current, ref.objectId?.abbreviate(8)?.name())
        }
        val remotes = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()
            .map { ref -> Repository.shortenRefName(ref.name) }
            .filter { it != "origin/HEAD" }
            .map { name -> Branch(name, true, false, refHash(repo, "refs/remotes/$name")) }
        locals + remotes
    }

    private fun refHash(repo: Repository, refName: String): String? =
        runCatching { repo.resolve(refName)?.abbreviate(8)?.name() }.getOrNull()

    /** 功能 24：新建分支 */
    fun createBranch(repoDir: File, name: String, fromRef: String? = null, checkout: Boolean = false): Result<Unit> =
        withRepo(repoDir) { git ->
            val cmd = git.branchCreate().setName(name.trim())
            if (!fromRef.isNullOrBlank()) cmd.setStartPoint(fromRef.trim())
            cmd.call()
            if (checkout) git.checkout().setName(name.trim()).call()
        }

    /** 功能 23/30：切换分支（远程分支自动创建跟踪分支；支持 detached HEAD） */
    fun checkout(repoDir: File, name: String): Result<Unit> = withRepo(repoDir) { git ->
        val remoteName = "origin/$name"
        val existsRemote = git.repository.resolve(remoteName) != null
        val existsLocal = git.repository.resolve("refs/heads/$name") != null
        when {
            existsLocal -> git.checkout().setName(name).call()
            existsRemote -> git.checkout()
                .setCreateBranch(true)
                .setName(name)
                .setStartPoint(remoteName)
                .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                .call()
            else -> throw IllegalStateException("分支不存在: $name")
        }
    }

    /** 功能 30：签出旧版本（detached HEAD） */
    fun checkoutDetached(repoDir: File, hash: String): Result<Unit> = withRepo(repoDir) { git ->
        git.checkout().setName(hash.trim()).call()
    }

    /** 功能 25：删除分支 */
    fun deleteBranch(repoDir: File, name: String): Result<Unit> = withRepo(repoDir) { git ->
        git.branchDelete().setBranchNames(name).setForce(true).call()
    }

    /** 功能 26：Merge 指定分支到当前分支 */
    fun merge(repoDir: File, ref: String): Result<String> = withRepo(repoDir) { git ->
        val id = git.repository.resolve(ref.trim())
            ?: throw IllegalStateException("找不到引用: $ref")
        val result = git.merge().include(id).call()
        when {
            result.conflicts != null && result.conflicts.isNotEmpty() ->
                throw GitConflictException(result.conflicts.keys.toList())
            result.mergeStatus == MergeResult.MergeStatus.ALREADY_UP_TO_DATE -> "已是最新，无需合并"
            else -> "合并完成：${result.mergeStatus.name}"
        }
    }

    // ────────────────────────── E. 历史与回滚 ──────────────────────────

    /** 功能 29：提交历史（仓库 / 文件级） */
    fun log(repoDir: File, path: String? = null, max: Int = 200): Result<List<Commit>> = withRepo(repoDir) { git ->
        try {
            var cmd = git.log().setMaxCount(max)
            if (!path.isNullOrBlank()) cmd = cmd.addPath(path)
            cmd.call().map { rc -> toCommit(rc) }
        } catch (e: Exception) {
            // 空仓库（无任何提交）
            emptyList()
        }
    }

    /**
     * 功能 4.5：提交图谱数据 —— 提交列表 + 指向各提交的分支/标签/HEAD 徽章。
     * 泳道连线由 CommitGraph.build 在内存中计算。
     */
    fun logGraph(repoDir: File, max: Int = 200): Result<List<Commit>> = withRepo(repoDir) { git ->
        val repo = git.repository
        val commits = try {
            git.log().setMaxCount(max).call().map { rc -> toCommit(rc) }
        } catch (e: Exception) {
            emptyList() // 空仓库
        }

        val refsByHash = HashMap<String, MutableList<CommitRef>>()
        runCatching {
            for (ref in repo.refDatabase.getRefsByPrefix(RefDatabase.ALL)) {
                val peeled = repo.refDatabase.peel(ref)
                val target = peeled.peeledObjectId ?: peeled.objectId ?: continue
                refsByHash.getOrPut(target.name()) { mutableListOf() }.add(
                    CommitRef(
                        name = Repository.shortenRefName(ref.name),
                        isHead = ref.name == repo.fullBranch,
                        isTag = ref.name.startsWith("refs/tags/")
                    )
                )
            }
            // detached HEAD：refs 列表不含 HEAD，单独补一个徽章
            val head = repo.refDatabase.exactRef("HEAD")
            if (head != null && !head.isSymbolic) {
                head.objectId?.name()?.let { hash ->
                    refsByHash.getOrPut(hash) { mutableListOf() }.add(CommitRef("HEAD", isHead = true))
                }
            }
        }

        commits.map { c ->
            val refs = refsByHash[c.hash].orEmpty()
                .sortedWith(compareBy({ it.isTag }, { !it.isHead }, { it.name }))
            c.copy(refs = refs)
        }
    }

    private fun toCommit(rc: RevCommit) = Commit(
        hash = rc.name(),
        message = rc.shortMessage,
        author = rc.authorIdent.name,
        email = rc.authorIdent.emailAddress,
        date = rc.commitTime * 1000L,
        parents = rc.parents.map { it.name },
        fullMessage = rc.fullMessage
    )

    /** 功能 31：Revert 撤销提交（生成反向提交） */
    fun revert(repoDir: File, hash: String): Result<String> = withRepo(repoDir) { git ->
        val id = git.repository.resolve(hash.trim())
            ?: throw IllegalStateException("找不到提交 $hash")
        val result = git.revert().include(id).call()
        log.append("git", "revert ${hash.take(8)}")
        result.name()
    }

    /**
     * 功能 4.4：Cherry-pick 择优选取 —— 把指定提交的变更作为新提交应用到当前分支顶端。
     * 冲突时与 merge/revert 一致抛 [GitConflictException]，改动页会列出冲突文件，
     * 解决并手动提交后完成选取。
     */
    fun cherryPick(repoDir: File, hash: String): Result<String> = withRepo(repoDir) { git ->
        val id = git.repository.resolve(hash.trim())
            ?: throw IllegalStateException("找不到提交 $hash")
        setIdentity(git)
        val result = git.cherryPick().include(id).call()
        when (result.status) {
            // JGit 5.13 的 CherryPickResult 不携带冲突列表：从未合并索引读取
            CherryPickResult.CherryPickStatus.CONFLICTING ->
                throw GitConflictException(git.status().call().conflicting.toList())
            CherryPickResult.CherryPickStatus.FAILED ->
                throw IllegalStateException("择优选取失败（工作区可能有未提交改动）")
            else -> {
                log.append("git", "cherry-pick ${hash.take(8)}")
                // newHead 为 null = 变更已全部存在（空提交），视为成功
                result.newHead?.name() ?: hash
            }
        }
    }

    /**
     * 功能 4.4：重置当前分支到任意提交（软/混合/硬）。
     * 软重置到父提交 = 撤销最后一次提交（改动回到暂存区），是改消息 / 拆提交 /
     * squash 的工作流地基（对标 Working Copy Undo Commit / Soft Reset）。
     */
    fun resetTo(repoDir: File, hash: String, mode: ResetMode): Result<Unit> = withRepo(repoDir) { git ->
        val id = git.repository.resolve(hash.trim())
            ?: throw IllegalStateException("找不到提交 $hash")
        git.reset().setRef(id.name()).setMode(ResetCommand.ResetType.valueOf(mode.name)).call()
        log.append("git", "reset ${mode.name.lowercase()} → ${hash.take(8)}")
    }

    /**
     * 功能 4.4：删除提交 —— 在分离 HEAD 上把该提交之后的提交逐个 cherry-pick 到其父提交上，
     * 全部成功才把分支指针移过去（事务性：任一步冲突即整体放弃还原，分支不动）。
     * 仅支持当前分支线性历史上的普通提交（含 HEAD 提交本身）。
     */
    fun dropCommit(repoDir: File, hash: String): Result<String> = withRepo(repoDir) { git ->
        setIdentity(git)
        val (parent, after) = linearRange(git, hash)
        val branchFull = git.repository.fullBranch
        val newHead = replayLinear(git, branchFull, git.repository.resolve(Constants.HEAD)!!, parent.id) {
            for (c in after) pickOrThrow(git, c.id)
        }
        log.append("git", "drop ${hash.take(8)} · 重放 ${after.size} 个提交")
        newHead.name()
    }

    /** 单步 cherry-pick：OK 继续（newHead 为 null = 空变更，视为成功），冲突抛 [GitConflictException] */
    private fun pickOrThrow(git: Git, id: ObjectId) {
        val result = git.cherryPick().include(id).call()
        when (result.status) {
            CherryPickResult.CherryPickStatus.OK -> Unit
            CherryPickResult.CherryPickStatus.CONFLICTING ->
                throw GitConflictException(git.status().call().conflicting.toList())
            else -> throw IllegalStateException("重放失败：${result.status}")
        }
    }

    /**
     * 校验 target 在当前分支 HEAD 的第一父链上、非合并/根提交；
     * 返回（父提交, 待重放提交序列(旧→新)）。区间含合并提交时拒绝重放。
     */
    private fun linearRange(git: Git, hash: String): Pair<RevCommit, List<RevCommit>> {
        val repo = git.repository
        val fullBranch = repo.fullBranch
        if (fullBranch == null || !fullBranch.startsWith("refs/heads/"))
            throw IllegalStateException("分离头指针状态下不能重写历史")
        val headId = repo.resolve(Constants.HEAD) ?: throw IllegalStateException("空仓库")
        val targetId = repo.resolve(hash.trim()) ?: throw IllegalStateException("找不到提交 $hash")
        RevWalk(repo).use { walk ->
            val target = walk.parseCommit(targetId)
            val head = walk.parseCommit(headId)
            if (!walk.isMergedInto(target, head))
                throw IllegalStateException("只能操作当前分支历史上的提交")
            if (target.parentCount != 1)
                throw IllegalStateException("不能删除或改写合并提交与根提交")
            val after = ArrayList<RevCommit>()
            var cur: RevCommit = head
            while (cur.id != targetId) {
                after.add(cur)
                if (cur.parentCount == 0)
                    throw IllegalStateException("只能操作当前分支历史上的提交")
                cur = walk.parseCommit(cur.getParent(0))
            }
            after.reverse()
            if (after.any { it.parentCount > 1 })
                throw IllegalStateException("该提交之后存在合并提交，无法安全重放")
            return walk.parseCommit(target.getParent(0)) to after
        }
    }

    /**
     * 线性区间重放地基（删除提交 / 改写消息共用）：
     * 分离 HEAD 到 base → 逐个 cherry-pick → 全部成功才把分支指针移到新 HEAD 并切回。
     * 任一步失败（冲突）：清 cherry-pick 状态 → 硬重置回原提交 → 切回原分支，
     * 分支引用从未被移动（事务性回滚；force checkout 对未合并索引条目会抛
     * CheckoutConflictException，回滚必须走 reset --hard）。
     */
    private fun replayLinear(
        git: Git,
        branchFull: String,
        originalHead: ObjectId,
        baseId: ObjectId,
        step: () -> Unit
    ): ObjectId {
        val repo = git.repository
        git.checkout().setName(baseId.name()).setForce(true).call()
        try {
            step()
            val newHead = repo.resolve(Constants.HEAD)
                ?: throw IllegalStateException("重放失败：HEAD 丢失")
            val ru = repo.updateRef(branchFull)
            ru.setNewObjectId(newHead)
            ru.setForceUpdate(true)
            val r = ru.update()
            if (r != RefUpdate.Result.NEW && r != RefUpdate.Result.FORCED)
                throw IllegalStateException("分支更新失败：$r")
            git.checkout().setName(branchFull).setForce(true).call()
            return newHead
        } catch (t: Throwable) {
            runCatching { repo.writeCherryPickHead(null as ObjectId?) }
            runCatching { git.reset().setRef(originalHead.name()).setMode(ResetCommand.ResetType.HARD).call() }
            runCatching { git.checkout().setName(branchFull).setForce(true).call() }
            throw t
        }
    }

    /**
     * 功能 4.4：改写提交信息 —— HEAD 直接 amend（保留原父列表，合并提交也可改消息）；
     * 历史提交则分离重放：pick 该提交 → amend 改消息 → 重放其后提交。
     * 作者与作者日期保持不变（仅提交者信息随本地身份），重放冲突自动整体还原。
     */
    fun rewriteMessage(repoDir: File, hash: String, newMessage: String): Result<String> = withRepo(repoDir) { git ->
        if (newMessage.isBlank()) throw IllegalStateException("提交信息不能为空")
        setIdentity(git)
        val repo = git.repository
        val targetId = repo.resolve(hash.trim()) ?: throw IllegalStateException("找不到提交 $hash")
        val headId = repo.resolve(Constants.HEAD) ?: throw IllegalStateException("空仓库")
        val newHead: ObjectId = if (targetId == headId) {
            git.commit().setAmend(true).setMessage(newMessage.trim()).call().id
        } else {
            val (parent, after) = linearRange(git, hash)
            val branchFull = repo.fullBranch
            replayLinear(git, branchFull, headId, parent.id) {
                pickOrThrow(git, targetId)
                git.commit().setAmend(true).setMessage(newMessage.trim()).call()
                for (c in after) pickOrThrow(git, c.id)
            }
        }
        log.append("git", "reword ${hash.take(8)}")
        newHead.name()
    }

    // ────────────────────────── G. 高级：Stash / Tag / 冲突 ──────────────────────────

    /** 功能 43：Stash 储藏全部改动（含未跟踪） */
    fun stash(repoDir: File): Result<String> = withRepo(repoDir) { git ->
        val ref = git.stashCreate().setIncludeUntracked(true).setWorkingDirectoryMessage("码湾储藏")
            .call() ?: throw IllegalStateException("没有可储藏的改动")
        log.append("git", "stash ${repoDir.name}")
        ref.abbreviate(8).name()
    }

    /** Stash 列表（ref 字符串按顺序 stash@{i}） */
    fun stashList(repoDir: File): Result<List<Commit>> = withRepo(repoDir) { git ->
        git.stashList().call().map { toCommit(it) }
    }

    /** 恢复指定储藏（不删除记录用 apply；index 参数恢复暂存状态） */
    fun stashApply(repoDir: File, index: Int): Result<Unit> = withRepo(repoDir) { git ->
        git.stashApply().setStashRef("stash@{$index}").call()
    }

    /** 删除指定储藏 */
    fun stashDrop(repoDir: File, index: Int): Result<Unit> = withRepo(repoDir) { git ->
        git.stashDrop().setStashRef(index).call()
    }

    /** 功能 34/42/4.4：创建标签（message 空 → 轻量，否则附注）；atRef 指定时打在任意提交上 */
    fun createTag(repoDir: File, name: String, message: String? = null, atRef: String? = null): Result<Unit> =
        withRepo(repoDir) { git ->
            val cmd = git.tag().setName(name.trim())
            if (!atRef.isNullOrBlank()) {
                val id = git.repository.resolve(atRef.trim())
                    ?: throw IllegalStateException("找不到提交 $atRef")
                RevWalk(git.repository).use { walk -> cmd.setObjectId(walk.parseCommit(id)) }
            }
            if (!message.isNullOrBlank()) cmd.setMessage(message.trim())
            cmd.call()
        }

    /** 标签列表 */
    fun tags(repoDir: File): Result<List<Tag>> = withRepo(repoDir) { git ->
        git.tagList().call().map { Tag(Repository.shortenRefName(it.name), it.objectId?.abbreviate(8)?.name()) }
            .sortedByDescending { it.name }
    }

    /** 功能 35/36/37：冲突解决 —— 采用我方 / 对方后标记已解决 */
    fun resolveConflict(repoDir: File, path: String, action: ConflictAction): Result<Unit> = withRepo(repoDir) { git ->
        val stage = if (action == ConflictAction.OURS) CheckoutCommand.Stage.OURS else CheckoutCommand.Stage.THEIRS
        git.checkout().setStage(stage).addPath(path).call()
        git.add().addFilepattern(path).call()
        log.append("conflict", "$path → ${action.name}")
    }

    /** 冲突手动解决后标记（编辑器保存后调用） */
    fun markResolved(repoDir: File, path: String): Result<Unit> = withRepo(repoDir) { git ->
        git.add().addFilepattern(path).call()
    }

    /** 冲突文件三版本：base=stage1，ours=stage2（HEAD），theirs=stage3（对方） */
    data class ConflictSides(
        val base: ByteArray?,
        val ours: ByteArray?,
        val theirs: ByteArray?,
        val binary: Boolean,
        val oursLabel: String,
        val theirsLabel: String
    )

    /**
     * 功能 4.6：读取冲突文件的三个版本字节与二进制判定（首 8000 字节含 NUL 视为二进制）。
     * theirsLabel 取对方分支名（MERGE_HEAD 所指），取不到则退化为哈希缩写。
     */
    fun conflictSides(repoDir: File, path: String): ConflictSides? = runCatching {
        withRepo(repoDir) { git ->
            val repo = git.repository
            val cache = repo.readDirCache()
            fun bytesOf(stage: Int): ByteArray? = (0 until cache.entryCount)
                .asSequence()
                .map { cache.getEntry(it) }
                .firstOrNull { it.pathString == path && it.stage == stage }
                ?.let { repo.open(it.objectId).bytes }

            val base = bytesOf(1)
            val ours = bytesOf(2)
            val theirs = bytesOf(3)
            if (ours == null && theirs == null) null
            else {
                val sample = ours ?: theirs ?: ByteArray(0)
                val theirsLabel = runCatching {
                    val mh = repo.resolve("MERGE_HEAD")
                    if (mh != null) {
                        repo.refDatabase.getRefsByPrefix("refs/heads/")
                            .firstOrNull { it.objectId?.name() == mh.name() }
                            ?.let { Repository.shortenRefName(it.name) }
                            ?: mh.abbreviate(8).name()
                    } else null
                }.getOrNull()
                ConflictSides(
                    base = base,
                    ours = ours,
                    theirs = theirs,
                    binary = sample.take(8000).contains(0),
                    oursLabel = runCatching { repo.branch }.getOrNull() ?: "HEAD",
                    theirsLabel = theirsLabel ?: "MERGE_HEAD"
                )
            }
        }.getOrThrow()
    }.getOrNull()

    /** 冲突解决：把选定内容写回工作区并暂存标记（文本/二进制通用） */
    fun writeResolved(repoDir: File, path: String, bytes: ByteArray): Result<Unit> = withRepo(repoDir) { git ->
        val f = safeFile(repoDir, path) ?: throw IllegalStateException("非法路径 $path")
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
        git.add().addFilepattern(path).call()
        log.append("conflict", "$path → resolved (${bytes.size}B)")
    }

    fun writeResolvedText(repoDir: File, path: String, content: String): Result<Unit> =
        writeResolved(repoDir, path, content.toByteArray(Charsets.UTF_8))

    /** 提交日期格式化 */
    fun formatDate(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
}
