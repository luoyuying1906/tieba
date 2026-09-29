package com.example.tiebasearch.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 贴吧的时间有四种形态，都得认：
 *   1. Unix 秒时间戳     —— JSON 接口给的（最准，例如 1790439598）
 *   2. 2026-10-10 21:00 —— 完整日期
 *   3. 9-25 22:27       —— 缺年份（移动版 HTML 列表页大量使用）
 *   4. 今天 21:00 / 21:10 —— 相对日期
 */
object TimeFormat {

    private val fullDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val fullDateNoTime = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val shortDate = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
    private val shortDateNoTime = SimpleDateFormat("MM-dd", Locale.CHINA)
    private val clock = SimpleDateFormat("HH:mm", Locale.CHINA)

    init {
        val tz = TimeZone.getTimeZone("Asia/Shanghai")
        listOf(fullDate, fullDateNoTime, shortDate, shortDateNoTime, clock).forEach {
            it.timeZone = tz
        }
    }

    private val DATE_TIME_RE =
        Regex("""\d{4}-\d{1,2}-\d{1,2}(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?""")
    private val SHORT_DATE_RE =
        Regex("""\d{1,2}-\d{1,2}(?:\s+\d{1,2}:\d{2})?""")
    private val RELATIVE_RE =
        Regex("""(今天|昨天|前天)?\s*(\d{1,2}:\d{2})""")

    /**
     * 从一段文本里揪出时间并转成 Unix 秒。
     * 注意顺序：完整日期 → 短日期 → 相对时间。反了会把 "2026-10-10" 误判成 "10-10"。
     */
    fun parseToEpochSeconds(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val t = text.trim()

        DATE_TIME_RE.find(t)?.value?.let { raw ->
            return runCatching {
                val hasTime = raw.contains(":")
                val fmt = if (hasTime) fullDate else fullDateNoTime
                fmt.parse(raw)?.time?.div(1000)
            }.getOrNull()
        }

        SHORT_DATE_RE.find(t)?.value?.let { raw ->
            return runCatching {
                val hasTime = raw.contains(":")
                val fmt = if (hasTime) shortDate else shortDateNoTime
                val parsed = fmt.parse(raw) ?: return@runCatching null
                // 补年份。规则：如果解析出来的月份比当前月份大，说明是去年的帖子
                // （否则「12-30」在 1 月会被错判成未来时间）。
                val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
                val parsedCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
                    time = parsed
                }
                cal.set(Calendar.MONTH, parsedCal.get(Calendar.MONTH))
                cal.set(Calendar.DAY_OF_MONTH, parsedCal.get(Calendar.DAY_OF_MONTH))
                if (hasTime) {
                    cal.set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
                    cal.set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
                } else {
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                }
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (cal.timeInMillis > System.currentTimeMillis() + 86_400_000L) {
                    cal.add(Calendar.YEAR, -1)
                }
                cal.timeInMillis / 1000
            }.getOrNull()
        }

        RELATIVE_RE.find(t)?.let { m ->
            val dayWord = m.groupValues[1]
            val hhmm = m.groupValues[2]
            return runCatching {
                val parts = hhmm.split(":")
                val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
                when (dayWord) {
                    "昨天" -> cal.add(Calendar.DAY_OF_YEAR, -1)
                    "前天" -> cal.add(Calendar.DAY_OF_YEAR, -2)
                    else -> Unit // 今天 或 没写
                }
                cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                cal.set(Calendar.MINUTE, parts[1].toInt())
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                // 没写「今天/昨天」但时间比现在晚 → 那是昨天的
                if (dayWord.isBlank() && cal.timeInMillis > System.currentTimeMillis() + 60_000L) {
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                }
                cal.timeInMillis / 1000
            }.getOrNull()
        }

        return null
    }

    /** 界面上显示：今天 21:00 / 昨天 21:00 / 09-25 22:27 / 2024-01-01 12:00 */
    fun humanize(epochSeconds: Long?): String {
        if (epochSeconds == null || epochSeconds <= 0) return "时间未知"
        val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        val then = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
            timeInMillis = epochSeconds * 1000
        }

        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val hhmm = clock.format(Date(epochSeconds * 1000))

        val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
        val yearDiff = now.get(Calendar.YEAR) - then.get(Calendar.YEAR)
        if (yearDiff == 0 && dayDiff == 0) return "今天 $hhmm"
        if (yearDiff == 0 && dayDiff == 1) return "昨天 $hhmm"
        if (yearDiff == 0 && dayDiff == 2) return "前天 $hhmm"

        return if (sameYear) {
            SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).apply {
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }.format(Date(epochSeconds * 1000))
        } else {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply {
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }.format(Date(epochSeconds * 1000))
        }
    }
}
