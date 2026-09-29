package com.example.tiebasearch.data.mapper

import com.example.tiebasearch.data.parser.MoHtmlParser
import com.example.tiebasearch.data.remote.dto.SearchPostDto
import com.example.tiebasearch.domain.model.TiebaFloor
import com.example.tiebasearch.domain.model.TiebaPost

/**
 * DTO → 领域模型。
 * 集中放这里，是为了让「接口字段变了要改哪里」只有一个地方。
 */

fun SearchPostDto.toDomain(): TiebaPost {
    val u = user
    return TiebaPost(
        postId = pid ?: urlSafeHash("$tid#$content"),
        threadId = tid.toLongOrNull() ?: 0L,
        title = title.trim(),
        content = content.trim(),
        authorUserId = u?.userId,
        authorName = u?.userName.orEmpty(),
        authorNickname = u?.showNickname.orEmpty(),
        createdAt = bestTime,
        modifiedAt = modifiedTime,
        forumName = forumName.ifBlank { forumInfo?.forumName.orEmpty() },
        forumId = forumId,
        replyCount = postNum,
        likeCount = likeNum,
        avatarUrl = u?.portrait.orEmpty(),
        threadUrl = pbUrl.ifBlank { "https://tieba.baidu.com/p/$tid" },
        firstImageUrl = media?.firstOrNull()?.let {
            it.smallPic.ifBlank { it.waterPic }.ifBlank { it.bigPic }
        },
        source = TiebaPost.DataSource.JSON_SEARCH
    )
}

/** HTML 楼层 → 领域模型（详情页「加载完整正文」用） */
fun MoHtmlParser.Floor.toDomain(): TiebaFloor = TiebaFloor(
    floorId = pid,
    content = content,
    authorName = authorName,
    avatarUrl = avatarUrl,
    createdAt = epochSeconds,
    isOp = isOp,
    floorNumber = floorNumber
)

/**
 * 吧内主题列表项 → 领域模型（「吧内标题检索」兜底路径用）。
 *
 * ⚠️ 诚实说明这条路径的局限：吧内列表页**只有标题**，
 * 拿不到正文、也拿不到数字 UID，所以 content 直接用标题填充，
 * authorUserId 为 null。这是兜底，不是主路径。
 */
fun MoHtmlParser.ThreadBrief.toThreadPost(forumName: String): TiebaPost {
    val id = tid.toLongOrNull()
    return TiebaPost(
        postId = id ?: urlSafeHash(tid),
        threadId = id ?: 0L,
        title = title,
        content = title,
        authorUserId = null,
        authorName = "",
        authorNickname = authorName,
        createdAt = epochSeconds,
        modifiedAt = null,
        forumName = forumName,
        forumId = null,
        replyCount = replyCount,
        likeCount = 0,
        avatarUrl = "",
        threadUrl = "https://tieba.baidu.com/p/$tid",
        firstImageUrl = null,
        source = TiebaPost.DataSource.HTML_FORUM_CRAWL
    )
}

/**
 * pid 缺失时的兜底唯一键。
 * 直接调 hashCode() 在不同进程/版本间可能不一致，所以自己算一个稳定值。
 */
private fun urlSafeHash(seed: String): Long {
    var h = 1125899906842597L
    for (c in seed) h = 31 * h + c.code
    return h and 0x7FFFFFFFFFFFFFFFL
}
