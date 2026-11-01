package com.gitcove.app.ui.repo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitcove.app.data.git.GitConflictException
import com.gitcove.app.data.ai.AiCommit
import com.gitcove.app.di.AppContainer
import com.gitcove.app.domain.model.Branch
import com.gitcove.app.domain.model.Commit
import com.gitcove.app.domain.model.CommitStat
import com.gitcove.app.domain.model.DiffEntryInfo
import com.gitcove.app.domain.model.FileNode
import com.gitcove.app.domain.model.RepoStatus
import com.gitcove.app.domain.model.Repository
import com.gitcove.app.domain.model.Status
import com.gitcove.app.domain.model.ConflictAction
import com.gitcove.app.domain.model.ResetMode
import com.gitcove.app.domain.model.SearchHit
import com.gitcove.app.domain.model.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    /** 正在执行提交（按钮文字切“提交中…”用，与通用 busy 区分） */
    var committing by mutableStateOf(false)
        private set
    /** 正在 AI 生成提交信息（星星按钮转圈 + 防重复点击） */
    var aiGenerating by mutableStateOf(false)
        private set
    var progress by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<SearchHit>?>(null)
        private set
    /** 文件剪贴板（MP 管理器式复制/剪切/粘贴）；cut=false 复制、true 剪切 */
    var fileClip by mutableStateOf<FileClip?>(null)
        private set
    var lastCommitDetail by mutableStateOf<Pair<Commit, List<DiffEntryInfo>>?>(null)
        private set

    val repoDir: File? get() = repo?.let { File(it.path) }

    /** 认证存储（头像等 UI 直接读取 GitHub 账号摘要） */
    val auth get() = c.auth

    /** 刷新 GitHub 账号摘要（用户名/邮箱/头像 URL），失败静默 */
    suspend fun refreshGithubAccount() {
        c.githubApi.refreshAccountCache()
    }

    private val msg = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = msg

    /** 刷新串行锁：全量/轻量刷新并发时避免旧结果覆盖新结果 */
    private val refreshMutex = Mutex()

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
            refreshMutex.withLock {
                val dir = repoDir ?: return@withLock
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
                    return@withLock
                }
                // 先更新状态与文件列表（界面立即反映外部改动），历史/分支等次要信息随后补充
                c.gitCore.status(dir).onSuccess {
                    status = it
                    c.repoStore.updateCurrentBranch(repoId, it.branch)
                }.onFailure { msg.tryEmit(c.strings.statusFailed(it.message ?: "")) }
                files = listFiles(dir, currentPath, status)
                commits = c.gitCore.logGraph(dir).getOrDefault(emptyList())
                branches = c.gitCore.branches(dir).getOrDefault(emptyList())
                tags = c.gitCore.tags(dir).getOrDefault(emptyList())
                stashes = c.gitCore.stashList(dir).getOrDefault(emptyList())
                diffStaged = c.gitCore.diffEntries(dir, cached = true).getOrDefault(emptyList())
                diffUnstaged = c.gitCore.diffEntries(dir, cached = false).getOrDefault(emptyList())
                recentMsgs = c.gitCore.recentMessages(dir)
            }
        }
    }

    /**
     * 轻量刷新：仅 Git 状态 + 当前目录文件列表（毫秒级，不拉历史/分支/差异）。
     * 用于回到应用前台、从子页面返回时自动拉取外部改动（如 MT 管理器修改文件后切回），
     * 避免全量刷新带来的等待。
     */
    fun refreshQuick() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshMutex.withLock {
                val dir = repoDir ?: return@withLock
                val valid = c.gitCore.isValidRepo(dir)
                if (valid != isGitRepo) {
                    isGitRepo = valid
                    c.repoStore.updateIsGitRepo(repoId, valid)
                    repo = repo?.copy(isGitRepo = valid)
                }
                if (!valid) {
                    status = null
                    files = listFiles(dir, currentPath, null)
                    return@withLock
                }
                c.gitCore.status(dir).onSuccess {
                    status = it
                    c.repoStore.updateCurrentBranch(repoId, it.branch)
                }
                files = listFiles(dir, currentPath, status)
            }
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
            FileNode(
                d.name, rel, true, statusFor(rel, st, isDir = true),
                size = 0, lastModified = d.lastModified(),
                childCount = d.listFiles()?.size ?: 0
            )
        } + filesOnly.map { f ->
            val rel = relOf(f.name)
            FileNode(
                f.name, rel, false, statusFor(rel, st, isDir = false),
                size = f.length(), lastModified = f.lastModified()
            )
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

    // 暂存/取消暂存不弹提示：行在区块间移动就是反馈，且勾选高频操作
    // 每次都弹 Snackbar 会排成长队，把后面的「已提交」顶到几秒之后
    fun stage(paths: List<String>) = launchOp(refresh = Refresh.QUICK) {
        c.gitCore.stage(repoDir!!, paths).getOrThrow()
    }

    fun unstage(paths: List<String>) = launchOp(refresh = Refresh.QUICK) {
        c.gitCore.unstage(repoDir!!, paths).getOrThrow()
    }

    fun discard(paths: List<String>) = launchOp(c.strings.discardedN(paths.size), refresh = Refresh.QUICK) {
        c.gitCore.discard(repoDir!!, paths).getOrThrow()
    }

    /** 功能 10/11/12/13：提交，可选立即推送。
     *  提交成功立刻报「已提交」；如需推送，推送全程用进度文字展示，
     *  完成后再报「推送完成」——用户 1 秒内就能看到提交结果，不必陪网络干等。
     *  [onDone] 在提交（含推送）全部成功后回调，UI 借此清空输入框；
     *  失败时不回调，用户输入的提交信息得以保留，改完可直接重试。 */
    fun commit(
        message: String,
        amend: Boolean,
        pushAfter: Boolean,
        onDone: (() -> Unit)? = null
    ) = launchOp(refresh = Refresh.QUICK_THEN_FULL) {
        committing = true
        try {
            require(message.isNotBlank()) { c.strings.commitRequired }
            c.gitCore.commit(repoDir!!, message, amend).getOrThrow()
            // 乐观更新：提交成功即清空「已暂存」显示与角标，不等 status 重算（体感秒级成功）
            status?.let { st -> status = st.copy(staged = emptyList()) }
            diffStaged = emptyList()
            msg.tryEmit(c.strings.committed)
            if (pushAfter) {
                progress = c.strings.pushing
                c.gitCore.push(repoDir!!).getOrThrow()
                c.repoStore.updateLastSync(repoId, System.currentTimeMillis())
                msg.tryEmit(c.strings.pushDone)
            }
            withContext(Dispatchers.Main) { onDone?.invoke() }
        } finally {
            committing = false
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

    /** 图谱行统计缓存（主线程访问，行内 LaunchedEffect 触发） */
    private val commitStatsCache = HashMap<String, CommitStat>()

    suspend fun commitStats(hash: String): CommitStat? {
        commitStatsCache[hash]?.let { return it }
        val d = repoDir ?: return null
        val stat = withContext(Dispatchers.IO) { c.gitCore.commitStats(d, hash) } ?: return null
        commitStatsCache[hash] = stat
        return stat
    }

    /** 分享补丁文本 */
    suspend fun commitPatch(hash: String): String? {
        val d = repoDir ?: return null
        return withContext(Dispatchers.IO) { c.gitCore.commitPatch(d, hash) }
    }

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

    /** 功能 4.4：Cherry-pick 择优选取到当前分支 */
    fun cherryPick(hash: String) = launchOp(c.strings.cherryPickDone(hash.take(8))) {
        c.gitCore.cherryPick(repoDir!!, hash).getOrThrow()
    }

    /** 功能 4.4：重置当前分支到任意提交（软/混合/硬） */
    fun resetTo(hash: String, mode: ResetMode) = launchOp(c.strings.resetDone(hash.take(8))) {
        c.gitCore.resetTo(repoDir!!, hash, mode).getOrThrow()
    }

    /** 功能 4.4：删除提交 —— 其后提交逐个重放到父提交上，冲突自动整体还原（分支不动） */
    fun dropCommit(hash: String) = launchOp(c.strings.dropDone(hash.take(8))) {
        c.gitCore.dropCommit(repoDir!!, hash).getOrThrow()
    }

    /** 功能 4.4：改写提交信息 —— HEAD 直接 amend；历史提交重放改写，作者日期不变 */
    fun rewriteMessage(hash: String, message: String) = launchOp(c.strings.rewriteDone(hash.take(8))) {
        c.gitCore.rewriteMessage(repoDir!!, hash, message).getOrThrow()
    }

    fun checkoutDetached(hash: String) = launchOp(c.strings.checkoutDone(hash.take(8))) {
        c.gitCore.checkoutDetached(repoDir!!, hash).getOrThrow()
    }

    /**
     * 功能 4.6：AI 生成提交信息 —— 暂存 diff（无暂存回退工作区）→ 调 OpenAI 兼容
     * 接口 → 清洗后回填输入框。调用失败不中断：提示错误并按文件路径生成本地
     * 规则建议（type: update xxx）仍回填；未配置 / 无改动直接提示。
     */
    fun generateAiCommitMessage(onDone: (String) -> Unit) {
        if (aiGenerating) return
        if (!c.aiConfig.isConfigured) {
            msg.tryEmit(c.strings.aiNotConfigured)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            aiGenerating = true
            try {
                val dir = repoDir ?: return@launch
                val (diff, files) = c.gitCore.aiDiffText(dir).getOrElse {
                    msg.tryEmit(c.strings.aiFailedFallback(it.message ?: ""))
                    return@launch
                }
                if (files.isEmpty()) {
                    msg.tryEmit(c.strings.aiNothingStaged)
                    return@launch
                }
                try {
                    val text = c.aiChat.generateCommitMessage(files, diff)
                    withContext(Dispatchers.Main) { onDone(text) }
                    msg.tryEmit(c.strings.aiFilled)
                } catch (e: Throwable) {
                    // 失败回退：错误提示 + 本地规则建议仍回填，保证总有可用起点
                    msg.tryEmit(c.strings.aiFailedFallback(e.message ?: e.javaClass.simpleName))
                    val local = AiCommit.localFallback(files)
                    withContext(Dispatchers.Main) { onDone(local) }
                }
            } finally {
                aiGenerating = false
            }
        }
    }

    // ─────────────── Stash / 标签 ───────────────

    fun stash() = launchOp(c.strings.stashed, refresh = Refresh.QUICK) {
        c.gitCore.stash(repoDir!!).getOrThrow()
    }

    fun stashApply(index: Int) = launchOp(c.strings.stashApplied, refresh = Refresh.QUICK) {
        c.gitCore.stashApply(repoDir!!, index).getOrThrow()
    }

    fun stashDrop(index: Int) = launchOp(c.strings.stashDropped, refresh = Refresh.QUICK) {
        c.gitCore.stashDrop(repoDir!!, index).getOrThrow()
    }

    /** 功能 4.4：在任意提交上打标签（atRef 为空时打在 HEAD，BranchTab 原行为） */
    fun createTag(name: String, message: String?, atRef: String? = null) = launchOp(c.strings.tagCreated(name)) {
        c.gitCore.createTag(repoDir!!, name, message, atRef).getOrThrow()
    }

    // ─────────────── 冲突解决 ───────────────

    fun resolveConflict(path: String, action: ConflictAction) = launchOp(
        if (action == ConflictAction.OURS) c.strings.tookOurs else c.strings.tookTheirs,
        refresh = Refresh.QUICK
    ) {
        c.gitCore.resolveConflict(repoDir!!, path, action).getOrThrow()
    }

    fun markResolved(path: String) = launchOp(c.strings.markedResolved, refresh = Refresh.QUICK) {
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

    fun createFile(path: String) = launchOp(c.strings.fileCreated(path), refresh = Refresh.QUICK) {
        c.gitCore.createFile(repoDir!!, path).getOrThrow()
    }

    fun createDirectory(path: String) = launchOp(c.strings.dirCreated(path), refresh = Refresh.QUICK) {
        c.gitCore.createDirectory(repoDir!!, path).getOrThrow()
    }

    fun deleteFile(path: String) = launchOp(c.strings.fileDeleted(path), refresh = Refresh.QUICK) {
        c.gitCore.deleteFile(repoDir!!, path).getOrThrow()
    }

    /** 批量删除（多选模式） */
    fun deleteFiles(paths: List<String>) = launchOp(c.strings.deletedN(paths.size), refresh = Refresh.QUICK) {
        paths.forEach { c.gitCore.deleteFile(repoDir!!, it).getOrThrow() }
    }

    /** 重命名（同目录内改名字） */
    fun renameEntry(oldPath: String, newName: String) = launchOp(c.strings.renamedDone(newName), refresh = Refresh.QUICK) {
        val parent = oldPath.substringBeforeLast('/', "")
        val newPath = if (parent.isBlank()) newName else "$parent/$newName"
        c.gitCore.renameEntry(repoDir!!, oldPath, newPath).getOrThrow()
    }

    // ─────────────── 文件剪贴板（复制 / 剪切 / 粘贴） ───────────────

    fun clipCopy(paths: List<String>) {
        fileClip = FileClip(paths.toList(), cut = false)
        msg.tryEmit(c.strings.clipCopiedN(paths.size))
    }

    fun clipCut(paths: List<String>) {
        fileClip = FileClip(paths.toList(), cut = true)
        msg.tryEmit(c.strings.clipCutN(paths.size))
    }

    /** 粘贴到指定目录（默认当前目录）；剪切粘贴后清空剪贴板，复制粘贴保留 */
    fun clipPaste(targetDir: String? = null) = launchOp(refresh = Refresh.QUICK) {
        val clip = fileClip ?: return@launchOp
        val target = targetDir ?: currentPath
        if (clip.cut) {
            val n = c.gitCore.moveEntries(repoDir!!, clip.paths, target).getOrThrow()
            fileClip = null
            msg.tryEmit(c.strings.movedN(n))
        } else {
            val n = c.gitCore.copyEntries(repoDir!!, clip.paths, target).getOrThrow()
            msg.tryEmit(c.strings.pastedN(n))
        }
    }

    /** 目录递归大小（属性页展示，IO 线程调用） */
    suspend fun dirSize(rel: String): Long = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val base = repoDir?.let { d -> if (rel.isBlank()) d else File(d, rel) } ?: return@withContext 0L
        runCatching { base.walkBottomUp().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
    }

    fun saveFile(path: String, content: String) = launchOp(c.strings.fileSaved(path), refresh = Refresh.QUICK) {
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

    /** 操作完成后的刷新深度：高频小操作只跑毫秒级轻刷新，全量留给分支/同步类操作 */
    private enum class Refresh { NONE, QUICK, FULL, QUICK_THEN_FULL }

    private fun launchOp(
        toastOnSuccess: String? = null,
        refresh: Refresh = Refresh.FULL,
        op: suspend () -> Unit
    ) {
        // busy 在点按线程同步置位：进度条/按钮禁用当帧生效，不等 IO 调度——
        // 否则快速操作（提交、暂存）里“按下后毫无反应”的空窗就是这段调度延迟
        busy = true
        progress = ""
        viewModelScope.launch(Dispatchers.IO) {
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
                when (refresh) {
                    Refresh.NONE -> {}
                    // 全量刷新（历史+分支+标签+双差异+提交历史）在大仓库上是秒级 IO 风暴，
                    // 挂在勾选暂存/提交这类高频操作后面，正是“提交要等三秒”的主要构成。
                    // 高频操作只跑轻刷新：状态+文件列表毫秒级就绪，界面立刻可用；
                    // QUICK_THEN_FULL：先轻刷新让界面就绪，历史/分支随后在后台补齐，不阻塞任何交互。
                    Refresh.QUICK -> refreshQuick()
                    Refresh.QUICK_THEN_FULL -> {
                        refreshQuick()
                        viewModelScope.launch(Dispatchers.IO) { refreshAll() }
                    }
                    Refresh.FULL -> refreshAll()
                }
            }
        }
    }
}

/** 文件剪贴板：一组路径 + 是否剪切 */
data class FileClip(val paths: List<String>, val cut: Boolean)

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
