package com.asmr.player.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * "收听日" 的口径工具。
 *
 * 由于 app 受众用户的收听行为通常集中在夜间，凌晨的收听在语义上仍属于"前一天"。
 * 因此我们把每日的重置时间点定为设备时区的 **凌晨 5 点**：
 * 例如 07-20 03:00 的收听归属于 07-19，07-20 05:00 之后的收听归属于 07-20。
 *
 * 实现方式是把时间戳整体回拨 [RESET_HOUR] 小时后，再按设备本地时区取日期。
 * 所有方法均为纯函数，便于单元测试。
 */
object ListeningDay {

    /** 每日重置时间点（设备本地时区，24 小时制）。固定为凌晨 5 点。 */
    const val RESET_HOUR = 5

    private const val RESET_OFFSET_MS = RESET_HOUR * 60L * 60L * 1000L

    private val dateFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

    /** 当前收听日（"yyyy-MM-dd"）。 */
    fun currentDate(nowMs: Long = System.currentTimeMillis()): String = dateOf(nowMs)

    /** 给定时间戳所属的收听日（"yyyy-MM-dd"），按设备时区、凌晨 5 点重置。 */
    fun dateOf(epochMs: Long): String {
        return dateFormat.format(Date(epochMs - RESET_OFFSET_MS))
    }

    /**
     * 收听日字符串（"yyyy-MM-dd"）对应的 00:00（即该收听日真正开始的 05:00）的时间戳。
     * 主要用于热度图中把日期映射到网格坐标。返回该收听日窗口的起始 epoch 毫秒。
     */
    fun startOfDate(date: String): Long {
        val parsed = dateFormat.parse(date) ?: return 0L
        // parsed 是回拨 5 小时后的 00:00；真实起点需要再加回 5 小时。
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.time = parsed
        cal.add(Calendar.MILLISECOND, RESET_OFFSET_MS.toInt())
        return cal.timeInMillis
    }

    /**
     * 生成从 [startDate]（含）到 [endDate]（含）之间、以收听日为单位的连续日期列表，升序。
     * 若解析失败或区间非法则返回空列表。
     */
    fun datesBetween(startDate: String, endDate: String): List<String> {
        val start = dateFormat.parse(startDate) ?: return emptyList()
        val end = dateFormat.parse(endDate) ?: return emptyList()
        if (start.after(end)) return emptyList()
        val result = ArrayList<String>()
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.time = start
        val fmt = dateFormat
        while (!cal.time.after(end)) {
            result.add(fmt.format(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return result
    }

    /**
     * 收听日对应的"星期几"，周日=0 … 周六=6。用于热度图纵向 7 行排布。
     */
    fun dayOfWeekIndex(date: String): Int {
        val parsed = dateFormat.parse(date) ?: return 0
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.time = parsed
        // Calendar.SUNDAY == 1 … Calendar.SATURDAY == 7
        return cal.get(Calendar.DAY_OF_WEEK) - 1
    }

    /**
     * 返回从今天往前推 [days] 天的收听日（含今天）作为区间起点日期字符串。
     * 例如 days = 365 表示最近一年。
     */
    fun dateDaysAgo(days: Int, nowMs: Long = System.currentTimeMillis()): String {
        val todayStart = dateFormat.parse(dateOf(nowMs)) ?: return dateOf(nowMs)
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.time = todayStart
        cal.add(Calendar.DAY_OF_MONTH, -days)
        return dateFormat.format(cal.time)
    }
}
