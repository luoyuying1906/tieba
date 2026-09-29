package com.example.tiebasearch.util

import android.content.Context

/**
 * 轻量设置存储。目前只存一个「请求间隔」。
 *
 * 为什么要持久化：用户调大间隔往往是因为刚被百度风控了，
 * 下次打开 App 如果又退回 800ms，等于白调。
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tieba_search_settings", Context.MODE_PRIVATE)

    /**
     * 两次贴吧请求之间的最小间隔（毫秒）。
     * 范围限制在 [MIN] ~ [MAX]，防止用户拉到极端值导致完全不可用或直接被封。
     */
    var requestIntervalMs: Long
        get() = prefs.getLong(KEY_INTERVAL, DEFAULT).coerceIn(MIN, MAX)
        set(value) {
            prefs.edit().putLong(KEY_INTERVAL, value.coerceIn(MIN, MAX)).apply()
        }

    companion object {
        /** 默认 800ms：一个人手动翻页的节奏 */
        const val DEFAULT = 800L
        /** 最快 300ms。再快基本等于主动申请验证码 */
        const val MIN = 300L
        /** 最慢 5000ms */
        const val MAX = 5000L

        private const val KEY_INTERVAL = "request_interval_ms"
    }
}
