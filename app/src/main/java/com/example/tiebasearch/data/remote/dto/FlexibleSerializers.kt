package com.example.tiebasearch.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
// intOrNull / longOrNull 是 JsonPrimitive 的扩展属性，必须整包导入
import kotlinx.serialization.json.*

/**
 * 为什么需要这两个东西？
 *
 * 贴吧后端对同一个字段会混用 JSON 数字和 JSON 字符串。
 * 实测同一次请求里 `pid` 就出现过 153973273891（数字）和 "152881837851"（字符串）两种形态，
 * `vipInfo.a_score` 也出现过 -50 和 "100"。
 *
 * 如果直接把字段声明成 Long，遇到字符串那一版就会抛
 * JsonDecodingException 导致**整页结果解析失败**（而不是只坏一条）。
 * 用这组序列化器可以两种形态都吃下来，解析不出来就返回 null，永远不会让整页崩。
 */

object FlexibleLongSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long? {
        // 只在 JSON 解码器下做宽松处理；其他格式走严格路径
        val json = decoder as? JsonDecoder ?: return decoder.decodeLong()
        return when (val el = json.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> el.longOrNull ?: el.content.trim().toLongOrNull()
            else -> null
        }
    }
}

object FlexibleIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }

    override fun deserialize(decoder: Decoder): Int? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeInt()
        return when (val el = json.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> el.intOrNull ?: el.content.trim().toIntOrNull()
            else -> null
        }
    }
}
