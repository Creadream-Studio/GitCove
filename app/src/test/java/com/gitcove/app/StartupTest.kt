package com.gitcove.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 启动冒烟测试：完整走一遍 Application.onCreate → Activity onCreate/Start/Resume，
 * 用于复现并守护「启动闪退」问题。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = GitCoveApp::class)
class StartupTest {

    @Test
    fun appStartsWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // 能拿到 Activity 实例且容器已初始化
                val app = activity.application as GitCoveApp
                check(app.container.repoParent.exists())
            }
        }
    }
}
