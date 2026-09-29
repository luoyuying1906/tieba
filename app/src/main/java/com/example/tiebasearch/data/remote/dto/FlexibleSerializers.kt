package com.example.tiebasearch.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
// intOrNull / longOrNull / content 是 JsonPrimitive 的扩展属性，必须整包导入
import kotlinx.serialization.json.*

/**
 * 为什么需要这两个东西？
 *
 * 贴吧后端对同一个字段会混用 JSON 数字和 JSON 字符串。
 * 实测同一次请求里 `pid` 就出现过 153973273891（数字）和 "152881837851"（字符串）两种形态，
 * `vipInfo.a_score` 也出现过 -50 和 "100"。
 *
 * 如果直接把字段声明成 Long，遇到字符串那一版就会抛 JsonDecodingException，
 * 导致**整页结果解析失败**（而不是只坏一条）。用这组序列化器可以两种形态都吃下来。
 *
 * ⚠️⚠️ 极其重要的一条约束（我曾在这里写错过，导致编译失败）：
 *
 *   序列化器的类型参数必须和目标属性的可空性**完全一致**：
 *     - KSerializer<Long?>  只能用在 `Long?` 属性上
 *     - KSerializer<Int>    只能用在 `Int`  属性上
 *
 *   不能混用。原因是 `DeserializationStrategy<out T>` 是**协变**的，
 *   `KSerializer<Int?>` 并不是 `KSerializer<Int>` 的子类型，
 *   编译器会直接报 Type mismatch。
 *
 * 所以这里刻意分成两种形态：
 *   - FlexibleLongSerializer : KSerializer<Long?>  → 用于可空字段，解析不出来给 null
 *   - FlexibleIntSerializer  : KSerializer<Int>    → 用于非空字段，解析不出来给 0 兜底
 */

/** 用于所有 `Long?` 字段（pid / time / create_time / forum_id / user_id / log_id ...） */
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

/**
 * 用于所有非空 `Int` 字段（has_more / current_page / post_num / like_num / media.size ...）。
 * 解析不出来时返回 0 —— 这些字段都是展示用的统计值，给 0 比让整页崩掉好得多。
 */
object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)

    override fun deserialize(decoder: Decoder): Int {
        val json = decoder as? JsonDecoder ?: return decoder.decodeInt()
        return when (val el = json.decodeJsonElement()) {
            is JsonNull -> 0
            // 「15.5W」这种带单位的字符串会走到最后那个 0
            is JsonPrimitive -> el.intOrNull ?: el.content.trim().toIntOrNull() ?: 0
            else -> 0
        }
    }
}
