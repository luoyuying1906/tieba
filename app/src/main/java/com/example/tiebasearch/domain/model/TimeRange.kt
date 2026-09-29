package com.example.tiebasearch.domain.model

/**
 * 时间过滤范围（需求五）。
 *
 * ⚠️ 技术前提：贴吧搜索接口**不接受时间参数**，它只按关键词做全网相关性排序。
 * 所以时间过滤只能在客户端用每条结果自带的 create_time（Unix 秒）来筛。
 *
 * 代价：符合时间条件、但没排进前面几页的帖子会漏掉。
 * 靠 TiebaRepository 的「前瞻翻页」机制缓解（一次最多扫 8 页全网结果）。
 *
 * 按用户要求：选项为 不限 / 近1年 / 近3年 / 近5年，**默认近3年**。
 */
enum class TimeRange(val label: String, private val days: Long?) {

    ALL("不限", null),
    YEAR_1("近1年", 365L),
    YEAR_3("近3年", 1095L),
    YEAR_5("近5年", 1825L);

    /**
     * 筛选起点（Unix 秒）；返回 null 表示不限时间。
     * 注释里带上 nowMillis 参数是为了可测试，正常调用不用传。
     */
    fun sinceEpochSeconds(nowMillis: Long = System.currentTimeMillis()): Long? =
        days?.let { nowMillis / 1000L - it * 86_400L }

    companion object {
        /** 用户从未选择过时的默认值：近3年 */
        val DEFAULT: TimeRange = YEAR_3

        /** 从持久化的名字还原；名字不认得了就退回默认值 */
        fun fromName(name: String?): TimeRange =
            TimeRange.values().firstOrNull { it.name == name } ?: DEFAULT
    }
}
