package com.example.tiebasearch.data.remote

import com.example.tiebasearch.data.remote.dto.ChatMessage
import com.example.tiebasearch.data.remote.dto.ChatRequest
import com.example.tiebasearch.data.remote.dto.ChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * DeepSeek Chat Completions 客户端。
 *
 * ⚠️ 为什么不用 TiebaHttp.client？
 * 那个客户端带了两个贴吧专用的东西：
 *   1. 伪装成移动端浏览器的 UA + 贴吧 Referer —— 发给 DeepSeek 是不合适的
 *   2. 全局限速拦截器 —— 会把 LLM 调用也一起拖慢，而且毫无意义
 * 所以这里单独建一个客户端。LLM 响应慢，读超时也放宽到 60 秒。
 *
 * ⚠️ API Key 从哪里来？
 * 由用户在 App 的「设置」里自己填，只保存在手机本地 SharedPreferences。
 * **绝不硬编码在源码里** —— 否则推到 GitHub 就等于把账单公开了。
 */
class DeepSeekApi {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun chat(apiKey: String, systemPrompt: String, userPrompt: String): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw IOException("没有配置 DeepSeek API Key")

            val body = json.encodeToString(
                ChatRequest.serializer(),
                ChatRequest(
                    model = DEFAULT_MODEL,
                    messages = listOf(
                        ChatMessage("system", systemPrompt),
                        ChatMessage("user", userPrompt)
                    )
                )
            )

            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()

                if (!resp.isSuccessful) {
                    // 把常见错误码翻译成人话，不然用户只会看到一串英文
                    val hint = when (resp.code) {
                        400 -> "请求格式有误"
                        401 -> "API Key 无效或已过期"
                        402 -> "DeepSeek 账户余额不足"
                        403 -> "无权访问该模型"
                        429 -> "请求过于频繁，请稍后再试"
                        500, 502, 503 -> "DeepSeek 服务端异常，稍后再试"
                        else -> "HTTP ${resp.code}"
                    }
                    throw IOException("DeepSeek 调用失败（$hint）：${text.take(200)}")
                }

                val parsed = json.decodeFromString(ChatResponse.serializer(), text)
                parsed.choices.firstOrNull()?.message?.content?.trim().orEmpty()
                    .ifBlank { throw IOException("DeepSeek 返回内容为空") }
            }
        }

    companion object {
        private const val ENDPOINT = "https://api.deepseek.com/chat/completions"

        /** deepseek-chat = V3 通用模型，总结这种任务够用且便宜 */
        private const val DEFAULT_MODEL = "deepseek-chat"

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
