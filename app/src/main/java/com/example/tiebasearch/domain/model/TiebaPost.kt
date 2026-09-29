package com.example.tiebasearch.domain.model

/**
 * 领域模型：界面上「一条搜索结果」。
 *
 * 注意区分三种「发帖人标识」，很多人会在这里搞混：
 *   - authorNickname  昵称     —— 用户可见、**随时可改**，界面上主要显示这个
 *   - authorName      用户名   —— 登录名，不可改，但**实测经常返回空字符串**（用户设置了隐私）
 *   - authorUserId    数字UID  —— 永久唯一，是「用户ID」最标准的答案；缺失时退化为头像 hash
 */
data class TiebaPost(
    /** 楼层 id，列表去重用的最小唯一键 */
    val postId: Long,
    val threadId: Long,
    val title: String,
    /** 搜索接口返回的正文。⚠️ 实测这是**被截断的摘要**（长文末尾会出现「（ps:」这种断口），
     *  想看全文要用 [TiebaRepository.loadThreadFloors] 按 tid 再抓一次。 */
    val content: String,

    /** 数字 UID，可能为 null（接口没给） */
    val authorUserId: Long?,
    /** 用户名（登录名），可能为空串 */
    val authorName: String,
    /** 昵称，界面上主显示 */
    val authorNickname: String,

    /** 发帖时间，Unix 秒 */
    val createdAt: Long?,
    val modifiedAt: Long?,

    /** 来源吧名 —— 界面上的【来自XX吧】 */
    val forumName: String,
    val forumId: Long?,

    val replyCount: Int,
    val likeCount: Int,
    val avatarUrl: String,
    val threadUrl: String,
    val firstImageUrl: String?,

    /** 数据来源，调试/排查时很有用 */
    val source: DataSource
) {
    /** 作者唯一标识：UID 优先，退化到头像 hash */
    val authorStableId: String
        get() = authorUserId?.toString()
            ?: avatarUrl.substringAfterLast('/', "")
                .substringBefore('?')
                .ifBlank { "unknown" }

    /** 界面上显示的名字 */
    val authorDisplay: String
        get() = authorNickname.ifBlank { authorName }.ifBlank { "（用户名不可见）" }

    /**
     * 给浏览器打开用的**干净**原帖地址。
     *
     * 刻意统一成 https://tieba.baidu.com/p/{tid}，而不是直接用接口返回的 pb_url：
     * pb_url 形如 `...?tid=xxx&jump_tieba_native=1`，那个 jump_tieba_native 参数
     * 会诱导系统直接唤起「百度贴吧 App」而不是浏览器，和用户点这个按钮的预期不符。
     */
    val webUrl: String
        get() = if (threadId > 0) "https://tieba.baidu.com/p/$threadId" else threadUrl

    /** 正文是否疑似被接口截断 */
    val mayBeTruncated: Boolean
        get() = content.length >= 90 || content.endsWith("（ps:") || content.endsWith("...")

    enum class DataSource {
        /** 主路径：/mo/q/search/thread JSON 接口 */
        JSON_SEARCH,
        /** 兜底：/mo/q/m 吧内主题翻页 + 关键词本地匹配 */
        HTML_FORUM_CRAWL,
        /** 兜底补充：/mo/q/m?kz= 楼层页补全正文 */
        HTML_THREAD_DETAIL
    }
}

/** 帖子楼层（详情页「加载完整正文」时用） */
data class TiebaFloor(
    val floorId: String?,
    val content: String,
    val authorName: String,
    val createdAt: Long?,
    /** 是否为楼主（主楼） */
    val isOp: Boolean
)
