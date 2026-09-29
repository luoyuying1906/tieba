package com.example.tiebasearch.util

import android.content.Context
import com.example.tiebasearch.domain.model.Forums
import com.example.tiebasearch.domain.model.TimeRange

/**
 * 轻量设置存储。
 * 全部走 SharedPreferences，个人自用不需要数据库。
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tieba_search_settings", Context.MODE_PRIVATE)

    // ---------------------------------------------------------- 请求间隔

    /**
     * 两次贴吧请求之间的最小间隔（毫秒）。
     * 范围限制在 [MIN] ~ [MAX]，防止用户拉到极端值导致完全不可用或直接被封。
     */
    var requestIntervalMs: Long
        get() = prefs.getLong(KEY_INTERVAL, DEFAULT).coerceIn(MIN, MAX)
        set(value) {
            prefs.edit().putLong(KEY_INTERVAL, value.coerceIn(MIN, MAX)).apply()
        }

    // ---------------------------------------------------------- 主题

    /**
     * 深色模式。
     * null = 用户还没选过 → 跟随系统；true/false = 用户明确选过，之后不再跟随系统。
     */
    var darkTheme: Boolean?
        get() = if (prefs.contains(KEY_DARK)) prefs.getBoolean(KEY_DARK, false) else null
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_DARK) else putBoolean(KEY_DARK, value)
            }.apply()
        }

    // ---------------------------------------------------------- DeepSeek

    /** DeepSeek API Key。默认空 —— 绝不硬编码，由用户自己填，只存在本机 */
    var deepSeekApiKey: String
        get() = prefs.getString(KEY_DEEPSEEK, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DEEPSEEK, value.trim()).apply()

    // ---------------------------------------------------------- 检索范围

    /** 用户勾选的吧（不带「吧」字）。默认全选 */
    var selectedForums: Set<String>
        get() {
            val raw = prefs.getStringSet(KEY_FORUMS, null) ?: return Forums.ALL.toSet()
            // 过滤掉已经不在允许列表里的历史值
            val cleaned = raw.filter { Forums.normalize(it) in Forums.ALL.map(Forums::normalize) }
            return if (cleaned.isEmpty()) Forums.ALL.toSet() else cleaned.toSet()
        }
        set(value) {
            // 存副本：SharedPreferences 要求不能存外部可变引用
            prefs.edit().putStringSet(KEY_FORUMS, HashSet(value)).apply()
        }

    // ---------------------------------------------------------- 时间过滤

    /**
     * 时间过滤范围。默认 [TimeRange.DEFAULT]（近3年）。
     * 用户改过之后就一直用他的选择。
     */
    var timeRange: TimeRange
        get() = TimeRange.fromName(prefs.getString(KEY_TIME_RANGE, null))
        set(value) {
            prefs.edit().putString(KEY_TIME_RANGE, value.name).apply()
        }

    companion object {
        /** 默认 800ms：一个人手动翻页的节奏 */
        const val DEFAULT = 800L
        /** 最快 300ms。再快基本等于主动申请验证码 */
        const val MIN = 300L
        /** 最慢 5000ms */
        const val MAX = 5000L

        private const val KEY_INTERVAL = "request_interval_ms"
        private const val KEY_DARK = "dark_theme"
        private const val KEY_DEEPSEEK = "deepseek_api_key"
        private const val KEY_FORUMS = "selected_forums"
        private const val KEY_TIME_RANGE = "time_range"
    }
}
