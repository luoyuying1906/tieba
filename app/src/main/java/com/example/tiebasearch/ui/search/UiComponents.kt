package com.example.tiebasearch.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tiebasearch.domain.model.TiebaPost
import com.example.tiebasearch.util.TextSegmenter

/**
 * 搜索结果列表与详情页共用的小组件。
 * 放在一起是为了避免两处各写一份、以后改一处漏一处。
 */

/** 圆形头像。url 为空就显示一个灰底占位圈 */
@Composable
internal fun Avatar(url: String?, size: Int = 36) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** 小标签，例如「时间 今天 21:00」「来自yy小说吧」「#3」 */
@Composable
internal fun Tag(text: String, highlight: Boolean = false) {
    val bg = if (highlight) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = text,
        fontSize = 11.sp,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

/** 头像 + 昵称 + 用户名/UID。搜索结果卡片用 */
@Composable
internal fun AuthorRow(post: TiebaPost, avatarSize: Int = 36) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(post.avatarUrl, avatarSize)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = post.authorDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    if (post.authorName.isNotBlank()) append("用户名 ${post.authorName}  ·  ")
                    append("UID ${post.authorStableId}")
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 顶部那条可关闭的提示（错误 / 中性说明共用） */
@Composable
internal fun MessageStrip(text: String, bg: Color, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            "关闭",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = 8.dp)
                .clickable(onClick = onDismiss)
        )
    }
}

/**
 * 长内容折叠（需求三）。
 *
 * 超过 [collapseThreshold] 字就先截断，配一个「展开全文」按钮，别让一层楼撑满整屏。
 * 用 rememberSaveable 记住展开状态，转屏不会又折回去。
 */
@Composable
internal fun CollapsibleText(
    text: String,
    collapseThreshold: Int = 500,
    collapsedChars: Int = 300,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 24.sp
) {
    val needCollapse = text.length > collapseThreshold
    var expanded by rememberSaveable(text) { mutableStateOf(false) }

    if (!needCollapse || expanded) {
        Text(text = text, fontSize = fontSize, lineHeight = lineHeight)
        if (needCollapse) {
            TextButton(onClick = { expanded = false }) {
                Text("收起", fontSize = 12.sp)
            }
        }
    } else {
        Text(
            text = text.take(collapsedChars).trimEnd() + "……",
            fontSize = fontSize,
            lineHeight = lineHeight
        )
        TextButton(onClick = { expanded = true }) {
            Text("展开全文（共 ${text.length} 字）", fontSize = 12.sp)
        }
    }
}

/**
 * 需求三（列表用）：长内容「智能分段」展示。
 *
 * 超过 [maxCharsPerBox] 的内容会被切成多个独立的小框，而不是堆成一大坨，
 * 排版上更接近「一段一段读」的体验。切分位置优先落在段落、句号等语义边界上，
 * 具体规则见 [TextSegmenter]。
 */
@Composable
internal fun SegmentedContent(
    text: String,
    maxCharsPerBox: Int = 90,
    maxBoxes: Int = 4
) {
    val segmented = remember(text, maxCharsPerBox, maxBoxes) {
        TextSegmenter.segment(text, maxCharsPerBox, maxBoxes)
    }

    if (segmented.boxes.isEmpty()) {
        Text(
            text = "（正文为空或需进入原帖查看）",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segmented.boxes.forEach { box ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(text = box, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }

        if (segmented.truncated) {
            Text(
                text = "…内容较长，已折叠剩余部分，点「展开全文」查看",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
