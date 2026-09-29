package com.example.tiebasearch.data.repository

import com.example.tiebasearch.data.mapper.toDomain
import com.example.tiebasearch.data.mapper.toThreadPost
import com.example.tiebasearch.data.parser.MoHtmlParser
import com.example.tiebasearch.data.remote.TiebaApi
import com.example.tiebasearch.domain.model.Forums
import com.example.tiebasearch.domain.model.TiebaFloor
import com.example.tiebasearch.domain.model.TiebaPost
import com.example.tiebasearch.domain.model.TimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 一页搜索结果。
 *
 * [nextPage] 是关键：因为要做「前瞻翻页筛选」，一次 search() 调用可能消耗掉好几页全网结果，
 * 所以必须告诉调用方「下次 loadMore 该从哪个全局页码继续」，否则会重复拉同一批数据。
 */
data class SearchPage(
    val posts: List<TiebaPost>,
    val nextPage: Int,
    val hasMore: Boolean,
    /** 本次为了筛出结果，扫描了几页全网结果 */
    val scannedPages: Int,
    /** 接口原始返回条数（过滤/去重前），用于在界面上解释「为什么只看这几条」 */
    val rawCount: Int,
    /** 被吧名过滤掉的条数 */
    val droppedByForum: Int,
    /** 被时间过滤掉的条数 */
    val droppedByTime: Int,
    /** 是否走了「吧内标题检索」兜底 */
    val usedTitleFallback: Boolean
)

/**
 * 搜索编排（需求二：限定吧 + 需求五：限定时间）。
 *
 * ⚠️ 两条核心约束（v1 实测结论，无法绕过）：
 *   贴吧搜索接口既**不支持限定吧**，也**不支持限定时间**。
 *   它只按关键词做全网相关性排序，传 kw 之类的参数会被直接忽略。
 *   所以「只搜这 5 个吧」「只看近 3 年」都只能在客户端用返回的
 *   forum_name 和 create_time 来筛。
 *
 * 由此产生的问题：前面几页可能一条都不满足条件。两个应对手段：
 *   1. **前瞻翻页**：一页命中太少就继续往后翻，最多扫 [MAX_SCAN_PAGES] 页，
 *      攒够 [TARGET_RESULTS] 条或翻到底为止。
 *   2. **吧内标题检索兜底**：一条都没命中时，改成逐个抓这几个吧的帖子列表页，
 *      用标题匹配关键词。保证用户不会看到空列表。
 *
 * 兜底路径的局限：列表页只有标题和粗略的日期，所以只能匹配标题，
 * 且时间精度只到「月-日」（年份是推算的）。
 */
class TiebaRepository(private val api: TiebaApi = TiebaApi()) {

    companion object {
        /** 单次 search 最多向后扫几页全网结果。8 页 × 30 条 = 240 条，翻完约 6 秒 */
        private const val MAX_SCAN_PAGES = 8
        /** 攒够这么多条就停止前瞻 */
        private const val TARGET_RESULTS = 10
        /** 兜底时每个吧最多取多少条 */
        private const val FALLBACK_LIMIT_PER_FORUM = 20
    }

    suspend fun search(
        keyword: String,
        forums: Set<String>,
        timeRange: TimeRange,
        startPage: Int
    ): SearchPage = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) throw IllegalArgumentException("关键词不能为空")
        if (forums.isEmpty()) throw IllegalArgumentException("请至少选择一个贴吧")

        val since = timeRange.sinceEpochSeconds()

        val collected = LinkedHashMap<Long, TiebaPost>()
        var rawCount = 0
        var droppedByForum = 0
        var droppedByTime = 0
        var cursor = startPage
        var scanned = 0
        var hasMore = false

        while (scanned < MAX_SCAN_PAGES) {
            val resp = api.searchThreads(keyword, cursor)

            if (resp.no != 0 || resp.data == null) {
                if (collected.isEmpty()) {
                    throw IOException("贴吧接口返回错误：no=${resp.no}, error=${resp.error}")
                }
                hasMore = false
                break
            }

            rawCount += resp.data.postList.size
            resp.data.postList.forEach { dto ->
                val post = dto.toDomain()

                // 筛选一：只保留用户勾选的吧
                if (!Forums.isAllowed(post.forumName, forums)) {
                    droppedByForum++
                    return@forEach
                }
                // 筛选二：只看指定时间范围内「发帖」的帖子
                if (!withinTime(post, since)) {
                    droppedByTime++
                    return@forEach
                }

                // 用 getOrPut 而不是 putIfAbsent：
                // Kotlin 的 MutableMap 接口并不保证暴露 JDK 的 putIfAbsent
                collected.getOrPut(post.postId) { post }
            }

            hasMore = resp.data.hasMore == 1
            cursor++
            scanned++

            if (!hasMore || collected.size >= TARGET_RESULTS) break
        }

        // 一条都没命中 → 走吧内标题检索兜底，避免用户看到空列表
        var usedFallback = false
        if (collected.isEmpty()) {
            crawlForumsByTitle(keyword, forums, since)
                .forEach { collected.getOrPut(it.postId) { it } }
            usedFallback = collected.isNotEmpty()
            hasMore = false // 兜底路径不做翻页
        }

        SearchPage(
            posts = collected.values.toList(),
            nextPage = cursor,
            hasMore = hasMore,
            scannedPages = scanned,
            rawCount = rawCount,
            droppedByForum = droppedByForum,
            droppedByTime = droppedByTime,
            usedTitleFallback = usedFallback
        )
    }

    /**
     * 发帖时间是否在范围内。
     * [since] 为 null 表示不限时间。
     * 时间字段缺失时**保守排除**：既然用户要按时间筛，来路不明的不该混进去。
     */
    private fun withinTime(post: TiebaPost, since: Long?): Boolean {
        if (since == null) return true
        val t = post.createdAt ?: return false
        return t >= since
    }

    /**
     * 兜底：逐个抓这几个吧的帖子列表页（HTML），用标题匹配关键词。
     * 每个吧一次请求，走 TiebaHttp 的限速。
     */
    private suspend fun crawlForumsByTitle(
        keyword: String,
        forums: Set<String>,
        since: Long?
    ): List<TiebaPost> {
        val out = mutableListOf<TiebaPost>()
        for (forum in forums) {
            // 某个吧抓取失败（被限制/改版）不影响其它吧，用 runCatching 兜住
            val html = runCatching { api.forumThreadsHtml(forum, 1) }.getOrNull() ?: continue
            val briefs = runCatching { MoHtmlParser.parseThreadBriefs(html, forum) }
                .getOrDefault(emptyList())

            briefs.asSequence()
                .filter { it.title.contains(keyword, ignoreCase = true) }
                .map { it.toThreadPost(forum) }
                .filter { withinTime(it, since) }
                .take(FALLBACK_LIMIT_PER_FORUM)
                .forEach { out += it }
        }
        return out
    }

    /**
     * 按 tid 抓取帖子的楼层列表 —— 用于详情页的「加载完整正文」。
     *
     * 为什么需要它：搜索接口返回的 `content` 实测是**截断后的摘要**，
     * 光靠它无法展示完整帖子内容，AI 总结也会因为输入太短而不准。
     */
    suspend fun loadThreadFloors(tid: String): List<TiebaFloor> = withContext(Dispatchers.IO) {
        if (tid.isBlank()) throw IllegalArgumentException("tid 不能为空")
        val html = api.threadFloorsHtml(tid, 1)
        MoHtmlParser.parseFloors(html, tid).map { it.toDomain() }
    }
}
