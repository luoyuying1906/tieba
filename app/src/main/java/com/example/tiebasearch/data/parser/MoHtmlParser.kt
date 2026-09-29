package com.example.tiebasearch.data.parser

import com.example.tiebasearch.util.TimeFormat
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * /mo/q/m 移动版页面的解析器。
 *
 * ================== 设计依据（重要，别乱改）==================
 *
 * 我**没能拿到原始 HTML**（沙箱里所有外网代理都被 SSRF 防护挡了），
 * 但拿到了 web_fetch 渲染后的文本 —— 它保留了链接地址、图片地址和列表结构。
 * 从那份真实数据里能确认这些**稳定锚点**：
 *
 *   1. 每个楼层都有一张头像：src 里必然含 `/sys/portrait/`
 *   2. 作者名是一个 `<a href="javascript:;">作者名</a>`（头像链接文本为空，可据此区分）
 *   3. 回复层有一个举报链接：`/mo/q/postreport?fid=..&tid=..&pid=..`（pid 就是楼层 id）
 *   4. 时间符合 TimeFormat 里那 4 种格式（9-25 22:27 / 今天 21:00 / ...）
 *   5. 楼中楼（子回复）的作者链接是 `/home/main?un=xxx`，且**没有头像**
 *   6. 广告块（「前往下载」「打开贴吧App」「立即打开」）**既没有作者锚点也没有时间**
 *
 * 所以本解析器完全**不依赖具体 class 名**（贴吧的 sfrs/spb/pb_content 之类会随版本变），
 * 而是用上面这些结构特征来定位。第 6 条尤其关键：
 * **要求「必须有作者 + 必须有时间」，广告就被自然过滤掉了。**
 *
 * ================== 上一版为什么输出一大坨纯文本 ==================
 *
 * 上一版在找不到 content 类元素时会退化成 `doc.select("div")`，
 * 于是**每一个 div 都变成了一层楼**，其中最外层包裹 div 的 text() 就是整页文字，
 * 结果就是「楼主+所有回复+所有广告堆成一块」。本版**彻底删掉了这个兜底**，
 * 宁可返回空列表让 UI 明确提示，也不吐垃圾。
 */
object MoHtmlParser {

    private val TID_RE = Regex("""/p/(\d+)""")
    private val PID_RE = Regex("""[?&]pid=(\d+)""")
    private val FLOOR_NO_RE = Regex("""(\d{1,6})\s*楼""")

    /** 贴吧网页里的广告 / 无意义提示。命中过多就整块丢弃，少量出现就从正文里抠掉 */
    private val AD_PHRASES = listOf(
        "前往下载", "打开贴吧App", "打开贴吧APP", "立即打开", "下载客户端",
        "打开百度APP", "打开手百APP", "打开APP", "阅读全文", "继续访问触屏版",
        "年轻人的潮流文化社区", "使用百度前必读", "打开贴吧App，查看全部",
        "打开APP查看", "立即下载", "广告"
    )

    /** 纯 UI 文字，不该被当成正文 */
    private val NOISE_WORDS = setOf(
        "关注", "看贴", "精华", "置顶", "楼主", "回复", "分享", "举报", "收藏",
        "操作", "更多", "搜索", "取消", "完成", "登录", "注册", "反馈", "回帖",
        "打开贴吧", "立即打开", "前往下载", "只看楼主", "新浪微博", "客户端",
        "设置精华贴", "跳页"
    )

    /** 正文元素优先看这些 class 片段（命中就最准，命中不了会自动退化） */
    private val CONTENT_CLASS_HINTS = listOf(
        "d_post_content", "p_content", "post_content", "pb_content", "content"
    )

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
        /** 头像地址；列表页的 timg 包装地址也能直接用，取不到就是 null */
        val avatarUrl: String?,
        val epochSeconds: Long?,
        val isOp: Boolean,
        /**
         * 楼层号。
         * ⚠️ 贴吧移动版里作者名后面那个数字（如「贴吧用户_7N4WP81 6」）是**吧内等级**，
         * 不是楼层号 —— 楼主出现两次、两次都是同一个数字，足以证明。
         * 所以这里优先取网页里明确写着「N楼」的文本，取不到就用解析顺序编号。
         */
        val floorNumber: Int?
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

            val container = findBriefContainer(a) ?: continue
            val text = container.text()
            val title = a.text().trim().ifBlank { a.attr("title").trim() }
            if (title.isBlank()) continue

            out[tid] = ThreadBrief(
                tid = tid,
                title = title,
                authorName = guessAuthorInBrief(container, title, forumName),
                timeText = text,
                epochSeconds = TimeFormat.parseToEpochSeconds(text),
                replyCount = guessReplyCount(container)
            )
        }
        return out.values.toList()
    }

    /**
     * 解析「帖子楼层页」，输出结构化楼层列表。
     *
     * 流程：
     *   1. 以**头像**为锚点，向上找到同时含「作者链接 + 时间」的最小祖先 = 一个楼层
     *   2. 同一个祖先被多个头像命中时只算一次（用 claimed 集合去重）
     *   3. 从楼层里分别抽出作者、pid、时间、楼层号、正文
     *   4. 正文为空 或 判定为广告 的楼层直接丢弃
     *
     * @return 楼层列表，按页面顺序。**解析不出来就返回空列表，绝不返回垃圾**。
     */
    fun parseFloors(html: String, tid: String): List<Floor> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<Floor>()
        val claimed = mutableListOf<Element>()

        val portraits = doc.select("img[src*=\"/sys/portrait/\"], img[src*=\"portrait\"]")

        for (img in portraits) {
            val container = findFloorContainer(img) ?: continue

            // 已经被归入某一层（含父层）就跳过，避免父子块重复计入
            val alreadyClaimed = claimed.any { c ->
                c === container || container.parents().any { it === c }
            }
            if (alreadyClaimed) continue
            claimed += container

            val floor = buildFloor(container, tid, out.size + 1) ?: continue
            out += floor
        }

        return out
    }

    /**
     * 结构诊断（调试用）。
     *
     * 为什么需要它：解析靠的是结构特征而不是 class 名，
     * 万一贴吧改版导致解析不到楼层，光知道「解析不出来」没法定位问题。
     * 这个方法会把「出现次数最多的 class 名 + 次数」列出来 ——
     * 楼层容器一定会在里面高频出现，一眼就能看出该用哪个选择器。
     *
     * 输出很短（约 25 行），可以直接复制发给我。
     */
    fun structureFingerprint(html: String, topN: Int = 25): String {
        val doc = Jsoup.parse(html)
        val counts = HashMap<String, Int>()
        doc.select("[class]").forEach { el ->
            el.classNames().forEach { c -> counts[c] = (counts[c] ?: 0) + 1 }
        }

        val sb = StringBuilder()
        sb.append("HTML 长度 = ").append(html.length).append('\n')
        sb.append("identified floors = ").append(parseFloors(html, "0").size).append('\n')
        sb.append("portrait img 数 = ")
            .append(doc.select("img[src*=\"/sys/portrait/\"], img[src*=\"portrait\"]").size)
            .append('\n')
        sb.append("postreport 链接数 = ")
            .append(doc.select("a[href*=\"postreport\"]").size)
            .append('\n')
        sb.append("出现最多的 class：\n")
        counts.entries.sortedByDescending { it.value }.take(topN).forEach { (c, n) ->
            sb.append("  ").append(c).append(" × ").append(n).append('\n')
        }
        return sb.toString()
    }

    // ------------------------------------------------------------ 楼层定位

    /**
     * 从头像向上找到「一个楼层」的容器：
     * 必须同时含有 作者链接(`<a href="javascript:;">短文本</a>`) 和 可解析的时间。
     * 这两条同时满足，基本只可能是楼层本身，不可能是广告块。
     */
    private fun findFloorContainer(img: Element): Element? {
        var current: Element? = img.parent()
        var depth = 0
        while (depth < 8) {
            val el = current ?: break
            if (findAuthorAnchor(el) != null && TimeFormat.parseToEpochSeconds(el.text()) != null) {
                return el
            }
            current = el.parent()
            depth++
        }
        return null
    }

    /**
     * 作者名链接：`href="javascript:;"` 且文本非空、够短、不是 UI 词。
     * 头像那个 `<a>` 的 text() 是空字符串，所以不会误判成作者。
     */
    private fun findAuthorAnchor(scope: Element): Element? =
        scope.select("a[href^=javascript]").firstOrNull { a ->
            val t = a.text().trim()
            t.isNotEmpty() &&
                t.length <= 24 &&
                t !in NOISE_WORDS &&
                // 只排除「纯数字」的链接（那是等级/楼层号），
                // 千万别写成「含数字就排除」—— 实测大量贴吧用户名带数字
                // （gmn0608、利利118、a357489488、贴吧用户_7N4WP81），那样会把真人全过滤掉
                t.toIntOrNull() == null &&
                TimeFormat.parseToEpochSeconds(t) == null
        }

    private fun buildFloor(container: Element, tid: String, sequence: Int): Floor? {
        val authorAnchor = findAuthorAnchor(container)
        val authorName = authorAnchor?.text()?.trim().orEmpty()
        val epoch = TimeFormat.parseToEpochSeconds(container.text())

        // 硬性门槛：没作者、没时间 → 不是楼层（广告块就死在这里）
        if (authorName.isBlank() || epoch == null) return null

        val pid = container.select("a[href*=\"postreport\"]").firstOrNull()
            ?.let { PID_RE.find(it.attr("href"))?.groupValues?.get(1) }

        val content = extractContent(container)
        if (content.isBlank() || isAdNoise(content)) return null

        // 楼主判定：页面里标了「楼主」，或这一层没有举报链接（主楼没有 pid）
        val isOp = container.text().contains("楼主") || pid == null

        val avatarUrl = container.select("img[src*=\"/sys/portrait/\"], img[src*=\"portrait\"]")
            .firstOrNull()?.attr("src")?.let { normalizeUrl(it) }

        val explicitFloorNo = FLOOR_NO_RE.find(container.text())
            ?.groupValues?.get(1)?.toIntOrNull()

        return Floor(
            tid = tid,
            pid = pid,
            content = content,
            authorName = authorName,
            avatarUrl = avatarUrl,
            epochSeconds = epoch,
            isOp = isOp,
            floorNumber = explicitFloorNo ?: sequence
        )
    }

    /** 头像地址在页面里可能是协议相对（//gss3.bdstatic.com/...），补上 https: 才能加载 */
    private fun normalizeUrl(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        return when {
            s.startsWith("//") -> "https:$s"
            s.startsWith("http") -> s
            else -> null
        }
    }

    /**
     * 抽正文。两级策略：
     *
     *   策略 1：找 class 里带 content 的元素。
     *           ⚠️ 必须排除「文本长度等于整块文本」的候选 ——
     *           否则会命中包裹层，又变成一整坨垃圾（这正是上一版的病根）。
     *   策略 2：找块内「自己直接拥有的文本(ownText)」够长、且占自身文本一半以上的元素。
     *           ownText 这个特性天生会跳过包裹元素，比按 class 猜稳得多。
     */
    private fun extractContent(container: Element): String {
        val containerText = container.text().trim()

        // ---- 策略 1：class 提示 ----
        val byClass = ArrayList<String>()
        for (hint in CONTENT_CLASS_HINTS) {
            if (container.classNames().any { it.contains(hint, ignoreCase = true) }) {
                byClass += containerText
            }
            container.select("[class*=\"$hint\"]").forEach { byClass += it.text().trim() }
        }
        // 只接受「比整块短」的候选：等于整块文本说明命中的是包裹层，那就是上一版吐垃圾的病根
        val valid = byClass.filter { it.length >= 2 && it.length < containerText.length }
        if (valid.isNotEmpty()) {
            return cleanContent(valid.maxByOrNull { it.length }!!)
        }

        // ---- 策略 2：ownText 启发式 ----
        val candidates = container.select("*")
            .asSequence()
            .map { el -> el to el.ownText().trim() }
            .filter { (el, own) ->
                own.length >= 4 &&
                    // ownText 要占该元素全部文本的一半以上，确保它不是又一个包裹层
                    own.length * 2 >= el.text().trim().length &&
                    TimeFormat.parseToEpochSeconds(own) == null &&
                    own !in NOISE_WORDS &&
                    !isAdNoise(own)
            }
            .sortedByDescending { it.second.length }
            .toList()

        candidates.firstOrNull()?.let { return cleanContent(it.second) }

        return ""
    }

    // ------------------------------------------------------------ 清洗

    /** 整块就是广告 / UI 提示 → 丢弃 */
    private fun isAdNoise(text: String): Boolean {
        val t = text.replace(Regex("\\s+"), "")
        if (t.length < 2) return true
        if (t in NOISE_WORDS) return true
        // 广告词占了一半以上篇幅，就认为这块是广告
        val adLen = AD_PHRASES.filter { t.contains(it) }.sumOf { it.length }
        return adLen * 2 > t.length
    }

    /** 抠掉正文里零散夹带的广告词，并规整空白 */
    private fun cleanContent(raw: String): String {
        var t = raw
        AD_PHRASES.forEach { p -> t = t.replace(p, "") }
        t = t.replace(Regex("[ \\t\\u00A0]+"), " ")
        t = t.replace(Regex(" {2,}"), " ")
        t = t.replace(Regex("\\n{3,}"), "\n\n")
        return t.trim()
    }

    // ------------------------------------------------------------ 列表页（兜底路径）

    private fun findBriefContainer(start: Element): Element? {
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

    private fun guessAuthorInBrief(container: Element, title: String, forumName: String): String {
        val candidates: Elements = container.select("a, span, div")
        for (el in candidates) {
            val c = el.ownText().trim()
            if (c.isBlank()) continue
            if (c == title) continue
            if (c.length > 24) continue
            if (c in NOISE_WORDS) continue
            if (c == forumName) continue
            if (c.all { it.isDigit() || it == ':' }) continue
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
}
