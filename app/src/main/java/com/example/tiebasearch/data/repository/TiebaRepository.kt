package com.example.tiebasearch.data.repository

import com.example.tiebasearch.data.mapper.toDomain
import com.example.tiebasearch.data.parser.MoHtmlParser
import com.example.tiebasearch.data.remote.TiebaApi
import com.example.tiebasearch.data.remote.TiebaBlockedException
import com.example.tiebasearch.domain.model.SearchQuery
import com.example.tiebasearch.domain.model.TiebaPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 一页搜索结果。
 * [rawCount] / [filteredOut] 是特意暴露出来的：贴吧是全吧搜索，
 * 用户指定「诡秘之主吧」时可能 30 条原始结果里只有 2 条属于该吧。
 * 不把这个数字显示出来，用户会以为程序坏了。
 */
data class SearchPage(
    val posts: List<TiebaPost>,
    val hasMore: Boolean,
    val rawCount: Int,
    val filteredOut: Int,
    val source: TiebaPost.DataSource,
    val fallbackReason: String? = null
)

/**
 * 搜索编排。三级策略，逐级降级：
 *
 *   1. JSON 全吧搜索 + 按 forum_name 本地过滤   ← 主路径，字段最全（有数字 UID）
 *      问题：某一页可能一条目标吧的都没有 → 加「向后再翻几页」的前瞻逻辑
 *   2. HTML 吧内主题翻页 + 关键词本地匹配        ← 主路径被风控/结构变更时的兜底
 *   3. HTML 楼层页补全正文/作者/时间             ← 第 2 级命中后按需触发（每帖 1 次请求）
 *
 * 之所以不做「客户端签名 API（c/f/frs/page）」，是因为第 1 级已经能给全字段，
 * 没必要引入 sign 计算和 protobuf 解析的复杂度 —— 那是这个项目最大的可维护性陷阱。
 */
class TiebaRepository(private val api: TiebaApi = TiebaApi()) {

    companion object {
        /** 严格过滤时，若当前页命中太少最多再往后追几页 */
        private const val MAX_LOOKAHEAD_PAGES = 4
        /** 一页至少命中这么多条就停止前瞻 */
        private const val MIN_RESULTS_PER_PAGE = 5
    }

    suspend fun search(query: SearchQuery, page: Int): SearchPage = withContext(Dispatchers.IO) {
        if (!query.isValid) throw IllegalArgumentException("关键词不能为空")

        try {
            searchViaJson(query, page)
        } catch (e: TiebaBlockedException) {
            throw e // 被风控了就老实告诉用户，别偷偷降级掩盖问题
        } catch (e: Exception) {
            // 主路径挂了 → 降级到 HTML 翻页
            crawlForumFallback(query, page, reason = e.message ?: e.javaClass.simpleName)
        }
    }

    // ---------------------------------------------------------- 第 1 级：JSON

    private suspend fun searchViaJson(query: SearchQuery, page: Int): SearchPage {
        val target = query.normalizedForum
        val strict = query.strictForumOnly && target.isNotBlank()

        val byPostId = LinkedHashMap<Long, TiebaPost>()
        var rawCount = 0
        var filteredOut = 0
        var cursor = page
        var hasMore = false
        var lookahead = 0

        while (true) {
            val resp = api.searchThreads(query.keyword, cursor, target.ifBlank { null })

            if (resp.no != 0 || resp.data == null) {
                // 第一页就失败 → 抛出去让上层降级；已经攒到数据则保留现有结果
                if (byPostId.isEmpty()) {
                    throw IOException("接口返回错误码 no=${resp.no}, error=${resp.error}")
                }
                hasMore = false
                break
            }

            val dtoList = resp.data.postList
            rawCount += dtoList.size

            for (dto in dtoList) {
                val post = dto.toDomain()
                if (strict && !post.belongsTo(target)) {
                    filteredOut++
                    continue
                }
                // 用 getOrPut 而不是 putIfAbsent：
                // Kotlin 的 MutableMap 接口并不保证暴露 JDK 的 putIfAbsent，
                // getOrPut 是 Kotlin 标准库自有函数，语义完全一致（存在则返回旧值、不覆盖）。
                byPostId.getOrPut(post.postId) { post }
            }

            hasMore = resp.data.hasMore == 1
            cursor++
            lookahead++

            val enough = byPostId.size >= MIN_RESULTS_PER_PAGE
            if (!hasMore || enough || lookahead > MAX_LOOKAHEAD_PAGES) break
        }

        return SearchPage(
            posts = byPostId.values.toList(),
            hasMore = hasMore,
            rawCount = rawCount,
            filteredOut = filteredOut,
            source = TiebaPost.DataSource.JSON_SEARCH
        )
    }

    // ------------------------------------------------------- 第 2/3 级：HTML

    private suspend fun crawlForumFallback(
        query: SearchQuery,
        page: Int,
        reason: String?
    ): SearchPage {
        val forum = query.normalizedForum
        if (forum.isBlank()) {
            throw IOException("全吧搜索失败，且没有指定吧名，无法走兜底路径。原因：$reason")
        }

        val html = api.forumThreadsHtml(forum, page)
        val briefs = MoHtmlParser.parseThreadBriefs(html, forum)

        // 列表页只有标题，所以只能用标题做关键词匹配。
        // 这是兜底路径的天然局限：正文里含关键词但标题没含的帖子会漏掉。
        val hits = briefs.filter { it.title.contains(query.keyword, ignoreCase = true) }

        val posts = hits.map { brief ->
            // 命中后进楼层页拿正文 + 作者 + 精确时间。1 帖 1 请求，靠 TiebaHttp 的限速兜住。
            val floors = runCatching {
                MoHtmlParser.parseFloors(api.threadFloorsHtml(brief.tid, 1), brief.tid)
            }.getOrDefault(emptyList())

            brief.toDomain(forum, floors)
        }

        return SearchPage(
            posts = posts,
            hasMore = briefs.isNotEmpty(),
            rawCount = briefs.size,
            filteredOut = briefs.size - hits.size,
            source = TiebaPost.DataSource.HTML_FORUM_CRAWL,
            fallbackReason = reason
        )
    }

    // ------------------------------------------------------------------ 工具

    /** 吧名比对：接口返回不带「吧」字，用户输入通常带 */
    private fun TiebaPost.belongsTo(targetWithoutSuffix: String): Boolean {
        val mine = forumName.trim().removeSuffix("吧").trim()
        return mine == targetWithoutSuffix
    }
}
