package com.example.tiebasearch.data.remote

import com.example.tiebasearch.data.remote.dto.TiebaSearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** 被风控拦下（返回验证页而不是数据）时抛这个，方便 UI 提示「慢一点，或过会儿再试」 */
class TiebaBlockedException(message: String) : IOException(message)

/**
 * 贴吧接口定义。
 *
 * ==== 实测结论（真实抓取验证，2026-10）====
 *
 * ✅ 可用 · 纯 JSON · 无需签名 · 无需登录 · **本身即全网搜索**
 *    GET https://tieba.baidu.com/mo/q/search/thread?word={关键词}&pn={页码}&ie=utf-8
 *    → 一次返回 tid / pid / title / content / time / user.user_id / forum_name 等全部所需字段
 *    ⚠️ 这个接口**没有**「只搜某个吧」的能力：实测传 kw 会被直接忽略。
 *       所以「吧内搜索」只能靠返回的 forum_name 在客户端过滤；
 *       本项目现已取消吧名限制，直接用它的全网搜索能力。
 *    ⚠️ 返回的 content 是**截断后的摘要**，不是全文。要看全文得用下面的 kz 接口。
 *
 * ✅ 可用 · 服务端渲染 HTML（需移动 UA）
 *    GET https://tieba.baidu.com/mo/q/m?kz={tid}&pn={页码}   → 帖子楼层（用于补全完整正文）
 *    GET https://tieba.baidu.com/mo/q/m?kw={吧名}&pn={页码}   → 吧内主题列表（当前未启用）
 *
 * ❌ 不可用 · 已 SPA 化（正文为空壳，Jsoup 抓不到任何东西）
 *    GET https://tieba.baidu.com/f/search/res?kw={吧名}&qw={关键词}
 *    GET https://tieba.baidu.com/p/{tid}
 *    加 mo_device=1 也救不回来，实测无用。
 *
 * ⚠️ 需签名 · 返回 application/x-javascript
 *    GET https://tieba.baidu.com/c/f/frs/page?kw={吧名}
 *    需要 sign=MD5(排序后的参数串 + "tiebaclient!!!")。本项目刻意不依赖它。
 */
class TiebaApi(private val client: OkHttpClient = TiebaHttp.client) {

    private val json = Json {
        ignoreUnknownKeys = true   // 贴吧会塞 sample_switch 这种几十 KB 的无用字段
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 全网搜索帖子。
     *
     * @param word  关键词，例如「诡秘之主」
     * @param page  页码，从 1 开始
     */
    suspend fun searchThreads(word: String, page: Int = 1): TiebaSearchResponse {
        val url = buildString {
            append("https://tieba.baidu.com/mo/q/search/thread?")
            append("word=").append(enc(word))
            append("&ie=utf-8")
            append("&rn=30")
            // 贴吧 /mo/ 系列统一用 pn 表示「偏移量/页码」，首页为 0。
            // 若你实测发现翻页错位，把这里改成 page 即可。
            append("&pn=").append(page - 1)
        }
        val body = getString(url)
        return try {
            json.decodeFromString(TiebaSearchResponse.serializer(), body)
        } catch (e: Exception) {
            throw IOException("搜索结果解析失败（接口结构可能已变更）。原始响应前 200 字：${body.take(200)}", e)
        }
    }

    /**
     * 帖子楼层页（HTML）。用于「加载完整正文」——
     * 因为搜索接口只给截断摘要，想看全文只能按 tid 再抓一次这个页面。
     */
    suspend fun threadFloorsHtml(tid: String, page: Int = 1): String =
        getString("https://tieba.baidu.com/mo/q/m?kz=$tid&pn=$page")

    /** 吧内主题列表页（HTML）。当前未启用，保留给将来可能的吧内翻页需求。 */
    suspend fun forumThreadsHtml(forum: String, page: Int = 1): String =
        getString("https://tieba.baidu.com/mo/q/m?kw=${enc(forum)}&pn=$page&ie=utf-8")

    /** 调试/自检用：拿任意 URL 的原始文本 */
    suspend fun raw(url: String): String = getString(url)

    // ---------------------------------------------------------------- 内部实现

    private suspend fun getString(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code} —— $url")
            }
            val body = resp.body?.string().orEmpty()

            // 贴吧被风控时不会返回 4xx/5xx，而是返回一个 HTTP 200 的验证码/安全提示 HTML 页。
            // 这种情况必须在网络层拦住，否则会一路冒泡成莫名其妙的 JSON 解析错误。
            val head = body.trimStart()
            if (head.startsWith("<") && head.contains("验证", ignoreCase = true)) {
                throw TiebaBlockedException(
                    "触发了百度安全验证，请把「请求间隔」调大后重试（当前 ${TiebaHttp.minIntervalMs}ms）"
                )
            }
            body
        }
    }

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
}
