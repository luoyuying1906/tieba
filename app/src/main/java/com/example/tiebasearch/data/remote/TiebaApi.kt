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
 * ==== 实测结论（本次会话真实抓取验证，2026-10）====
 *
 * ✅ 可用 · 纯 JSON · 无需签名 · 无需登录
 *    GET https://tieba.baidu.com/mo/q/search/thread?word={关键词}&pn={页码}&ie=utf-8
 *    → 一次返回 tid / pid / title / content / time / user.user_id / forum_name 等全部所需字段
 *    ⚠️ 该接口的 `kw` 参数**不生效**，它是全吧搜索；吧内过滤要在客户端做。
 *
 * ✅ 可用 · 服务端渲染 HTML（用移动 UA）
 *    GET https://tieba.baidu.com/mo/q/m?kw={吧名}&pn={页码}    → 吧内主题列表
 *    GET https://tieba.baidu.com/mo/q/m?kz={tid}&pn={页码}     → 帖子楼层列表
 *
 * ❌ 不可用 · 已 SPA 化（正文为空壳，Jsoup 抓不到任何东西）
 *    GET https://tieba.baidu.com/f/search/res?kw={吧名}&qw={关键词}
 *    GET https://tieba.baidu.com/p/{tid}
 *    加 mo_device=1 也救不回来，实测无用。
 *
 * ⚠️ 需签名 · 返回 application/x-javascript
 *    GET https://tieba.baidu.com/c/f/frs/page?kw={吧名}
 *    这是客户端 API，需要 sign=MD5(排序后的参数串 + "tiebaclient!!!")。
 *    本项目**故意不依赖它** —— 上面那个 JSON 接口已经完全覆盖需求，不需要引入签名复杂度。
 */
class TiebaApi(private val client: OkHttpClient = TiebaHttp.client) {

    private val json = Json {
        ignoreUnknownKeys = true   // 贴吧会塞 sample_switch 这种几十 KB 的无用字段
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 搜索帖子。
     *
     * @param word  关键词，例如「诡秘之主」
     * @param page  页码，从 1 开始
     * @param forumHint 想要限定的吧名。会作为 `kw` 一起发出去（某些部署下可能生效），
     *                  但**不要依赖它** —— 调用方必须再用返回的 forum_name 做一次本地过滤。
     */
    suspend fun searchThreads(
        word: String,
        page: Int = 1,
        forumHint: String? = null
    ): TiebaSearchResponse {
        val url = buildString {
            append("https://tieba.baidu.com/mo/q/search/thread?")
            append("word=").append(enc(word))
            if (!forumHint.isNullOrBlank()) append("&kw=").append(enc(forumHint))
            append("&ie=utf-8")
            append("&rn=30")
            // 贴吧 /mo/ 系列统一用 pn 表示「偏移量/页码」，首页为 0。
            // 若你实测发现翻页错位，把这里改成 page 即可（自检工具会把 current_page 打出来）。
            append("&pn=").append(page - 1)
        }
        val body = getString(url)
        return try {
            json.decodeFromString(TiebaSearchResponse.serializer(), body)
        } catch (e: Exception) {
            throw IOException("搜索结果解析失败（接口结构可能已变更）。原始响应前 200 字：${body.take(200)}", e)
        }
    }

    /** 吧内主题列表页（HTML）。用于「全吧搜索漏掉目标吧帖子」时的兜底翻页。 */
    suspend fun forumThreadsHtml(forum: String, page: Int = 1): String =
        getString("https://tieba.baidu.com/mo/q/m?kw=${enc(forum)}&pn=$page&ie=utf-8")

    /** 帖子楼层页（HTML）。用于补全正文、作者、时间。 */
    suspend fun threadFloorsHtml(tid: String, page: Int = 1): String =
        getString("https://tieba.baidu.com/mo/q/m?kz=$tid&pn=$page")

    /** 调试用：拿任意 URL 的原始文本 */
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
                throw TiebaBlockedException("触发了百度安全验证，请降低频率后重试（当前间隔 ${TiebaHttp.minIntervalMs}ms）")
            }
            body
        }
    }

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
}
