package com.example.tiebasearch.domain.model

/**
 * 领域模型：界面上「一条搜索结果」。
 *
 * 注意区分三种「发帖人标识」，很多人会在这里搞混：
 *   - authorNickname  昵称     —— 用户可见、**随时可改**，界面上主要显示这个
 *   - authorName      用户名   —— 登录名，不可改，但**实测经常返回空字符串**（用户设置了隐私）
 *   - authorId        数字UID  —— 永久唯一，是「用户ID」最标准的答案；缺失时退化为头像 hash
 */
data class TiebaPost(
    /** 楼层 id，列表去重用的最小唯一键 */
    val postId: Long,
    val threadId: Long,
    val title: String,
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

    /** 数据来源，调试/排查时很有用：告诉你是哪条策略抓到的 */
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

    enum class DataSource {
        /** 主路径：/mo/q/search/thread JSON 接口 */
        JSON_SEARCH,
        /** 兜底：/mo/q/m 吧内主题翻页 + 关键词本地匹配 */
        HTML_FORUM_CRAWL,
        /** 兜底补充：/mo/q/m?kz= 楼层页补全正文 */
        HTML_THREAD_DETAIL
    }
}

/** 一次搜索的入参 */
data class SearchQuery(
    /** 吧名，例如「诡秘之主吧」。留空 = 全吧搜索 */
    val forumName: String = "",
    /** 关键词，例如「诡秘之主」 */
    val keyword: String = "",
    /** 是否严格只保留目标吧的结果 */
    val strictForumOnly: Boolean = true
) {
    val isValid: Boolean get() = keyword.isNotBlank()

    /** 贴吧接口里 kw 不带「吧」字，需要做归一化 */
    val normalizedForum: String
        get() = forumName.trim().removeSuffix("吧").trim()
}
