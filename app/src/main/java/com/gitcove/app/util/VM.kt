package com.gitcove.app.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * ViewModel 工厂快捷构建。
 *
 * 注意：必须使用 reified 泛型——`initializer<T>` 会以 T 的实际类型作为注册 key。
 * 若签名写成非泛型 `vmFactory(create: () -> ViewModel)`，initializer 会被推断为
 * ViewModel 基类作为 key，运行时 `viewModel<XxxViewModel>()` 按具体类型查找失败，
 * 抛出 IllegalArgumentException("No initializer set for given class ...") 导致启动闪退。
 */
inline fun <reified VM : ViewModel> vmFactory(noinline create: () -> VM): ViewModelProvider.Factory =
    viewModelFactory { initializer { create() } }
