package com.example.tiebasearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.tiebasearch.ui.search.SearchScreen
import com.example.tiebasearch.ui.theme.ThemeState
import com.example.tiebasearch.ui.theme.TiebaSearchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 恢复上次的主题选择；用户从没选过就跟随系统深色设置
        ThemeState.init(this)

        // 提前取出 applicationContext 传给切换回调，
        // 避免在 Composable 里引用 Activity 实例（那样会漏内存）
        val ctx = applicationContext

        setContent {
            val isDark by ThemeState.isDark.collectAsState()

            TiebaSearchTheme(darkTheme = isDark) {
                SearchScreen(
                    isDark = isDark,
                    onToggleTheme = { ThemeState.toggle(ctx) }
                )
            }
        }
    }
}
