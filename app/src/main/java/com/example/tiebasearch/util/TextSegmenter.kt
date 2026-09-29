package com.example.tiebasearch.util

/**
 * 长文本「智能分段」。
 *
 * 需求三要求：内容过长时不要堆成一个大框，要分成多个展示框。
 * 分段策略（从粗到细，尽量在语义边界断开）：
 *
 *   1. 先按换行切段（作者自己分的段最准）
 *   2. 段落仍然超长 → 按中文句末标点（。！？；…）切句
 *   3. 单句仍然超长（比如一段没有标点的长贴）→ 硬切
 *   4. 把短句合并回不超过上限的块，避免出现一堆碎块
 *
 * 最后只取前 [maxBoxes] 块，其余交给「展开全文」。
 */
object TextSegmenter {

    /** 句末标点 */
    private const val SENTENCE_END = "。！？；…!?;"

    data class Segmented(
        /** 分段后的文本块 */
        val boxes: List<String>,
        /** 是否还有被截掉的内容 */
        val truncated: Boolean
    )

    /**
     * @param raw            原始正文
     * @param maxCharsPerBox 每个展示框的目标字数上限
     * @param maxBoxes       列表卡片里最多展示几个框
     */
    fun segment(
        raw: String,
        maxCharsPerBox: Int = 90,
        maxBoxes: Int = 4
    ): Segmented {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return Segmented(emptyList(), false)

        val chunks = mutableListOf<String>()

        // 1) 按换行切段
        val paragraphs = text.split(Regex("\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (p in paragraphs) {
            if (p.length <= maxCharsPerBox) {
                chunks += p
            } else {
                chunks += splitLongParagraph(p, maxCharsPerBox)
            }
        }

        val truncated = chunks.size > maxBoxes
        return Segmented(chunks.take(maxBoxes), truncated)
    }

    /** 超长段落 → 先切句、再合并成不超限的块 */
    private fun splitLongParagraph(p: String, limit: Int): List<String> {
        // 2) 按句末标点切句
        val sentences = mutableListOf<String>()
        val sb = StringBuilder()
        for (ch in p) {
            sb.append(ch)
            if (SENTENCE_END.indexOf(ch) >= 0) {
                val s = sb.toString().trim()
                if (s.isNotEmpty()) sentences += s
                sb.setLength(0)
            }
        }
        if (sb.isNotEmpty()) {
            val s = sb.toString().trim()
            if (s.isNotEmpty()) sentences += s
        }

        // 3+4) 合并短句；单句超长则硬切
        val out = mutableListOf<String>()
        val cur = StringBuilder()

        for (s in sentences) {
            if (s.length > limit) {
                if (cur.isNotEmpty()) {
                    out += cur.toString()
                    cur.setLength(0)
                }
                var i = 0
                while (i < s.length) {
                    val end = minOf(i + limit, s.length)
                    out += s.substring(i, end)
                    i = end
                }
            } else if (cur.length + s.length > limit) {
                out += cur.toString()
                cur.setLength(0)
                cur.append(s)
            } else {
                cur.append(s)
            }
        }
        if (cur.isNotEmpty()) out += cur.toString()

        return out.filter { it.isNotEmpty() }
    }
}
