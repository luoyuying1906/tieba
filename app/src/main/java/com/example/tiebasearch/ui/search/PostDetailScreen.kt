package com.example.tiebasearch.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tiebasearch.domain.model.TiebaFloor
import com.example.tiebasearch.util.TimeFormat
import com.example.tiebasearch.util.UrlOpener

/**
 * 帖子详情页。
 *
 * 从上到下：
 *   1. 【需求四】AI 总结卡片 —— 进入页面即自动触发（命中缓存则不调 API）
 *   2. 作者 / 时间 / 来源吧
 *   3. 标题 + 正文（整页可滚动，不截断）
 *   4. 「加载完整正文」—— 按 tid 抓帖子页，补全被接口截断的内容
 *   5. 【需求三】完整楼层列表
 *   6. 【需求二/三】整宽的「在浏览器中打开原帖」按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onLoadFull: () -> Unit,
    onRetrySummary: () -> Unit
) {
    val post = state.selectedPost ?: return
    val context = LocalContext.current

    // 让系统返回键也能退回列表页，而不是直接退出 App
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("帖子详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---- 需求四：AI 总结，放在最上方 ----
            SummaryCard(
                summary = state.summary,
                loading = state.summaryLoading,
                error = state.summaryError,
                onRetry = onRetrySummary
            )

            AuthorRow(post, avatarSize = 42)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("时间 " + TimeFormat.humanize(post.createdAt))
                Tag("来自${post.forumName}吧", highlight = true)
            }

            HorizontalDivider()

            if (post.title.isNotBlank()) {
                Text(
                    text = post.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    lineHeight = 26.sp
                )
            }

            // 摘要正文：不设 maxLines，完整展示
            Text(
                text = post.content.ifBlank { "（正文为空或需进入原帖查看）" },
                fontSize = 15.sp,
                lineHeight = 24.sp
            )

            if (post.mayBeTruncated) {
                Text(
                    text = "提示：搜索接口只返回摘要，上面这段正文可能被截断。" +
                        "点下面的按钮可以抓取完整正文（AI 总结的输入也会更完整）。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onLoadFull,
                enabled = !state.isLoadingDetail,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoadingDetail) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("正在抓取…")
                } else {
                    Text("加载完整正文")
                }
            }

            state.detailMessage?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- 完整楼层 ----
            if (state.detailFloors.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "完整内容（共 ${state.detailFloors.size} 层）",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                state.detailFloors.forEach { floor -> FloorBlock(floor) }
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("回复 ${post.replyCount}")
                Tag("点赞 ${post.likeCount}")
                Tag("tid ${post.threadId}")
            }

            // ---- 醒目的「打开原帖」大按钮（只响应用户点击，不自动跳转） ----
            Button(
                onClick = { UrlOpener.open(context, post.webUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("在浏览器中打开原帖", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Text(
                text = post.webUrl,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * AI 总结卡片（需求四）。
 *
 * 三种状态：加载中 / 有结果 / 出错（通常是没配 Key 或 Key 无效）。
 * 出错时允许手动重试 —— 但**只要缓存里已有结果就不会重复调 API**，
 * 这是用户明确要求的「同一个帖子只调用一次」。
 */
@Composable
private fun SummaryCard(
    summary: String?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🤖 AI 总结",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.weight(1f))
                if (!loading) {
                    Text(
                        text = "重新总结",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.clickable(onClick = onRetry)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "正在调用 DeepSeek 生成总结…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                !summary.isNullOrBlank() -> {
                    Text(
                        text = summary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    // 缓存说明：让用户确信不会重复扣费
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "该总结已缓存，再次打开本帖不会再调用 API",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                else -> Text(
                    text = error ?: "尚未生成总结",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/** 单层楼层/回复的展示块 */
@Composable
private fun FloorBlock(floor: TiebaFloor) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = floor.authorName.ifBlank { "（用户名不可见）" },
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            if (floor.isOp) {
                Spacer(Modifier.width(6.dp))
                Tag("楼主", highlight = true)
            }
        }
        Text(
            text = TimeFormat.humanize(floor.createdAt),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = floor.content,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
    }
}
