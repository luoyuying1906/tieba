package com.example.tiebasearch.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 贴吧移动端搜索接口的响应模型。
 *
 * 接口：GET https://tieba.baidu.com/mo/q/search/thread?word={关键词}&pn={页码}&ie=utf-8
 * 实测（2026-10 验证）：返回 application/json，**无需签名、无需 Cookie、无需登录**。
 *
 * ⚠️ 重要实测结论：该接口的 `kw` 参数不生效 —— 它是「全吧搜索」。
 *    返回结果里每条都带 `forum_name`，所以要「指定吧内搜索」的正确做法是：
 *    请求全吧搜索 → 用 forum_name 做本地过滤（见 TiebaRepository）。
 *
 * ⚠️ 字段类型不一致是常态（贴吧后端会混用数字和字符串）：
 *    - `pid`      有时 153973273891（数字），有时 "152881837851"（字符串）
 *    - `vipInfo.a_score` 有时 -50（数字），有时 "100"（字符串）
 *    所以凡是不确定的字段一律挂 Flexible*Serializer，绝不用裸 Long/Int。
 */
@Serializable
data class TiebaSearchResponse(
    /** 0 = 成功，非 0 = 有错误，具体看 error */
    val no: Int = -1,
    val error: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
    val data: SearchData? = null,
    /** 服务端下发的 tbs，发帖/签到才需要；只读搜索用不到，但抓下来便于调试 */
    val tbs: String? = null,
    @SerialName("is_login") val isLogin: Int = 0,
    @SerialName("log_id") val logId: Long? = null
)

@Serializable
data class SearchData(
    /** 1 = 还有下一页，0 = 到底了。分页循环的终止条件 */
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("has_more") val hasMore: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("post_list") val postList: List<SearchPostDto> = emptyList()
)

/**
 * 搜索结果里的一个「帖子」。注意：贴吧的搜索结果粒度是**楼层(pid)**而不是主题(tid)，
 * 所以同一个 tid 可能出现多次（不同楼层命中了同一个关键词），入列表前需要按 pid 去重。
 */
@Serializable
data class SearchPostDto(
    /** 主题 id，字符串形式，对应 https://tieba.baidu.com/p/{tid} */
    val tid: String = "",
    /** 楼层 id —— 这是结果的最小唯一键 */
    @Serializable(with = FlexibleLongSerializer::class)
    val pid: Long? = null,
    /** 楼中楼 id，0 表示这是一楼/主楼回复 */
    val cid: String? = null,
    val title: String = "",
    val content: String = "",
    /** 发帖时间，Unix 秒 */
    @Serializable(with = FlexibleLongSerializer::class)
    val time: Long? = null,
    @Serializable(with = FlexibleLongSerializer::class)
    @SerialName("create_time") val createTime: Long? = null,
    @Serializable(with = FlexibleLongSerializer::class)
    @SerialName("modified_time") val modifiedTime: Long? = null,
    val user: UserDto? = null,
    /** 该主题的总回复数 */
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("post_num") val postNum: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("like_num") val likeNum: Int = 0,
    /** 来源吧 id —— 想精确过滤时比吧名更可靠（吧名可能被改名/繁简不一致） */
    @Serializable(with = FlexibleLongSerializer::class)
    @SerialName("forum_id") val forumId: Long? = null,
    /** 来源吧名，界面上【来自XX吧】就是它 */
    @SerialName("forum_name") val forumName: String = "",
    @SerialName("forum_info") val forumInfo: ForumInfoDto? = null,
    /** 帖子在网页版的地址 */
    @SerialName("pb_url") val pbUrl: String = "",
    val media: List<MediaDto>? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val type: Int = 0
) {
    /** 时间字段有多个，按可靠性依次回退 */
    val bestTime: Long? get() = time ?: createTime ?: modifiedTime
}

@Serializable
data class UserDto(
    /** 用户名（登录名，不可改）。⚠️ 实测经常是空字符串，不能当主显示名 */
    @SerialName("user_name") val userName: String = "",
    /** 数字 UID —— 这是需求里「发帖人的用户ID」最标准的答案 */
    @Serializable(with = FlexibleLongSerializer::class)
    @SerialName("user_id") val userId: Long? = null,
    val portrait: String = "",
    val portraith: String? = null,
    /** 昵称（可改）—— 界面上应该优先显示这个，用户才认得出来 */
    @SerialName("show_nickname") val showNickname: String = "",
    @SerialName("vipInfo") val vipInfo: JsonElement? = null
) {
    /** 界面上显示的名字：昵称优先，空了退回用户名，再空了给个占位 */
    val displayName: String
        get() = showNickname.ifBlank { userName }.ifBlank { "（用户名不可见）" }

    /**
     * 稳定且唯一的作者标识。
     * 优先数字 UID；极端情况下 UID 缺失，退化用头像路径里的哈希段
     * （形如 tb.1.7f321285.tYvFT7NY1g_SFt0sxqRkOw —— 贴吧的头像 hash 与用户一一对应）。
     */
    val stableId: String
        get() = userId?.toString()
            ?: portrait.substringAfterLast('/', "")
                .substringBefore('?')
                .ifBlank { "unknown" }
}

@Serializable
data class ForumInfoDto(
    @SerialName("forum_name") val forumName: String = "",
    val avatar: String = "",
    /** 注意这里是「15.5W」这种字符串，不是数字 */
    @SerialName("post_num") val postNum: String = "",
    @SerialName("concern_num") val concernNum: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("is_official_forum") val isOfficialForum: Int = 0
)

@Serializable
data class MediaDto(
    val type: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val size: Int = 0,
    val width: String = "",
    val height: String = "",
    /** 缩略图，列表里优先用它，别用 big_pic（实测有大到 6MB 的） */
    @SerialName("small_pic") val smallPic: String = "",
    @SerialName("big_pic") val bigPic: String = "",
    @SerialName("water_pic") val waterPic: String = ""
)
