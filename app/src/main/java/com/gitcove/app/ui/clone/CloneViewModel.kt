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

    /** 发送一次性提示（如系统目录选择器返回了不支持的目录） */
    fun showMessage(text: String) {
        msg.tryEmit(text)
    }

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
            progress = c.strings.preparingClone
            try {
                val cleanUrl = url.trim()
                require(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://") ||
                    cleanUrl.startsWith("ssh://") || cleanUrl.startsWith("git@") ||
                    cleanUrl.startsWith("git://")) { c.strings.unsupportedProtocol }
                val repoName = name.trim().ifBlank { GitCredentials.repoNameOf(cleanUrl) }
                require(repoName.isNotBlank()) { c.strings.repoNameRequired }
                require(parentDir.isDirectory) { c.strings.badTargetDir(parentDir.absolutePath) }
                val dir = File(parentDir, repoName)
                require(!(dir.exists() && dir.listFiles()?.isNotEmpty() == true)) { c.strings.repoExists(repoName) }
                dir.mkdirs()
                c.gitCore.clone(cleanUrl, dir) { progress = it }.getOrThrow()
                val repo = c.repoStore.add(repoName, dir.absolutePath, cleanUrl)
                c.gitCore.status(dir).onSuccess { c.repoStore.updateCurrentBranch(repo.id, it.branch) }
                c.repoStore.updateLastSync(repo.id, System.currentTimeMillis())
                c.opLog.append("repo", "Clone: $cleanUrl → $repoName")
                msg.tryEmit(c.strings.cloneDone(repoName))
                notifyDone(onDone)
            } catch (e: Throwable) {
                msg.tryEmit(c.strings.cloneFailed(e.message ?: e.javaClass.simpleName))
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
                require(repoName.isNotBlank()) { c.strings.repoNameRequired }
                require(parentDir.isDirectory) { c.strings.badTargetDir(parentDir.absolutePath) }
                val dir = File(parentDir, repoName)
                require(!dir.exists()) { c.strings.repoExists(repoName) }
                dir.mkdirs()
                c.gitCore.init(dir).getOrThrow()
                c.repoStore.add(repoName, dir.absolutePath, null)
                c.opLog.append("repo", "Create: $repoName")
                msg.tryEmit(c.strings.repoCreated(repoName))
                notifyDone(onDone)
            } catch (e: Throwable) {
                msg.tryEmit(c.strings.createFailed(e.message ?: e.javaClass.simpleName))
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
                require(dir.exists() && dir.isDirectory) { c.strings.dirNotFound(path) }
                if (!StorageAccess.hasAllFilesAccess(c.appContext) &&
                    dir.absolutePath.startsWith("/storage") && dir.listFiles() == null
                ) {
                    throw IllegalStateException(c.strings.noDirAccess)
                }
                val repoName = (name ?: dir.name).trim().ifBlank { dir.name }
                val isGit = c.gitCore.isValidRepo(dir)
                c.repoStore.add(repoName, dir.absolutePath, c.gitCore.remoteUrl(dir), isGit)
                c.opLog.append("repo", "Import: $repoName ← ${dir.absolutePath}${if (isGit) "" else " (non-git)"}")
                msg.tryEmit(
                    if (isGit) c.strings.importDone(repoName)
                    else c.strings.importDoneNonGit(repoName)
                )
                notifyDone(onDone)
            } catch (e: Throwable) {
                msg.tryEmit(c.strings.importFailed(e.message ?: e.javaClass.simpleName))
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
                .onFailure { msg.tryEmit(it.message ?: c.strings.listReposFailed) }
        }
    }

    fun dismissGithubList() { githubRepos = emptyList() }
}
