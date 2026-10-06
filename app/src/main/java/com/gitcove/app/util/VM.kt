package com.gitcove.app.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** ViewModel 工厂快捷构建 */
fun vmFactory(create: () -> ViewModel): ViewModelProvider.Factory =
    viewModelFactory { initializer { create() } }
