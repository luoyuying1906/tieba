package com.example.tiebasearch.data.parser

import com.example.tiebasearch.util.TimeFormat
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * /mo/q/m 移动版页面的解析器。
 *
 * ================== 本版修掉的两个 bug（都是同一个根因）==================
 *
 * 【根因】上一版用「头像」作锚点向上找楼层容器，代码是「找到第一个同时含作者链接
 * 和时间的祖先就停」。但贴吧移动版的 DOM 里，头像 / 作者名 / 等级 / 时间**本来就在
 * 同一个「作者块」里**，所以走两层就撞上作者块然后停了：
 *
 *     <div class="p_author">                 ← 容器错误地停在这里
 *       <a href="javascript:;"><img src=".../sys/portrait/xxx.jpg"></a>
 *       <a href="javascript:;">贴吧用户_7N4WP81</a>
 *       <span>6</span> <span>9-25 22:27</span>
 *     </div>
 *     <div class="d_post_content">正文根本不在这里面</div>
 *
 * 由此同时产生两个症状：
 *   1. **正文空白** —— 作者块里没有正文，退化策略只能捡到作者名或啥都捡不到
 *   2. **人人都是楼主** —— 旧判定有一条 `pid == null → isOp`，而举报链接
 *      `/mo/q/postreport?...&pid=` 在**楼层**里、不在作者块里，
 *      于是 pid 永远是 null，每一层都被判成楼主
 *
 * 【本版修法】
 *   - **反过来锚定正文元素**：先找 `d_post_content` 这类正文标签，再从它向上找楼层容器。
 *     这样正文一定在容器里（不可能空白），而从正文往上找到的容器也必然包含举报链接
 *     （pid 正确 → 楼主判定不再被污染）。
 *   - **楼主判定彻底重写**：不再看 pid、不再 `contains("楼主")`（正文里出现「楼主」
 *     二字就会误判！），改成 **以楼主的作者名为准，同名才算楼主**。
 *
 * ⚠️ 这里**没有任何针对某个吧的特殊逻辑** —— 选择器和判定规则都是贴吧全网通用的，
 *    所以对 yy小说吧 / 哀伤雪刃吧 / 单女主吧 / 起点吧 / 无女主吧 一视同仁。
 *
 * ================== 稳定锚点（来自真实渲染数据）==================
 *   1. 每层都有头像：src 含 `/sys/portrait/`
 *   2. 作者名是 `<a href="javascript:;">作者名</a>`（头像链接文本为空，可区分）
 *   3. 回复层有 `/mo/q/postreport?...&pid=xxx`（pid = 楼层 id）
 *   4. 时间符合 TimeFormat 里那 4 种格式
 *   5. 广告块既没有作者锚点也没有时间 → 被「必须有作者 + 必须有时间」硬性过滤掉
 */
object MoHtmlParser {

    private val TID_RE = Regex("""/p/(\d+)""")
    private val PID_RE = Regex("""[?&]pid=(\d+)""")
    private val FLOOR_NO_RE = Regex("""(\d{1,6})\s*楼""")

    /** 头像选择器 */
    private const val PORTRAIT_SELECTOR = "img[src*=\"/sys/portrait/\"], img[src*=\"portrait\"]"

    /**
     * 正文元素的 class 片段，**按特异性从高到低排列**。
     * 同一层里若同时命中 `p_content`（外层包裹）和 `d_post_content`（真正正文），
     * 靠这个顺序优先取后者。
     */
    private val CONTENT_HINTS = listOf("d_post_content", "post_content", "p_content")

    /** 贴吧网页里的广告 / 无意义提示 */
    private val AD_PHRASES = listOf(
        "前往下载", "打开贴吧App", "打开贴吧APP", "立即打开", "下载客户端",
        "打开百度APP", "打开手百APP", "打开APP", "阅读全文", "继续访问触屏版",
        "年轻人的潮流文化社区", "使用百度前必读", "打开贴吧App，查看全部",
        "打开APP查看", "立即下载"
    )

    /** 纯 UI 文字，不该被当成正文，也不该被当成作者名 */
    private val NOISE_WORDS = setOf(
        "关注", "看贴", "精华", "置顶", "楼主", "回复", "分享", "举报", "收藏",
        "操作", "更多", "搜索", "取消", "完成", "登录", "注册", "反馈", "回帖",
        "打开贴吧", "立即打开", "前往下载", "只看楼主", "新浪微博", "客户端",
        "设置精华贴", "跳页", "赞", "来自客户端"
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
        val avatarUrl: String?,
        val epochSeconds: Long?,
        /**
         * 真正的楼主（含楼主本人的回复层）。
         * 由 parseFloors 末尾统一判定：以楼主的作者名为准、同名才算，
         * 并且优先采用「带精确『楼主』角标」那一层的作者名。详见 parseFloors 里的注释。
         */
        val isOp: Boolean,
        /**
         * 楼层号。
         * ⚠️ 移动版里作者名后面那个数字（如「贴吧用户_7N4WP81 6」）是**吧内等级**，
         * 不是楼层号 —— 楼主出现两次、两次都是同一个数字，足以证明。
         * 所以优先取网页里明确写着「N楼」的文本，取不到就用解析顺序编号。
         */
        val floorNumber: Int?
    )

    // ================================================================ 楼层解析

    /**
     * 解析帖子楼层页，输出结构化楼层列表。
     *
     * 主策略：**以正文元素为锚点**，向上找楼层容器。
     * 兜底：一个正文元素都没找到时，退回头像锚点（但会走「头像不跨层」的边界规则
     *       找到完整楼层，而不是停在作者块）。
     *
     * @return 楼层列表，按页面顺序。**解析不出来就返回空列表，绝不返回垃圾**。
     */
    fun parseFloors(html: String, tid: String): List<Floor> {
        val doc = Jsoup.parse(html)
        val built = ArrayList<Pair<Element, Floor>>()

        // ---------- 主策略：正文元素 → 向上找楼层 ----------
        // Triple = (正文元素, 正文文本, 特异性优先级)，优先级越小越优先
        val picks = LinkedHashMap<Element, Triple<Element, String, Int>>()

        for ((priority, hint) in CONTENT_HINTS.withIndex()) {
            doc.select("[class*=\"$hint\"]").forEach { el ->
                val text = el.text().trim()
                if (text.length < 2) return@forEach

                // 从正文元素向上找：**第一个**同时含作者链接和时间的祖先就是本层。
                // 不需要「不跨层」的边界判断 —— 因为起点就是正文元素，
                // 找到的任何祖先都必然把正文包在里面（正文不可能丢）。
                val container = findContentFloorAncestor(el) ?: return@forEach

                val cur = picks[container]
                val better = cur == null ||
                    priority < cur.third ||
                    (priority == cur.third && text.length > cur.second.length)
                if (better) picks[container] = Triple(el, text, priority)
            }
        }

        picks.forEach { (container, pick) ->
            val floor = buildFloor(
                container = container,
                presetContent = pick.second,
                tid = tid,
                sequence = built.size + 1
            ) ?: return@forEach
            built += container to floor
        }

        // ---------- 兜底：没有正文元素，只能靠头像定位 ----------
        if (built.isEmpty()) {
            val claimed = mutableListOf<Element>()
            doc.select(PORTRAIT_SELECTOR).forEach { img ->
                val container = findFloorContainerFromPortrait(img) ?: return@forEach
                val already = claimed.any { c ->
                    c === container || container.parents().any { p -> p === c }
                }
                if (already) return@forEach
                claimed += container

                val floor = buildFloor(
                    container = container,
                    presetContent = null,
                    tid = tid,
                    sequence = built.size + 1
                ) ?: return@forEach
                built += container to floor
            }
        }

        if (built.isEmpty()) return emptyList()

        // ---------- 楼主识别（关键修复）----------
        // 规则：以「带精确『楼主』角标」那一层的作者名为准；没有角标就用第一层的作者名。
        // 然后只有**同名的层**才算楼主。
        //
        // 这样做的意义：
        //   - 正文里出现「楼主」二字不会再误判（旧版用 contains("楼主")，这是主要误判源）
        //   - pid 缺失也不会再误判（旧版 `pid == null → isOp`，是作者块 bug 的连带伤害）
        //   - 楼主本人的回复层**照常标楼主** —— 这是贴吧自己的惯例，是正确的
        val opName = built.firstOrNull { hasExactOpBadge(it.first) }
            ?.second?.authorName?.takeIf { it.isNotBlank() }
            ?: built.first().second.authorName

        return built.map { (container, floor) ->
            val isOp = hasExactOpBadge(container) ||
                (opName.isNotBlank() && floor.authorName.isNotBlank() && floor.authorName == opName)
            floor.copy(isOp = isOp)
        }
    }

    /**
     * 主策略的容器查找：从**正文元素**向上，找到第一个同时含作者链接和时间的祖先。
     * 因为起点是正文元素，找到的容器必然包含正文 —— 这是「正文不再空白」的保证。
     */
    private fun findContentFloorAncestor(contentEl: Element): Element? {
        var current: Element? = contentEl.parent()
        var depth = 0
        while (depth < 12) {
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
     * 兜底策略的容器查找：从头像向上，找到**完整的一层**。
     *
     * 关键是「不跨层」的边界规则：一个楼层里只应该有一张头像。
     * 一旦某祖先含 2 张以上头像，说明它已经把下一层也包进来了 → 停，上一轮候选就是本层。
     *
     * 这条规则正是用来修「停在作者块」的：头像 → 作者块（只有1张头像，但继续往上）
     * → 楼层 div（仍只有1张头像，且含作者+时间 → 记为候选）→ 再往上含2张 → 停。
     */
    private fun findFloorContainerFromPortrait(img: Element): Element? {
        var current: Element? = img.parent()
        var lastGood: Element? = null
        var depth = 0
        while (depth < 12) {
            val el = current ?: break

            // 边界：头像数 > 1 说明跨层了
            if (el.select(PORTRAIT_SELECTOR).size > 1) break

            if (findAuthorAnchor(el) != null && TimeFormat.parseToEpochSeconds(el.text()) != null) {
                lastGood = el
            }
            current = el.parent()
            depth++
        }
        return lastGood
    }

    private fun buildFloor(
        container: Element,
        presetContent: String?,
        tid: String,
        sequence: Int
    ): Floor? {
        val authorAnchor = findAuthorAnchor(container)
        val authorName = authorAnchor?.text()?.trim().orEmpty()

        val epoch = TimeFormat.parseToEpochSeconds(container.text())

        // 硬性门槛：没作者、没时间 → 不是楼层（广告块就死在这里）
        if (authorName.isBlank() || epoch == null) return null

        val pid = container.select("a[href*=\"postreport\"]").firstOrNull()
            ?.let { PID_RE.find(it.attr("href"))?.groupValues?.get(1) }

        // 正文：主策略直接给（最准）；兜底才需要从容器里反推
        val raw = presetContent ?: extractContent(container, authorAnchor)
        val content = cleanContent(raw)

        if (content.isBlank() || isAdNoise(content)) return null
        // 正文等于作者名 = 提取失败（作者块那类容器会这样），宁可丢掉也别显示垃圾
        if (content == authorName) return null

        val avatarUrl = container.select(PORTRAIT_SELECTOR)
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
            // 真正的楼主判定统一在 parseFloors 末尾做，这里先给 false
            isOp = false,
            floorNumber = explicitFloorNo ?: sequence
        )
    }

    // ================================================================ 身份识别

    /**
     * 精确「楼主」角标：只有某个元素的**直接文本恰好等于「楼主」**才算。
     *
     * 为什么不用 `container.text().contains("楼主")`？
     * 因为正文里出现「楼主说得对」这种句子时也会命中 —— 这正是「普通回复被错标楼主」的
     * 主要误判源之一。
     */
    private fun hasExactOpBadge(scope: Element): Boolean =
        scope.select("a, span, div, i, em, b").any { it.ownText().trim() == "楼主" }

    /**
     * 作者名链接：`href="javascript:;"` 且文本非空、够短、不是 UI 词。
     * 头像那个 `<a>` 的 text() 是空字符串，所以不会误判成作者。
     */
    private fun findAuthorAnchor(scope: Element): Element? = authorAnchors(scope).firstOrNull()

    private fun authorAnchors(scope: Element): List<Element> =
        scope.select("a[href^=javascript]").filter { a ->
            val t = a.text().trim()
            t.isNotEmpty() &&
                t.length <= 24 &&
                t !in NOISE_WORDS &&
                // 只排除「纯数字」的链接（那是等级/楼层号）。
                // 千万别写成「含数字就排除」—— 实测大量贴吧用户名带数字
                // （gmn0608、利利118、a357489488、贴吧用户_7N4WP81），那样会把真人全过滤掉
                t.toIntOrNull() == null &&
                TimeFormat.parseToEpochSeconds(t) == null
        }

    // ================================================================ 正文提取（仅兜底用）

    private fun extractContent(container: Element, authorAnchor: Element?): String {
        val containerText = container.text().trim()

        // 策略 A：class 提示。排除「文本等于整块」的候选 —— 那是包裹层，不是正文
        val valid = ArrayList<String>()
        for (hint in CONTENT_HINTS) {
            container.select("[class*=\"$hint\"]").forEach { el ->
                val t = el.text().trim()
                if (t.length >= 2 && t.length < containerText.length) valid += t
            }
        }
        valid.maxByOrNull { it.length }?.let { return it }

        // 策略 B：ownText 启发式。跳过作者链接本身，也跳过包裹层
        return container.select("*")
            .asSequence()
            .filter { it !== authorAnchor }
            .map { el -> el to el.ownText().trim() }
            .filter { (el, own) ->
                own.length >= 4 &&
                    // ownText 要占该元素全部文本的一半以上，确保它不是又一个包裹层
                    own.length * 2 >= el.text().trim().length &&
                    el.text().trim() != containerText &&
                    TimeFormat.parseToEpochSeconds(own) == null &&
                    own !in NOISE_WORDS &&
                    !isAdNoise(own)
            }
            .maxByOrNull { it.second.length }
            ?.second
            .orEmpty()
    }

    // ================================================================ 清洗

    /** 整块就是广告 / UI 提示 → 丢弃 */
    private fun isAdNoise(text: String): Boolean {
        val t = text.replace(Regex("\\s+"), "")
        if (t.length < 2) return true
        if (t in NOISE_WORDS) return true
        val adLen = AD_PHRASES.filter { t.contains(it) }.sumOf { it.length }
        return adLen * 2 > t.length
    }

    private fun cleanContent(raw: String): String {
        var t = raw
        AD_PHRASES.forEach { p -> t = t.replace(p, "") }
        t = t.replace(Regex("[ \\t\\u00A0]+"), " ")
        t = t.replace(Regex("\\n{3,}"), "\n\n")
        return t.trim()
    }

    private fun normalizeUrl(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        return when {
            s.startsWith("//") -> "https:$s"
            s.startsWith("http") -> s
            else -> null
        }
    }

    // ================================================================ 结构诊断

    /**
     * 结构诊断（调试用）。
     *
     * 解析靠的是结构特征而不是写死的 class 名，万一贴吧改版导致解析不到楼层，
     * 光知道「解析不出来」没法定位。这个方法输出「出现次数最多的 class + 次数」
     * 以及各锚点数量 —— 楼层容器一定会在里面高频出现，一眼就能看出该用哪个选择器。
     *
     * 输出很短（约 25 行），可以长按复制发出来。
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
        sb.append("portrait img 数 = ").append(doc.select(PORTRAIT_SELECTOR).size).append('\n')
        sb.append("postreport 链接数 = ")
            .append(doc.select("a[href*=\"postreport\"]").size).append('\n')
        CONTENT_HINTS.forEach { hint ->
            sb.append("正文候选 [").append(hint).append("] 数 = ")
                .append(doc.select("[class*=\"$hint\"]").size).append('\n')
        }
        sb.append("出现最多的 class：\n")
        counts.entries.sortedByDescending { it.value }.take(topN).forEach { (c, n) ->
            sb.append("  ").append(c).append(" × ").append(n).append('\n')
        }
        return sb.toString()
    }

    // ================================================================ 列表页（兜底搜索路径）

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

    private fun findBriefContainer(start: Element): Element? {
        var current: Element? = start
        var depth = 0
        while (depth < 6) {
            val el = current ?: break
            val t = el.text()
            val hasPortrait = el.select(PORTRAIT_SELECTOR).isNotEmpty()
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
