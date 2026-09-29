package com.example.tiebasearch.ui.search

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tiebasearch.domain.model.TiebaFloor
import com.example.tiebasearch.domain.model.TiebaPost
import com.example.tiebasearch.util.TimeFormat
import com.example.tiebasearch.util.UrlOpener

/**
 * 帖子详情页（重构版）。
 *
 * 结构从上到下：
 *   1. AI 总结卡片（需求四，保留在页面最顶部）
 *   2. **主贴卡片** —— 楼主正文单独一张卡，不再和回复混在一起
 *   3. 「加载完整正文」按钮 + 状态提示
 *   4. **回复楼层卡片列表** —— 每一层一张独立卡片（朋友圈那种感觉），
 *      显示 楼层号 / 发帖人 / 时间 / 本层内容
 *   5. 整宽的「在浏览器中打开原帖」
 *
 * 另外顶栏有个 🐞 按钮：解析不出楼层时会导出「结构诊断」，
 * 那段文本很短，可以复制发我，我就能定位该用什么选择器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onLoadFull: () -> Unit,
    onRetrySummary: () -> Unit,
    onExportDiagnostics: () -> Unit
) {
    val post = state.selectedPost ?: return
    val context = LocalContext.current

    // 系统返回键也能退回列表页，而不是直接退出 App
    BackHandler(onBack = onBack)

    val floors = state.detailFloors
    // 主贴 = 第一个带「楼主」标记的楼层，没有就取第一层
    val mainIndex = floors.indexOfFirst { it.isOp }.let { if (it >= 0) it else 0 }
    val mainFloor = floors.getOrNull(mainIndex)
    val replies = floors.filterIndexed { i, _ -> i != mainIndex }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("帖子详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onExportDiagnostics) {
                        Icon(Icons.Default.Build, contentDescription = "结构诊断(解析不出楼层时用)")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- 1. AI 总结（保留在顶部） ----------
            SummaryCard(
                summary = state.summary,
                loading = state.summaryLoading,
                error = state.summaryError,
                onRetry = onRetrySummary
            )

            // ---------- 2. 主贴（楼主） ----------
            MainPostCard(post = post, opFloor = mainFloor)

            // ---------- 3. 加载完整正文 ----------
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
                    Text(if (floors.isEmpty()) "加载完整正文与回复" else "重新加载完整正文")
                }
            }

            state.detailMessage?.let { msg ->
                // 用 SelectionContainer 包起来，诊断文本可以直接长按复制
                SelectionContainer {
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------- 4. 回复楼层 ----------
            when {
                replies.isNotEmpty() -> {
                    Text(
                        text = "回复（${replies.size} 层）· 楼层号按解析顺序编号",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    replies.forEach { floor -> FloorCard(floor) }
                }

                floors.isNotEmpty() -> {
                    Text(
                        text = "这个帖子只有主楼，没有解析到其他回复层。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("回复 ${post.replyCount}")
                Tag("点赞 ${post.likeCount}")
                Tag("tid ${post.threadId}")
            }

            // ---------- 5. 打开原帖（只响应用户点击，不自动跳转） ----------
            Button(
                onClick = { UrlOpener.open(context, post.webUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("在浏览器中打开原帖", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            SelectionContainer {
                Text(
                    text = post.webUrl,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * 主贴卡片（楼主正文）。
 *
 * 优先用「加载完整正文」抓回来的主楼内容；
 * 还没抓或抓不到时退回搜索接口给的摘要，并明确告诉你这只是摘要。
 */
@Composable
private fun MainPostCard(post: TiebaPost, opFloor: TiebaFloor?) {
    val content = opFloor?.content?.takeIf { it.isNotBlank() } ?: post.content
    val authorName = opFloor?.authorName?.takeIf { it.isNotBlank() } ?: post.authorDisplay
    val avatar = opFloor?.avatarUrl ?: post.avatarUrl
    val time = opFloor?.createdAt ?: post.createdAt

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("主贴", highlight = true)
                Tag("来自${post.forumName}吧")
            }

            if (post.title.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = post.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    lineHeight = 26.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(avatar, 32)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = authorName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = TimeFormat.humanize(time),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (opFloor != null) {
                    Tag("#${opFloor.floorNumber ?: 1}")
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            // 需求三：主贴超 500 字折叠起来，别撑满屏幕
            CollapsibleText(
                text = content.ifBlank { "（正文为空或需进入原帖查看）" },
                collapseThreshold = 500,
                collapsedChars = 350
            )

            if (opFloor == null && post.mayBeTruncated) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "↑ 这是搜索接口返回的摘要，可能不完整。点下方「加载完整正文与回复」抓取全文。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 单层回复卡片：楼层号 + 发帖人 + 时间 + 内容 */
@Composable
private fun FloorCard(floor: TiebaFloor) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(floor.avatarUrl, 32)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = floor.authorName.ifBlank { "（用户名不可见）" },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
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
                }
                Tag("#${floor.floorNumber ?: "?"}")
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // 需求三：单层超 500 字折叠
            CollapsibleText(
                text = floor.content,
                collapseThreshold = 500,
                collapsedChars = 300,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            floor.floorId?.let { pid ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "pid $pid",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * AI 总结卡片（需求四，保留）。
 *
 * 三种状态：加载中 / 有结果 / 出错（通常是没配 Key 或 Key 无效）。
 * 出错时允许手动重试 —— 但**只要缓存里已有结果就不会重复调 API**。
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
