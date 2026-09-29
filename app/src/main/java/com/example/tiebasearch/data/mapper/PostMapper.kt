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
    createdAt = epochSeconds,
    isOp = isOp
)

/**
 * pid 缺失时的兜底唯一键。
 * 直接调 hashCode() 在不同进程/版本间可能不一致，所以自己算一个稳定值。
 */
private fun urlSafeHash(seed: String): Long {
    var h = 1125899906842597L
    for (c in seed) h = 31 * h + c.code
    return h and 0x7FFFFFFFFFFFFFFFL
}
