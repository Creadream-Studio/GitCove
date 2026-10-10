package com.gitcove.app.ui.repo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitcove.app.data.git.GitConflictException
import com.gitcove.app.di.AppContainer
import com.gitcove.app.domain.model.Branch
import com.gitcove.app.domain.model.Commit
import com.gitcove.app.domain.model.DiffEntryInfo
import com.gitcove.app.domain.model.FileNode
import com.gitcove.app.domain.model.RepoStatus
import com.gitcove.app.domain.model.Repository
import com.gitcove.app.domain.model.Status
import com.gitcove.app.domain.model.ConflictAction
import com.gitcove.app.domain.model.SearchHit
import com.gitcove.app.domain.model.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 仓库详情 ViewModel：一次加载全量状态，四个 Tab 共享。
 * 覆盖文档 B/C/D/E/F/G 章节操作入口。
 */
class RepoViewModel(private val c: AppContainer, val repoId: Long) : ViewModel() {

    var repo by mutableStateOf<Repository?>(null)
        private set
    var status by mutableStateOf<RepoStatus?>(null)
        private set
    /** 目录是否为有效 Git 仓库（false 时 Git 功能置灰，提示先初始化） */
    var isGitRepo by mutableStateOf(true)
        private set
    var currentPath by mutableStateOf("")
        private set
    var files by mutableStateOf<List<FileNode>>(emptyList())
        private set
    var commits by mutableStateOf<List<Commit>>(emptyList())
        private set
    var branches by mutableStateOf<List<Branch>>(emptyList())
        private set
    var tags by mutableStateOf<List<Tag>>(emptyList())
        private set
    var stashes by mutableStateOf<List<Commit>>(emptyList())
        private set
    var diffStaged by mutableStateOf<List<DiffEntryInfo>>(emptyList())
        private set
    var diffUnstaged by mutableStateOf<List<DiffEntryInfo>>(emptyList())
        private set
    var recentMsgs by mutableStateOf<List<String>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var progress by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<SearchHit>?>(null)
        private set
    var lastCommitDetail by mutableStateOf<Pair<Commit, List<DiffEntryInfo>>?>(null)
        private set

    val repoDir: File? get() = repo?.let { File(it.path) }

    private val msg = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = msg

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repo = c.repoStore.get(repoId)
            isGitRepo = repo?.isGitRepo ?: true
            refreshAll()
        }
    }

    // ─────────────── 刷新 ───────────────

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = repoDir ?: return@launch
            // 动态校验是否为 Git 仓库（导入时可能是普通目录；也可能刚在别处被初始化）
            val valid = c.gitCore.isValidRepo(dir)
            if (valid != isGitRepo) {
                isGitRepo = valid
                c.repoStore.updateIsGitRepo(repoId, valid)
                repo = repo?.copy(isGitRepo = valid)
            }
            if (!valid) {
                // 非 Git 仓库：只刷新文件浏览，不触碰任何 Git 状态
                status = null
                commits = emptyList()
                branches = emptyList()
                tags = emptyList()
                stashes = emptyList()
                diffStaged = emptyList()
                diffUnstaged = emptyList()
                recentMsgs = emptyList()
                files = listFiles(dir, currentPath, null)
                return@launch
            }
            c.gitCore.status(dir).onSuccess {
                status = it
                c.repoStore.updateCurrentBranch(repoId, it.branch)
            }.onFailure { msg.tryEmit(c.strings.statusFailed(it.message ?: "")) }
            files = listFiles(dir, currentPath, status)
            commits = c.gitCore.log(dir).getOrDefault(emptyList())
            branches = c.gitCore.branches(dir).getOrDefault(emptyList())
            tags = c.gitCore.tags(dir).getOrDefault(emptyList())
            stashes = c.gitCore.stashList(dir).getOrDefault(emptyList())
            diffStaged = c.gitCore.diffEntries(dir, cached = true).getOrDefault(emptyList())
            diffUnstaged = c.gitCore.diffEntries(dir, cached = false).getOrDefault(emptyList())
            recentMsgs = c.gitCore.recentMessages(dir)
        }
    }

    fun toast(text: String) { msg.tryEmit(text) }

    // ─────────────── 文件浏览 / 搜索 ───────────────

    fun navigateTo(path: String) {
        currentPath = path
        searchResults = null
        viewModelScope.launch(Dispatchers.IO) {
            val dir = repoDir ?: return@launch
            files = listFiles(dir, currentPath, status)
        }
    }

    fun searchFiles(keyword: String) {
        if (keyword.isBlank()) { searchResults = null; return }
        viewModelScope.launch(Dispatchers.IO) {
            val dir = repoDir ?: return@launch
            val hits = walkAllFiles(dir).filter { it.path.contains(keyword, ignoreCase = true) }
                .take(100)
                .map { SearchHit(it.path, 0, "") }
            searchResults = hits
        }
    }

    /** 功能 58/60/61：内容搜索 */
    fun searchContent(keyword: String) {
        if (keyword.isBlank()) { searchResults = null; return }
        viewModelScope.launch(Dispatchers.IO) {
            val dir = repoDir ?: return@launch
            val hits = mutableListOf<SearchHit>()
            outer@ for (f in walkAllFiles(dir)) {
                if (!com.gitcove.app.util.isLikelyTextFile(f.file.name, f.file.length())) continue
                runCatching {
                    f.file.useLines { lines ->
                        lines.forEachIndexed { idx, line ->
                            if (hits.size >= 200) return@runCatching
                            if (line.contains(keyword, ignoreCase = true)) {
                                hits.add(SearchHit(f.path, idx + 1, line.trim().take(160)))
                            }
                        }
                    }
                }
                if (hits.size >= 200) break
            }
            searchResults = hits
        }
    }

    private fun walkAllFiles(root: File): List<PathedFile> {
        val result = mutableListOf<PathedFile>()
        val stack = ArrayDeque<Pair<File, String>>()
        stack.addLast(root to "")
        while (stack.isNotEmpty()) {
            val (dir, prefix) = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children) {
                if (f.name == ".git") continue
                val rel = if (prefix.isBlank()) f.name else "$prefix/${f.name}"
                if (f.isDirectory) stack.addLast(f to rel) else result.add(PathedFile(rel, f))
            }
        }
        return result
    }

    private data class PathedFile(val path: String, val file: File)

    private fun listFiles(dir: File, path: String, st: RepoStatus?): List<FileNode> {
        val base = if (path.isBlank()) dir else File(dir, path)
        if (!base.exists()) return emptyList()
        val dirs = base.listFiles { f -> f.isDirectory && f.name != ".git" }
            ?.sortedWith(compareBy({ it.name.lowercase() })) ?: emptyList()
        val filesOnly = base.listFiles { f -> f.isFile }
            ?.sortedWith(compareBy({ it.name.lowercase() })) ?: emptyList()
        fun relOf(name: String) = if (path.isBlank()) name else "$path/$name"
        return dirs.map { d ->
            val rel = relOf(d.name)
            FileNode(d.name, rel, true, statusFor(rel, st, isDir = true))
        } + filesOnly.map { f ->
            val rel = relOf(f.name)
            FileNode(f.name, rel, false, statusFor(rel, st, isDir = false))
        }
    }

    /**
     * 状态聚合（用于名称后的彩色圆点标记）：
     * - 文件：冲突品红；新增（含未跟踪）绿；修改黄；删除红（提交后消失）
     * - 文件夹：内部有文件被修改或删除，或同时存在多种改动 → 黄；
     *   内部全部为新增（含未跟踪）→ 绿
     */
    private fun statusFor(rel: String, st: RepoStatus?, isDir: Boolean): Status? {
        if (st == null) return null
        return if (isDir) {
            val inner = (st.staged + st.unstaged).filter { it.path.startsWith("$rel/") }
            when {
                st.conflicts.any { it.startsWith("$rel/") } -> Status.CONFLICT
                inner.isEmpty() -> null
                // 内部全部为新增（含未跟踪）：文件夹本身是新添加的 → 绿
                inner.all { it.status == Status.ADDED || it.status == Status.UNTRACKED } -> Status.ADDED
                // 内部有修改或删除，或多种改动混合 → 黄
                else -> Status.MODIFIED
            }
        } else {
            st.conflicts.firstOrNull { it == rel }?.let { return Status.CONFLICT }
            (st.staged + st.unstaged).firstOrNull { it.path == rel }?.status
        }
    }

    /** 最近访问记录（功能 63） */
    fun openRecent(path: String) {
        c.auth.pushRecentFile(repoId, path)
    }

    // ─────────────── 暂存 / 提交 ───────────────

    fun stage(paths: List<String>) = launchOp(c.strings.stagedN(paths.size)) {
        c.gitCore.stage(repoDir!!, paths).getOrThrow()
    }

    fun unstage(paths: List<String>) = launchOp(c.strings.unstagedN(paths.size)) {
        c.gitCore.unstage(repoDir!!, paths).getOrThrow()
    }

    fun discard(paths: List<String>) = launchOp(c.strings.discardedN(paths.size)) {
        c.gitCore.discard(repoDir!!, paths).getOrThrow()
    }

    /** 功能 10/11/12/13：提交，可选立即推送 */
    fun commit(message: String, amend: Boolean, pushAfter: Boolean) = launchOp {
        require(message.isNotBlank()) { c.strings.commitRequired }
        c.gitCore.commit(repoDir!!, message, amend).getOrThrow()
        if (pushAfter) {
            progress = c.strings.pushing
            c.gitCore.push(repoDir!!).getOrThrow()
            c.repoStore.updateLastSync(repoId, System.currentTimeMillis())
            msg.tryEmit(c.strings.commitPushed)
        } else {
            msg.tryEmit(c.strings.committed)
        }
    }

    // ─────────────── 同步 ───────────────

    fun fetch() = launchOp(c.strings.fetchDone) {
        c.gitCore.fetch(repoDir!!) { progress = it }.getOrThrow()
    }

    fun pull() = launchOp(c.strings.pullDone) {
        c.gitCore.pull(repoDir!!).getOrThrow()
        c.repoStore.updateLastSync(repoId, System.currentTimeMillis())
    }

    fun push(force: Boolean = false) = launchOp(if (force) c.strings.forcePushDone else c.strings.pushDone) {
        c.gitCore.push(repoDir!!, force) { progress = it }.getOrThrow()
        c.repoStore.updateLastSync(repoId, System.currentTimeMillis())
    }

    // ─────────────── 分支 ───────────────

    fun checkoutBranch(name: String) = launchOp(c.strings.switchedBranch(name)) {
        c.gitCore.checkout(repoDir!!, name).getOrThrow()
    }

    fun createBranch(name: String, from: String?) = launchOp(c.strings.branchCreated(name)) {
        c.gitCore.createBranch(repoDir!!, name, from).getOrThrow()
    }

    fun deleteBranch(name: String) = launchOp(c.strings.branchDeleted(name)) {
        c.gitCore.deleteBranch(repoDir!!, name).getOrThrow()
    }

    fun merge(ref: String) = launchOp {
        val summary = c.gitCore.merge(repoDir!!, ref).getOrThrow()
        msg.tryEmit(summary)
    }

    // ─────────────── 历史 ───────────────

    fun loadCommitDetail(commit: Commit) {
        viewModelScope.launch(Dispatchers.IO) {
            val files = repoDir?.let { d -> c.gitCore.commitDiffEntries(d, commit.hash).getOrDefault(emptyList()) } ?: emptyList()
            lastCommitDetail = commit to files
        }
    }

    fun clearCommitDetail() { lastCommitDetail = null }

    fun revert(hash: String) = launchOp(c.strings.revertDone(hash.take(8))) {
        c.gitCore.revert(repoDir!!, hash).getOrThrow()
    }

    fun checkoutDetached(hash: String) = launchOp(c.strings.checkoutDone(hash.take(8))) {
        c.gitCore.checkoutDetached(repoDir!!, hash).getOrThrow()
    }

    // ─────────────── Stash / 标签 ───────────────

    fun stash() = launchOp(c.strings.stashed) {
        c.gitCore.stash(repoDir!!).getOrThrow()
    }

    fun stashApply(index: Int) = launchOp(c.strings.stashApplied) {
        c.gitCore.stashApply(repoDir!!, index).getOrThrow()
    }

    fun stashDrop(index: Int) = launchOp(c.strings.stashDropped) {
        c.gitCore.stashDrop(repoDir!!, index).getOrThrow()
    }

    fun createTag(name: String, message: String?) = launchOp(c.strings.tagCreated(name)) {
        c.gitCore.createTag(repoDir!!, name, message).getOrThrow()
    }

    // ─────────────── 冲突解决 ───────────────

    fun resolveConflict(path: String, action: ConflictAction) = launchOp(
        if (action == ConflictAction.OURS) c.strings.tookOurs else c.strings.tookTheirs
    ) {
        c.gitCore.resolveConflict(repoDir!!, path, action).getOrThrow()
    }

    fun markResolved(path: String) = launchOp(c.strings.markedResolved) {
        c.gitCore.markResolved(repoDir!!, path).getOrThrow()
    }

    /** 冲突文件两侧内容（用于手动解决参考） */
    fun conflictVersions(path: String): Pair<String?, String?>? {
        val dir = repoDir ?: return null
        return try {
            val ours = GitConflictVersions.readStageVersion(dir, path, 2)
            val theirs = GitConflictVersions.readStageVersion(dir, path, 3)
            ours to theirs
        } catch (e: Exception) {
            null
        }
    }

    // ─────────────── 文件操作 ───────────────

    fun createFile(path: String) = launchOp(c.strings.fileCreated(path)) {
        c.gitCore.createFile(repoDir!!, path).getOrThrow()
    }

    fun createDirectory(path: String) = launchOp(c.strings.dirCreated(path)) {
        c.gitCore.createDirectory(repoDir!!, path).getOrThrow()
    }

    fun deleteFile(path: String) = launchOp(c.strings.fileDeleted(path)) {
        c.gitCore.deleteFile(repoDir!!, path).getOrThrow()
    }

    fun saveFile(path: String, content: String) = launchOp(c.strings.fileSaved(path)) {
        c.gitCore.writeFile(repoDir!!, path, content).getOrThrow()
    }

    // ─────────────── Git 仓库初始化 ───────────────

    /** 对普通目录执行 git init，使 Git 功能可用 */
    fun initGitRepo() = launchOp(c.strings.gitInited) {
        val dir = repoDir ?: return@launchOp
        require(!c.gitCore.isValidRepo(dir)) { c.strings.alreadyGit }
        c.gitCore.init(dir).getOrThrow()
        isGitRepo = true
        c.repoStore.updateIsGitRepo(repoId, true)
        repo = repo?.copy(isGitRepo = true)
    }

    // ─────────────── 通用执行器 ───────────────

    private fun launchOp(toastOnSuccess: String? = null, op: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            progress = ""
            try {
                op()
                toastOnSuccess?.let { msg.tryEmit(it) }
            } catch (e: GitConflictException) {
                msg.tryEmit(c.strings.conflictPrefix(e.message ?: ""))
            } catch (e: Throwable) {
                // 捕获 Throwable：Error 类异常（如 JGit 内部 NoSuchMethodError）也转为提示，不闪退
                msg.tryEmit(e.message ?: c.strings.opFailedNamed(e.javaClass.simpleName))
            } finally {
                busy = false
                progress = ""
                refreshAll()
            }
        }
    }
}

/** 冲突版本读取（index stage 2 = ours，stage 3 = theirs） */
private object GitConflictVersions {
    fun readStageVersion(dir: File, path: String, stage: Int): String? = runCatching {
        org.eclipse.jgit.api.Git.open(dir).use { git ->
            val repo = git.repository
            val cache = repo.readDirCache()
            var content: String? = null
            for (i in 0 until cache.getEntryCount()) {
                val e = cache.getEntry(i)
                if (e.pathString == path && e.stage == stage) {
                    content = String(repo.open(e.objectId).bytes, Charsets.UTF_8)
                    break
                }
            }
            content
        }
    }.getOrNull()
}
