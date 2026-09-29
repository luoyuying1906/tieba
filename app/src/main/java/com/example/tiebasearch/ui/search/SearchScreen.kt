package com.example.tiebasearch.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.tiebasearch.domain.model.TiebaPost
import com.example.tiebasearch.util.TimeFormat
import com.example.tiebasearch.util.UrlOpener

/** 请求间隔预设。改成"按钮组"而不是滑块，是为了避开 Slider 在 Material3 里
 *  存在多个重载、按名字传参可能产生解析歧义的风险。 */
private val INTERVAL_PRESETS = listOf(300L, 800L, 1500L, 3000L)

@Composable
fun SearchScreen(vm: SearchViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    if (state.selectedPost != null) {
        // 详情页。用一个状态切换代替导航库 —— 少一个依赖就少一分构建风险。
        PostDetailScreen(
            state = state,
            onBack = vm::closeDetail,
            onLoadFull = vm::loadFullThread
        )
    } else {
        SearchListScreen(
            state = state,
            onKeywordChange = vm::onKeywordChange,
            onIntervalChange = vm::onIntervalChange,
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
    state: SearchUiState,
    onKeywordChange: (String) -> Unit,
    onIntervalChange: (Long) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (TiebaPost) -> Unit,
    onExportDump: () -> Unit,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("贴吧全网搜索") },
                actions = {
                    IconButton(onClick = onExportDump) {
                        // 核心图标 Build，不用 BugReport（后者属于 material-icons-extended，
                        // 那个依赖在部分 Compose BOM 版本里已被移除）
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
                onIntervalChange = onIntervalChange,
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
    onIntervalChange: (Long) -> Unit,
    onSearch: () -> Unit
) {
    // 设置默认收起，不占地方
    var showSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 需求一：只剩一个关键词输入框，不再有「选择贴吧」
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showSettings = !showSettings }) {
                Text(
                    text = (if (showSettings) "▾ " else "▸ ") + "请求间隔 ${state.intervalMs}ms",
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "全网搜索",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showSettings) {
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
                text = "两次请求之间的最小间隔（毫秒）。数值越大越不容易被百度风控；" +
                    "如果出现「触发安全验证」，请调大后重试。设置会自动保存。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

            // ---- 【帖子内容】列表里只显示摘要，避免卡片过长 ----
            if (post.title.isNotBlank()) {
                Text(
                    text = post.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = post.content.ifBlank { "（正文为空或需进入原帖查看）" },
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))

            // ---- 需求二 + 需求三：两个按钮 ----
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
            "已取消吧名限制，直接在全部贴吧里搜索",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "例：诡秘之主",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
