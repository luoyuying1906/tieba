package com.example.tiebasearch.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tiebasearch.domain.model.Forums
import com.example.tiebasearch.domain.model.TiebaPost
import com.example.tiebasearch.domain.model.TimeRange
import com.example.tiebasearch.util.TimeFormat
import com.example.tiebasearch.util.UrlOpener

/** 请求间隔预设。用按钮组而不是滑块，避开 Slider 多重重载可能带来的解析歧义 */
private val INTERVAL_PRESETS = listOf(300L, 800L, 1500L, 3000L)

/** 时间过滤预设（需求五）。放在顶层避免每次重组都新建数组 */
private val TIME_RANGES: List<TimeRange> = TimeRange.values().toList()

@Composable
fun SearchScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    vm: SearchViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    if (state.selectedPost != null) {
        // 详情页。用一个状态切换代替导航库 —— 少一个依赖就少一分构建风险。
        PostDetailScreen(
            state = state,
            onBack = vm::closeDetail,
            onLoadFull = vm::loadFullThread,
            onRetrySummary = vm::retrySummary
        )
    } else {
        SearchListScreen(
            isDark = isDark,
            onToggleTheme = onToggleTheme,
            state = state,
            onKeywordChange = vm::onKeywordChange,
            onToggleForum = vm::onToggleForum,
            onSelectAllForums = vm::onSelectAllForums,
            onTimeRangeChange = vm::onTimeRangeChange,
            onIntervalChange = vm::onIntervalChange,
            onDeepSeekKeyChange = vm::onDeepSeekKeyChange,
            onSearch = vm::search,
            onLoadMore = vm::loadMore,
            onOpenDetail = vm::openDetail,
            onExportDump = vm::exportDebugDump,
            onDismiss = vm::dismissMessages
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchListScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    state: SearchUiState,
    onKeywordChange: (String) -> Unit,
    onToggleForum: (String) -> Unit,
    onSelectAllForums: () -> Unit,
    onTimeRangeChange: (TimeRange) -> Unit,
    onIntervalChange: (Long) -> Unit,
    onDeepSeekKeyChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (TiebaPost) -> Unit,
    onExportDump: () -> Unit,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("贴吧搜索") },
                actions = {
                    // 需求一：日间/夜间切换，一键生效
                    IconButton(onClick = onToggleTheme) {
                        Text(
                            text = if (isDark) "☀️" else "🌙",
                            fontSize = 18.sp
                        )
                    }
                    IconButton(onClick = onExportDump) {
                        Icon(Icons.Default.Build, contentDescription = "导出原始响应(自检)")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchHeader(
                state = state,
                onKeywordChange = onKeywordChange,
                onToggleForum = onToggleForum,
                onSelectAllForums = onSelectAllForums,
                onTimeRangeChange = onTimeRangeChange,
                onIntervalChange = onIntervalChange,
                onDeepSeekKeyChange = onDeepSeekKeyChange,
                onSearch = onSearch
            )

            MessageBar(state, onDismiss)

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.posts.isEmpty() -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }

                    state.posts.isEmpty() -> {
                        EmptyHint(Modifier.align(Alignment.Center))
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.posts, key = { it.postId }) { post ->
                                PostCard(post = post, onOpenDetail = onOpenDetail)
                            }
                            item {
                                LoadMoreFooter(
                                    hasMore = state.hasMore,
                                    loading = state.isLoadingMore,
                                    onLoadMore = onLoadMore
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 搜索区

@Composable
private fun SearchHeader(
    state: SearchUiState,
    onKeywordChange: (String) -> Unit,
    onToggleForum: (String) -> Unit,
    onSelectAllForums: () -> Unit,
    onTimeRangeChange: (TimeRange) -> Unit,
    onIntervalChange: (Long) -> Unit,
    onDeepSeekKeyChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 关键词（v2 起已取消吧名输入）
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.keywordInput,
                onValueChange = onKeywordChange,
                label = { Text("关键词") },
                placeholder = { Text("例如：诡秘之主") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSearch, enabled = state.canSearch) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("搜索")
            }
        }

        // ---- 需求五：发帖时间过滤（常驻可见，因为默认就是「近3年」） ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TIME_RANGES.forEach { range ->
                val selected = state.timeRange == range
                if (selected) {
                    Button(
                        onClick = { onTimeRangeChange(range) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(range.label, fontSize = 11.sp, maxLines = 1)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onTimeRangeChange(range) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(range.label, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        // ---- 设置开关 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showSettings = !showSettings }) {
                Text(
                    text = (if (showSettings) "▾ " else "▸ ") +
                        "设置（${state.selectedForums.size} 个吧 · 间隔 ${state.intervalMs}ms）",
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "按发帖时间：${state.timeRange.label}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showSettings) {
            // 加上高度上限 + 内部滚动，避免设置面板把结果列表挤没了
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ---- 需求二：检索范围多选 ----
                Text(
                    text = "检索范围（只从勾选的吧抓取）",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Forums.ALL.forEach { forum ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleForum(forum) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = forum in state.selectedForums,
                            onCheckedChange = { onToggleForum(forum) }
                        )
                        Text(Forums.display(forum), fontSize = 13.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSelectAllForums) {
                        Text("全选", fontSize = 12.sp)
                    }
                    Text(
                        text = "已选 ${state.selectedForums.size} / ${Forums.ALL.size}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.selectedForums.isEmpty()) {
                    Text(
                        text = "⚠️ 一个吧都没选，无法搜索",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider()

                // ---- 请求间隔 ----
                Text(
                    text = "请求间隔（毫秒）",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    INTERVAL_PRESETS.forEach { ms ->
                        val selected = state.intervalMs == ms
                        if (selected) {
                            Button(
                                onClick = { onIntervalChange(ms) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("$ms", fontSize = 11.sp, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onIntervalChange(ms) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("$ms", fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
                Text(
                    text = "数值越大越不容易被百度风控。出现「触发安全验证」时请调大。设置会自动保存。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // ---- 需求四：DeepSeek API Key ----
                Text(
                    text = "DeepSeek API Key（用于 AI 总结）",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = state.deepSeekKeyInput,
                    onValueChange = onDeepSeekKeyChange,
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "到 platform.deepseek.com 申请。Key 只保存在本机，不会上传，也不会写进代码。" +
                        "总结结果按帖子缓存，同一个帖子只调用一次 API。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MessageBar(state: SearchUiState, onDismiss: () -> Unit) {
    state.error?.let {
        MessageStrip(it, MaterialTheme.colorScheme.errorContainer, onDismiss)
    }
    state.notice?.let {
        MessageStrip(it, MaterialTheme.colorScheme.secondaryContainer, onDismiss)
    }
}

// ------------------------------------------------------------------ 结果卡片

@Composable
private fun PostCard(post: TiebaPost, onOpenDetail: (TiebaPost) -> Unit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // ---- 【发帖人】 ----
            AuthorRow(post)

            Spacer(Modifier.height(8.dp))

            // ---- 【时间】+【来自XX吧】 ----
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("时间 " + TimeFormat.humanize(post.createdAt))
                Tag("来自${post.forumName}吧", highlight = true)
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            if (post.title.isNotBlank()) {
                Text(
                    text = post.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
            }

            // ---- 需求三：长内容分段展示，而不是挤在一个大框里 ----
            SegmentedContent(text = post.content)

            Spacer(Modifier.height(10.dp))

            // ---- 需求二/三/四的入口按钮 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onOpenDetail(post) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("展开全文")
                }
                Button(
                    onClick = { UrlOpener.open(context, post.webUrl) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("查看原帖", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "回复 ${post.replyCount}  ·  点赞 ${post.likeCount}  ·  tid ${post.threadId}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadMoreFooter(hasMore: Boolean, loading: Boolean, onLoadMore: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(24.dp))
            hasMore -> Button(onClick = onLoadMore) { Text("加载更多") }
            else -> Text(
                "没有更多了",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("输入关键词开始搜索", fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "检索范围限定在指定的贴吧内，并按发帖时间过滤",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "例：诡秘之主",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
