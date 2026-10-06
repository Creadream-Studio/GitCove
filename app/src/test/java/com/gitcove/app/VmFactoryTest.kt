package com.gitcove.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.gitcove.app.util.vmFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * vmFactory 语义回归测试。
 *
 * 修复前的 bug：vmFactory 非泛型签名导致 initializer 以 ViewModel 基类为 key 注册，
 * 按具体子类查找时抛 IllegalArgumentException("No initializer set for given class ...")，
 * 造成 App 启动即闪退（首页 RepoListScreen 第一个中招）。
 *
 * 走 ViewModelProvider（与 Compose viewModel() 相同的真实路径），
 * 因为 InitializerViewModelFactory 仅支持带 CreationExtras 的创建入口。
 */
class VmFactoryTest {

    class FakeViewModel : ViewModel()
    class AnotherViewModel : ViewModel()

    private fun <T : ViewModel> createViaProvider(
        modelClass: Class<T>,
        factory: ViewModelProvider.Factory
    ): T = ViewModelProvider(ViewModelStore(), factory)[modelClass]

    @Test
    fun factoryRegistersAndCreatesConcreteViewModelType() {
        val factory = vmFactory { FakeViewModel() }
        // 修复前此处抛 IllegalArgumentException: No initializer set for given class FakeViewModel
        val vm = createViaProvider(FakeViewModel::class.java, factory)
        assertTrue(vm is FakeViewModel)
    }

    @Test
    fun factoryWorksWithDifferentViewModelTypes() {
        val factory = vmFactory { AnotherViewModel() }
        val vm = createViaProvider(AnotherViewModel::class.java, factory)
        assertTrue(vm is AnotherViewModel)
    }
}
