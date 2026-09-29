package com.example.tiebasearch.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * DeepSeek（OpenAI 兼容）Chat Completions 的请求/响应模型。
 *
 * 注意 ChatMessage 请求和响应共用：
 *   请求 = { "role": "user", "content": "..." }
 *   响应 = { "role": "assistant", "content": "..." }
 * 结构一致，所以一个类够用。
 */

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
    /** 本项目用不到流式，固定 false */
    val stream: Boolean = false
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChatChoice> = emptyList()
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    val finishReason: String? = null
)
