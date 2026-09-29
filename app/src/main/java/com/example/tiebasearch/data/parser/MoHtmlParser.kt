package com.example.tiebasearch.data.parser

import com.example.tiebasearch.util.TimeFormat
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * /mo/q/m 移动版页面的解析器（兜底路径）。
 *
 * ⚠️ 写在最前面的免责说明：
 * 贴吧的 HTML 类名（sfrs / spb / frslist_content ...）会随版本变化，**不要**把选择器写死。
 * 所以这里刻意**不依赖任何具体 class 名**，而是用「结构特征」来定位：
 *
 *   - 帖子链接的形态是稳定的：/p/{tid}?lp=...&mo_device=1&is_jingpost=0  ← 已实测确认
 *   - 头像地址里必然含 /sys/portrait/                                  ← 已实测确认
 *   - 时间文本符合 TimeFormat 里的四种格式                              ← 已实测确认
 *
 * 这个策略的鲁棒性明显好于 `div.s_post > .p_title` 这种硬编码路径。
 * 但正文段落、作者名的归属仍属于「启发式推断」，如果解析结果不对，
 * 用 DebugDumper 把原始 HTML 导出看一眼，大概率只需要调 [guessAuthor] 一个函数。
 */
object MoHtmlParser {

    private val TID_RE = Regex("""/p/(\d+)""")
    private val PID_RE = Regex("""[?&]pid=(\d+)""")

    /** 吧内主题列表里的一条（列表页只有标题，没有正文） */
    data class ThreadBrief(
        val tid: String,
        val title: String,
        val authorName: String,
        val timeText: String,
        val epochSeconds: Long?,
        val replyCount: Int
    )

    /** 楼层页里的一层 */
    data class Floor(
        val tid: String,
        val pid: String?,
        val content: String,
        val authorName: String,
        val epochSeconds: Long?,
        val isOp: Boolean
    )

    /**
     * 解析「吧内主题列表」。
     * @param forumName 当前吧名，用于把作者名从标题/吧名里区分出来
     */
    fun parseThreadBriefs(html: String, forumName: String): List<ThreadBrief> {
        val doc = Jsoup.parse(html)
        val anchors = doc.select("a[href*=\"/p/\"]")
        val out = LinkedHashMap<String, ThreadBrief>()

        for (a in anchors) {
            val tid = TID_RE.find(a.attr("href"))?.groupValues?.get(1) ?: continue
            if (out.containsKey(tid)) continue

            val container = findContainer(a) ?: continue
            val text = container.text()
            val title = a.text().trim().ifBlank { extractTitleFallback(container, a) }
            if (title.isBlank()) continue

            val epoch = TimeFormat.parseToEpochSeconds(text)
            out[tid] = ThreadBrief(
                tid = tid,
                title = title,
                authorName = guessAuthor(container, title, forumName),
                timeText = text,
                epochSeconds = epoch,
                replyCount = guessReplyCount(container)
            )
        }
        return out.values.toList()
    }

    /** 解析「帖子楼层页」 */
    fun parseFloors(html: String, tid: String): List<Floor> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<Floor>()

        // 楼层正文在移动版里通常是 class 含 "p_content" / "d_post_content" 的块；
        // 拿不到就退化为「找所有 div」。
        // 这里刻意写成显式 if，不用 Collection.ifEmpty —— 它的泛型约束
        // (C : Collection<*>, C : R) 在 Elements 上能否推断成功依赖版本，不值得冒险。
        val byClass: Elements = doc.select(
            "[class*=\"p_content\"], [class*=\"d_post_content\"], [class*=\"post_content\"]"
        )
        val contentNodes: Elements = if (byClass.isNotEmpty()) byClass else doc.select("div")

        for (node in contentNodes) {
            val content = node.text().trim()
            if (content.length < 2) continue

            val container = findContainer(node)
            val pid = container?.select("a[href*=\"postreport\"]")
                ?.firstOrNull()
                ?.let { PID_RE.find(it.attr("href"))?.groupValues?.get(1) }

            out += Floor(
                tid = tid,
                pid = pid,
                content = content,
                authorName = container?.let { guessAuthor(it, content, "") }.orEmpty(),
                epochSeconds = container?.let { TimeFormat.parseToEpochSeconds(it.text()) },
                isOp = container?.text()?.contains("楼主") == true
            )
        }
        return out.distinctBy { it.content }
    }

    // ------------------------------------------------------------ 启发式工具

    /**
     * 从某个节点向上找到一个「完整的条目容器」：
     * 特征是既包含头像图，又包含可识别的时间文本。
     * 找 6 层就够了，再多会一路爬到整个列表把几十条混成一条。
     */
    private fun findContainer(start: Element): Element? {
        var current: Element? = start
        var depth = 0
        while (depth < 6) {
            val el = current ?: break
            val t = el.text()
            val hasPortrait = el.select("img[src*=\"/sys/portrait/\"], img[src*=\"portrait\"]").isNotEmpty()
            val hasTime = TimeFormat.parseToEpochSeconds(t) != null
            if (hasTime && (hasPortrait || t.length > 12)) return el
            current = el.parent()
            depth++
        }
        return start.parent()
    }

    private fun extractTitleFallback(container: Element, anchor: Element): String =
        container.select("a[href*=\"/p/\"]").firstOrNull()?.text()?.trim().orEmpty()
            .ifBlank { anchor.attr("title").trim() }

    /**
     * 推断作者名。
     * 依据实测的渲染顺序：头像 → 作者名 → 时间 → 标题。
     * 因此作者名应当是「时间之前、且不等于标题」的那段短文本。
     */
    private fun guessAuthor(container: Element, title: String, forumName: String): String {
        val candidates = container.select("a, span, div")
            .map { it.ownText().trim() }
            .filter { it.isNotBlank() }

        for (c in candidates) {
            if (c == title) continue
            if (c.length > 24) continue              // 太长的肯定是正文
            if (c in IGNORE_WORDS) continue
            if (c == forumName) continue
            if (c.all { it.isDigit() || it == ':' }) continue  // 纯数字/冒号 = 时间或楼层号
            if (TimeFormat.parseToEpochSeconds(c) != null && c.length <= 8) continue
            if (c.contains("吧") && c.length <= 8) continue
            return c
        }
        return ""
    }

    private fun guessReplyCount(container: Element): Int =
        container.select("a, span")
            .mapNotNull { it.ownText().trim().toIntOrNull() }
            .maxOrNull() ?: 0

    private val IGNORE_WORDS = setOf(
        "关注", "看贴", "精华", "置顶", "楼主", "回复", "分享", "举报",
        "收藏", "操作", "更多", "搜索", "取消", "打开贴吧", "立即打开"
    )
}
