package com.example.tiebasearch.data.repository

import android.content.Context
import com.example.tiebasearch.data.remote.DeepSeekApi
import com.example.tiebasearch.domain.model.TiebaPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * AI 帖子总结（需求四）。
 *
 * ==== 缓存机制 ====
 * 用户明确要求「同一个帖子只调用一次 API，避免重复扣费」。
 * 所以缓存是**读优先、写后置**的：
 *   - [cached] 先查本机缓存，命中就直接返回，绝不发请求
 *   - [summarize] 只有缓存未命中才会真的调用 DeepSeek，成功后写回缓存
 *
 * 存储用 SharedPreferences 的「一条一个 key」（key = summary_{tid}）而不是把整个
 * Map 序列化成一个 JSON 字符串 —— 后者每次写入都要重写全部数据，
 * 帖子攒到几百条之后会很慢，而且一旦序列化出错整个缓存全丢。
 *
 * 缓存不会过期：书帖的总结不会因为时间而失效，没必要花钱重算。
 */
class SummaryRepository(
    context: Context,
    private val api: DeepSeekApi = DeepSeekApi()
) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tieba_summary_cache", Context.MODE_PRIVATE)

    /** 查缓存。命中就绝不会再调 API */
    fun cached(threadId: Long): String? =
        prefs.getString(KEY_PREFIX + threadId, null)?.takeIf { it.isNotBlank() }

    fun isCached(threadId: Long): Boolean = cached(threadId) != null

    /** 清掉某条缓存（目前只在「重新总结」时用） */
    fun clear(threadId: Long) {
        prefs.edit().remove(KEY_PREFIX + threadId).apply()
    }

    fun cachedCount(): Int = prefs.all.keys.count { it.startsWith(KEY_PREFIX) }

    /**
     * 取总结。缓存优先；未命中才调 DeepSeek 并把结果写入缓存。
     */
    suspend fun summarize(post: TiebaPost, apiKey: String): String = withContext(Dispatchers.IO) {
        cached(post.threadId)?.let { return@withContext it }

        if (apiKey.isBlank()) {
            throw IOException("请先在首页「设置」里填入 DeepSeek API Key")
        }

        val result = api.chat(apiKey, SYSTEM_PROMPT, buildUserPrompt(post))

        prefs.edit().putString(KEY_PREFIX + post.threadId, result).apply()
        result
    }

    /** 强制重新生成（跳过缓存读取，但结果仍写回缓存） */
    suspend fun resummarize(post: TiebaPost, apiKey: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IOException("请先在首页「设置」里填入 DeepSeek API Key")
        }
        val result = api.chat(apiKey, SYSTEM_PROMPT, buildUserPrompt(post))
        prefs.edit().putString(KEY_PREFIX + post.threadId, result).apply()
        result
    }

    private fun buildUserPrompt(post: TiebaPost): String = buildString {
        appendLine("【来源贴吧】${post.forumName}吧")
        appendLine("【帖子标题】${post.title.ifBlank { "（无标题）" }}")
        appendLine("【发帖人】${post.authorDisplay}")
        appendLine("【正文】")
        append(post.content.ifBlank { "（正文为空，只能根据标题判断）" })
    }

    companion object {
        private const val KEY_PREFIX = "summary_"

        /**
         * 提示词。用户要的四个要素（推了什么书 / 类型 / 雷点郁闷点 / 推荐指数）都在这里约束死，
         * 并要求 3~5 句话、纯文本 —— 因为 Compose 的 Text 不会渲染 Markdown，
         * 如果模型输出 `**加粗**` 或 `- 列表`，界面上会原样显示符号，很难看。
         */
        private val SYSTEM_PROMPT = """
            你是一个贴吧「推书帖」的信息提取助手。
            用户会给你一个帖子的标题和正文，请输出 3~5 句话的中文总结。

            必须覆盖以下几点：
            1. 推荐或讨论了哪本书，作者是谁（若文中提到）；
            2. 这本书的类型、题材、标签；
            3. 有没有雷点、郁闷点、毒点；如果没有提到，就明确写「无明显雷点」；
            4. 推荐指数或整体评价（若文中提到；没提到就根据语气概括）。

            输出要求：
            - 直接给结论，不要寒暄，不要复述原文；
            - 不要使用 Markdown 语法（不要 ** 加粗、不要 # 标题、不要 - 列表符号）；
            - 就用普通的几句话，总共 3~5 句。
        """.trimIndent()
    }
}
