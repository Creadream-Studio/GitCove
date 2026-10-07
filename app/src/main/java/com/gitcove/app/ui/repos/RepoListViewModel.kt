package com.gitcove.app.ui.repos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitcove.app.di.AppContainer
import com.gitcove.app.domain.model.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 仓库列表 ViewModel（文档 5.3：仓库名 / 当前分支 / 待提交数量）
 */
class RepoListViewModel(private val c: AppContainer) : ViewModel() {

    data class Item(
        val repo: Repository,
        val dirty: Int = 0,
        val conflicts: Int = 0,
        val ahead: Int = 0,
        val behind: Int = 0,
        val broken: Boolean = false,
        val notGit: Boolean = false
    )

    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            loading = true
            val repos = c.repoStore.list()
            items = repos.map { repo ->
                val dir = File(repo.path)
                if (!dir.exists()) Item(repo, broken = true)
                else if (!c.gitCore.isValidRepo(dir)) Item(repo, notGit = true)
                else {
                    val st = c.gitCore.status(dir).getOrNull()
                    Item(
                        repo = repo,
                        dirty = st?.dirtyCount ?: 0,
                        conflicts = st?.conflicts?.size ?: 0,
                        ahead = st?.ahead ?: 0,
                        behind = st?.behind ?: 0,
                        broken = st == null
                    )
                }
            }
            loading = false
        }
    }

    /** 功能 6：删除仓库（可选删除文件） */
    fun delete(repo: Repository, deleteFiles: Boolean, onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (deleteFiles) File(repo.path).deleteRecursively()
            c.repoStore.remove(repo.id)
            c.opLog.append("repo", "Delete: ${repo.name}")
            onDone(c.strings.deleted(repo.name))
            refresh()
        }
    }
}
