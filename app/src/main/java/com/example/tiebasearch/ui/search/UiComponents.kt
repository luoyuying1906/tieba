package com.example.tiebasearch.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tiebasearch.domain.model.TiebaPost

/**
 * 搜索结果列表与详情页共用的小组件。
 * 放在一起是为了避免两处各写一份、以后改一处漏一处。
 */

/** 小标签，例如「时间 今天 21:00」「来自北京吧」 */
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

/** 头像 + 昵称 + 用户名/UID。列表和详情页都要展示【发帖人】，所以抽出来 */
@Composable
internal fun AuthorRow(post: TiebaPost, avatarSize: Int = 36) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(avatarSize.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (post.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = post.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
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
