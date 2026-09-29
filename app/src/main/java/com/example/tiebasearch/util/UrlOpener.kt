package com.example.tiebasearch.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 用系统浏览器打开链接。
 *
 * 设计要点：**只做用户点击后的跳转**，绝不自动打开——
 * 用户明确说过「跳转必须是可选的，由用户自己点击决定」。
 */
object UrlOpener {

    fun open(context: Context, url: String) {
        if (url.isBlank()) {
            Toast.makeText(context, "没有可打开的链接", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            // context 有可能不是 Activity，加这个 flag 更保险
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // 极少数设备（或无浏览器环境）会走到这里，不能让 App 崩
            Toast.makeText(context, "没有找到可以打开网页的应用", Toast.LENGTH_SHORT).show()
        }
    }
}
