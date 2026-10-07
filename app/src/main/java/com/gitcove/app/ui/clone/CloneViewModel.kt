package com.gitcove.app.ui.clone

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitcove.app.data.git.GitCredentials
import com.gitcove.app.data.remote.GitHubApi
import com.gitcove.app.data.remote.GitHubRepoInfo
import com.gitcove.app.di.AppContainer
import com.gitcove.app.util.StorageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 克隆 / 新建 / 导入仓库（功能 1/2/3/4）
 */
class CloneViewModel(private val c: AppContainer) : ViewModel() {

    var busy by mutableStateOf(false)
        private set
    var progress by mutableStateOf("")
        private set
    var githubRepos by mutableStateOf<List<GitHubRepoInfo>>(emptyList())
        private set

    private val msg = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = msg

    /**
     * 完成回调（含 nav.popBackStack 等导航操作）必须回到主线程执行：
     * NavController 非线程安全，在 IO 线程调用会与 Compose 重组竞争导致闪退。
     */
    private suspend fun notifyDone(onDone: () -> Unit) {
        withContext(Dispatchers.Main.immediate) { onDone() }
    }

    /** 功能 1/2：从远程克隆到指定父目录 */
    fun cloneRepo(url: String, name: String, parentDir: File, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            progress = "准备克隆…"
            try {
                val cleanUrl = url.trim()
                require(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://") ||
                    cleanUrl.startsWith("ssh://") || cleanUrl.startsWith("git@") ||
                    cleanUrl.startsWith("git://")) { "不支持的协议，请使用 https / ssh / git 地址" }
                val repoName = name.trim().ifBlank { GitCredentials.repoNameOf(cleanUrl) }
                require(repoName.isNotBlank()) { "请填写仓库名称" }
                require(parentDir.isDirectory) { "目标目录不可用：${parentDir.absolutePath}（请检查权限）" }
                val dir = File(parentDir, repoName)
                require(!(dir.exists() && dir.listFiles()?.isNotEmpty() == true)) { "已存在同名仓库：$repoName" }
                dir.mkdirs()
                c.gitCore.clone(cleanUrl, dir) { progress = it }.getOrThrow()
                val repo = c.repoStore.add(repoName, dir.absolutePath, cleanUrl)
                c.gitCore.status(dir).onSuccess { c.repoStore.updateCurrentBranch(repo.id, it.branch) }
                c.repoStore.updateLastSync(repo.id, System.currentTimeMillis())
                c.opLog.append("repo", "克隆仓库: $cleanUrl → $repoName")
                msg.tryEmit("克隆完成：$repoName")
                notifyDone(onDone)
            } catch (e: Throwable) {
                msg.tryEmit("克隆失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = false
            }
        }
    }

    /** 功能 4：在指定父目录创建新仓库 */
    fun initRepo(name: String, parentDir: File, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            try {
                val repoName = name.trim()
                require(repoName.isNotBlank()) { "请填写仓库名称" }
                require(parentDir.isDirectory) { "目标目录不可用：${parentDir.absolutePath}（请检查权限）" }
                val dir = File(parentDir, repoName)
                require(!dir.exists()) { "已存在同名仓库：$repoName" }
                dir.mkdirs()
                c.gitCore.init(dir).getOrThrow()
                c.repoStore.add(repoName, dir.absolutePath, null)
                c.opLog.append("repo", "新建仓库: $repoName")
                msg.tryEmit("已创建新仓库：$repoName")
                notifyDone(onDone)
            } catch (e: Throwable) {
                msg.tryEmit("创建失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = false
            }
        }
    }

    /**
     * 功能 3/5：导入本地目录 / 关联外部目录。
     *
     * 任意目录均可导入：
     *   - 有效 Git 仓库 → 全部功能可用；
     *   - 非 Git 目录 → 也能导入，Git 功能置灰，需先在仓库页初始化 Git。
     * 另外检测存储访问权限：Android 11+ 未授予「所有文件访问」时
     * 目录内容不可读，会误报为空 / 无法识别 .git，此处提前拦截并提示。
     */
    fun importRepo(path: String, name: String?, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            try {
                val dir = File(path.trim())
                require(dir.exists() && dir.isDirectory) { "目录不存在：$path" }
                if (!StorageAccess.hasAllFilesAccess(c.appContext) &&
                    dir.absolutePath.startsWith("/storage") && dir.listFiles() == null
                ) {
                    throw IllegalStateException("无权访问该目录，请在系统设置中授予「所有文件」权限后重试")
                }
                val repoName = (name ?: dir.name).trim().ifBlank { dir.name }
                val isGit = c.gitCore.isValidRepo(dir)
                c.repoStore.add(repoName, dir.absolutePath, c.gitCore.remoteUrl(dir), isGit)
                c.opLog.append("repo", "导入仓库: $repoName ← ${dir.absolutePath}${if (isGit) "" else "（非 Git）"}")
                msg.tryEmit(
                    if (isGit) "已导入仓库：$repoName"
                    else "已导入：$repoName（非 Git 仓库，Git 功能需先初始化）"
                )
                notifyDone(onDone)
            } catch (e: Throwable) {
                msg.tryEmit("导入失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = false
            }
        }
    }

    /** 功能 2：从我的 GitHub 选择仓库 */
    fun loadMyGithubRepos() {
        viewModelScope.launch(Dispatchers.IO) {
            c.githubApi.listUserRepos()
                .onSuccess { githubRepos = it }
                .onFailure { msg.tryEmit(it.message ?: "获取仓库列表失败") }
        }
    }

    fun dismissGithubList() { githubRepos = emptyList() }
}
