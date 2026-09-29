package com.example.tiebasearch.domain.model

/**
 * 检索范围：只允许从这几个吧抓取帖子。
 *
 * ⚠️ 重要：贴吧搜索接口返回的 `forum_name` **不带「吧」字**
 * （实测返回的是「反激女」「文学」「李毅」这种）。
 * 所以这里存的是**不带「吧」的规范名**，比对时也要去掉「吧」再比。
 */
object Forums {

    /** 允许检索的吧（不带「吧」字） */
    val ALL: List<String> = listOf(
        "yy小说",
        "哀伤雪刃",
        "单女主",
        "起点",
        "无女主"
    )

    /** 界面上显示用：yy小说吧 */
    fun display(apiName: String): String = "${apiName}吧"

    /**
     * 归一化：去掉空白、去掉尾部「吧」、转小写。
     * 用户可能输入「YY小说吧」，接口返回「yy小说」，必须能对上。
     */
    fun normalize(name: String): String =
        name.trim().removeSuffix("吧").trim().lowercase()

    /** 某个吧名是否在允许范围内 */
    fun isAllowed(forumName: String, selected: Collection<String>): Boolean {
        val n = normalize(forumName)
        if (n.isEmpty()) return false
        return selected.any { normalize(it) == n }
    }
}
