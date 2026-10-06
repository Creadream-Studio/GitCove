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
            refreshAll()
        }
    }

    // ─────────────── 刷新 ───────────────

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = repoDir ?: return@launch
            c.gitCore.status(dir).onSuccess {
                status = it
                c.repoStore.updateCurrentBranch(repoId, it.branch)
            }.onFailure { msg.tryEmit("状态获取失败：${it.message}") }
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

    private fun statusFor(rel: String, st: RepoStatus?, isDir: Boolean): Status? {
        if (st == null) return null
        return if (isDir) {
            when {
                st.conflicts.any { it.startsWith("$rel/") } -> Status.CONFLICT
                (st.staged + st.unstaged).any { it.path.startsWith("$rel/") } -> Status.MODIFIED
                else -> null
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

    fun stage(paths: List<String>) = launchOp("已暂存 ${paths.size} 个文件") {
        c.gitCore.stage(repoDir!!, paths).getOrThrow()
    }

    fun unstage(paths: List<String>) = launchOp("已取消暂存 ${paths.size} 个文件") {
        c.gitCore.unstage(repoDir!!, paths).getOrThrow()
    }

    fun discard(paths: List<String>) = launchOp("已还原 ${paths.size} 个文件") {
        c.gitCore.discard(repoDir!!, paths).getOrThrow()
    }

    /** 功能 10/11/12/13：提交，可选立即推送 */
    fun commit(message: String, amend: Boolean, pushAfter: Boolean) = launchOp {
        require(message.isNotBlank()) { "请填写提交信息" }
        c.gitCore.commit(repoDir!!, message, amend).getOrThrow()
        if (pushAfter) {
            progress = "推送中…"
            c.gitCore.push(repoDir!!).getOrThrow()
            c.repoStore.updateLastSync(repoId, System.currentTimeMillis())
            msg.tryEmit("提交并推送成功")
        } else {
            msg.tryEmit("提交成功")
        }
    }

    // ─────────────── 同步 ───────────────

    fun fetch() = launchOp("Fetch 完成") {
        c.gitCore.fetch(repoDir!!) { progress = it }.getOrThrow()
    }

    fun pull() = launchOp("Pull 完成") {
        c.gitCore.pull(repoDir!!).getOrThrow()
        c.repoStore.updateLastSync(repoId, System.currentTimeMillis())
    }

    fun push(force: Boolean = false) = launchOp(if (force) "已强制推送" else "推送成功") {
        c.gitCore.push(repoDir!!, force) { progress = it }.getOrThrow()
        c.repoStore.updateLastSync(repoId, System.currentTimeMillis())
    }

    // ─────────────── 分支 ───────────────

    fun checkoutBranch(name: String) = launchOp("已切换到 $name") {
        c.gitCore.checkout(repoDir!!, name).getOrThrow()
    }

    fun createBranch(name: String, from: String?) = launchOp("已创建分支 $name") {
        c.gitCore.createBranch(repoDir!!, name, from).getOrThrow()
    }

    fun deleteBranch(name: String) = launchOp("已删除分支 $name") {
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

    fun revert(hash: String) = launchOp("已撤销提交 ${hash.take(8)}（生成反向提交）") {
        c.gitCore.revert(repoDir!!, hash).getOrThrow()
    }

    fun checkoutDetached(hash: String) = launchOp("已签出版本 ${hash.take(8)}（分离头指针）") {
        c.gitCore.checkoutDetached(repoDir!!, hash).getOrThrow()
    }

    // ─────────────── Stash / 标签 ───────────────

    fun stash() = launchOp("改动已储藏") {
        c.gitCore.stash(repoDir!!).getOrThrow()
    }

    fun stashApply(index: Int) = launchOp("储藏已恢复") {
        c.gitCore.stashApply(repoDir!!, index).getOrThrow()
    }

    fun stashDrop(index: Int) = launchOp("储藏记录已删除") {
        c.gitCore.stashDrop(repoDir!!, index).getOrThrow()
    }

    fun createTag(name: String, message: String?) = launchOp("已创建标签 $name") {
        c.gitCore.createTag(repoDir!!, name, message).getOrThrow()
    }

    // ─────────────── 冲突解决 ───────────────

    fun resolveConflict(path: String, action: ConflictAction) = launchOp("已采用${if (action == ConflictAction.OURS) "我方" else "对方"}版本") {
        c.gitCore.resolveConflict(repoDir!!, path, action).getOrThrow()
    }

    fun markResolved(path: String) = launchOp("已标记为已解决") {
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

    fun createFile(path: String) = launchOp("已创建 $path") {
        c.gitCore.createFile(repoDir!!, path).getOrThrow()
    }

    fun deleteFile(path: String) = launchOp("已删除 $path") {
        c.gitCore.deleteFile(repoDir!!, path).getOrThrow()
    }

    fun saveFile(path: String, content: String) = launchOp("已保存 $path") {
        c.gitCore.writeFile(repoDir!!, path, content).getOrThrow()
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
                msg.tryEmit("冲突：${e.message}")
            } catch (e: Exception) {
                msg.tryEmit(e.message ?: "操作失败")
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
