package com.gitcove.app.data.git

import com.gitcove.app.data.log.OpLog
import com.gitcove.app.data.store.AuthStore
import com.gitcove.app.domain.model.Branch
import com.gitcove.app.domain.model.Commit
import com.gitcove.app.domain.model.ConflictAction
import com.gitcove.app.domain.model.DiffEntryInfo
import com.gitcove.app.domain.model.DiffLine
import com.gitcove.app.domain.model.FileStatus
import com.gitcove.app.domain.model.LineType
import com.gitcove.app.domain.model.RepoStatus
import com.gitcove.app.domain.model.Status
import com.gitcove.app.domain.model.Tag
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.URIish
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
 * ⚠️ 边界统一捕获 Throwable 而非 Exception：JGit 内部可能抛出 Error 类型
 * （NoSuchMethodError / NoClassDefFoundError / OutOfMemoryError 等），
 * 若只捕 Exception 会直接穿透到协程导致应用闪退，这里将其转为 Result.failure。
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

    // ────────────────────────── C. 同步 ──────────────────────────

    /** 远端 origin URL */
    fun remoteUrl(repoDir: File): String? = runCatching {
        Git.open(repoDir).use { git ->
            git.repository.config.getString("remote", "origin", "url")
        }
    }.getOrNull()

    /** 功能 15/18：Fetch */
    fun fetch(repoDir: File, onProgress: (String) -> Unit = {}): Result<Unit> = try {
        Git.open(repoDir).use { git ->
            val url = git.repository.config.getString("remote", "origin", "url")
                ?: return@use
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
        val pulled = Git.open(repoDir).use { git ->
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
        Git.open(repoDir).use { git ->
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

    private fun toCommit(rc: RevCommit) = Commit(
        hash = rc.name(),
        message = rc.shortMessage,
        author = rc.authorIdent.name,
        email = rc.authorIdent.emailAddress,
        date = rc.commitTime * 1000L,
        parents = rc.parents.map { it.name }
    )

    /** 功能 31：Revert 撤销提交（生成反向提交） */
    fun revert(repoDir: File, hash: String): Result<String> = withRepo(repoDir) { git ->
        val id = git.repository.resolve(hash.trim())
            ?: throw IllegalStateException("找不到提交 $hash")
        val result = git.revert().include(id).call()
        log.append("git", "revert ${hash.take(8)}")
        result.name()
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

    /** 功能 42：创建标签（message 为空 → 轻量标签，否则附注标签） */
    fun createTag(repoDir: File, name: String, message: String? = null): Result<Unit> = withRepo(repoDir) { git ->
        val cmd = git.tag().setName(name.trim())
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

    /** 提交日期格式化 */
    fun formatDate(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
}
