package com.example.tiebasearch.data.repository

import com.example.tiebasearch.data.mapper.toDomain
import com.example.tiebasearch.data.parser.MoHtmlParser
import com.example.tiebasearch.data.remote.TiebaApi
import com.example.tiebasearch.domain.model.TiebaFloor
import com.example.tiebasearch.domain.model.TiebaPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 一页搜索结果。
 */
data class SearchPage(
    val posts: List<TiebaPost>,
    val hasMore: Boolean,
    /** 接口本次原始返回条数（去重前），用于在界面上说明「本页拿到 N 条」 */
    val rawCount: Int
)

/**
 * 搜索编排。
 *
 * 相比上一版**大幅简化**了，因为取消了「吧内搜索」：
 *   - 不再需要按 forum_name 做本地过滤
 *   - 因此也不需要「某一页命中太少就向后再翻几页」的前瞻逻辑
 *   - 一页 = 一次请求，逻辑线性、可预测
 *
 * 贴吧那个 JSON 接口本身就是全网搜索，这是它唯一的工作方式。
 */
class TiebaRepository(private val api: TiebaApi = TiebaApi()) {

    /** 全网搜索关键词 */
    suspend fun search(keyword: String, page: Int): SearchPage = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) throw IllegalArgumentException("关键词不能为空")

        val resp = api.searchThreads(keyword, page)

        if (resp.no != 0 || resp.data == null) {
            throw IOException("贴吧接口返回错误：no=${resp.no}, error=${resp.error}")
        }

        // 搜索结果的粒度是「楼层(pid)」而不是「主题(tid)」，同一主题的多个楼层命中时会出现多条。
        // 这里按 postId 去重。
        val byPostId = LinkedHashMap<Long, TiebaPost>()
        resp.data.postList.forEach { dto ->
            val post = dto.toDomain()
            // 用 getOrPut 而不是 putIfAbsent：
            // Kotlin 的 MutableMap 接口并不保证暴露 JDK 的 putIfAbsent，
            // getOrPut 是 Kotlin 标准库自有函数，语义完全一致（存在则返回旧值、不覆盖）。
            byPostId.getOrPut(post.postId) { post }
        }

        SearchPage(
            posts = byPostId.values.toList(),
            hasMore = resp.data.hasMore == 1,
            rawCount = resp.data.postList.size
        )
    }

    /**
     * 按 tid 抓取帖子的楼层列表 —— 用于详情页的「加载完整正文」。
     *
     * 为什么需要它：搜索接口返回的 `content` 实测是**截断后的摘要**，
     * 光靠它无法展示完整帖子内容。
     *
     * 走的是 /mo/q/m?kz={tid} 这个移动版 HTML 页面，解析交给 MoHtmlParser。
     */
    suspend fun loadThreadFloors(tid: String): List<TiebaFloor> = withContext(Dispatchers.IO) {
        if (tid.isBlank()) throw IllegalArgumentException("tid 不能为空")
        val html = api.threadFloorsHtml(tid, 1)
        MoHtmlParser.parseFloors(html, tid).map { it.toDomain() }
    }
}
