package com.example.tiebasearch.ui.theme

import android.content.Context
import android.content.res.Configuration
import com.example.tiebasearch.util.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局主题状态（需求一）。
 *
 * 为什么单独搞一个 object，而不是塞进 ViewModel？
 * 因为主题要包住**整个 App**（在 MainActivity 的 setContent 最外层），
 * 而 SearchViewModel 是在 SearchScreen 内部才创建的 —— 层级上够不着。
 *
 * 用一个极简的全局 StateFlow，Compose 那边 collectAsState 一下就能实时切换。
 */
object ThemeState {

    private val _isDark = MutableStateFlow(false)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    /** App 启动时调一次：恢复用户上次的选择；从没选过就跟随系统 */
    fun init(context: Context) {
        val store = SettingsStore(context)
        _isDark.value = store.darkTheme ?: systemIsDark(context)
    }

    /** 点按钮时调用：立即切换 + 持久化，下次打开还是这个模式 */
    fun toggle(context: Context) {
        val next = !_isDark.value
        SettingsStore(context).darkTheme = next
        _isDark.value = next
    }

    private fun systemIsDark(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }
}
