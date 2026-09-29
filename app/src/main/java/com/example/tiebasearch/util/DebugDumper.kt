package com.example.tiebasearch.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 接口自检工具 —— 这个类很不起眼，但它是这个项目**最该有的东西**。
 *
 * 为什么：爬虫项目的死因几乎永远是「对方页面/接口改了，而你不知道改成什么样」。
 * 有了它，出问题时不需要连电脑、不需要抓包：
 *   在 App 里点一下「导出原始响应」，然后去
 *   内部存储/Android/data/com.example.tiebasearch/files/dump/ 拿文件，
 *   一眼就能看出是接口结构变了、还是被风控了。
 */
object DebugDumper {

    private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)

    /** 导出目录：无需存储权限，卸载即清理 */
    fun dumpDir(context: Context): File =
        File(context.getExternalFilesDir(null), "dump").apply { if (!exists()) mkdirs() }

    /**
     * 保存一份原始响应。
     * @return 落盘后的文件；失败返回 null
     */
    fun dump(context: Context, tag: String, content: String): File? = runCatching {
        val safeTag = tag.replace(Regex("""[^A-Za-z0-9_\-]"""), "_").take(40)
        val file = File(dumpDir(context), "${stamp.format(Date())}_$safeTag.txt")
        file.writeText(content, Charsets.UTF_8)
        file
    }.getOrNull()

    /** 列出已导出的文件，按时间倒序 */
    fun list(context: Context): List<File> =
        dumpDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
}
