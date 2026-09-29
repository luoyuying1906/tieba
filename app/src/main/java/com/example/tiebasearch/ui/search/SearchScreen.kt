package com.example.tiebasearch.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.tiebasearch.domain.model.TiebaPost
import com.example.tiebasearch.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: SearchViewModel = viewModel()) {

    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("贴吧搜索") },
                actions = {
                    IconButton(onClick = { vm.exportDebugDump() }) {
                        Icon(Icons.Default.BugReport, contentDescription = "导出原始响应(自检)")
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
            SearchBar(
                state = state,
                onForumChange = vm::onForumChange,
                onKeywordChange = vm::onKeywordChange,
                onStrictChange = vm::onStrictChange,
                onSearch = vm::search
            )

            MessageBar(state, onDismiss = vm::dismissMessages)

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
                                PostCard(post)
                            }
                            item {
                                LoadMoreFooter(
                                    hasMore = state.hasMore,
                                    loading = state.isLoadingMore,
                                    onLoadMore = vm::loadMore
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
private fun SearchBar(
    state: SearchUiState,
    onForumChange: (String) -> Unit,
    onKeywordChange: (String) -> Unit,
    onStrictChange: (Boolean) -> Unit,
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = state.forumInput,
            onValueChange = onForumChange,
            label = { Text("选择贴吧") },
            placeholder = { Text("例如：诡秘之主吧") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.keywordInput,
            onValueChange = onKeywordChange,
            label = { Text("关键词") },
            placeholder = { Text("例如：诡秘之主") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.strictForumOnly, onCheckedChange = onStrictChange)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("只显示指定吧的帖子", fontSize = 14.sp)
                Text(
                    "贴吧搜索接口本身是全吧搜索，勾选后按「来源吧名」本地过滤",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onSearch, enabled = state.canSearch) {
                Icon(Icons.Default.Search, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("搜索")
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

@Composable
private fun MessageStrip(text: String, bg: Color, onDismiss: () -> Unit) {
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

// ------------------------------------------------------------------ 结果卡片

@Composable
private fun PostCard(post: TiebaPost) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // ---- 【发帖人】 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
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

            Spacer(Modifier.height(8.dp))

            // ---- 【时间】+【来自XX吧】 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag("时间 " + TimeFormat.humanize(post.createdAt))
                Spacer(Modifier.width(6.dp))
                Tag("来自${post.forumName}吧", highlight = true)
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ---- 【帖子内容】 ----
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
private fun Tag(text: String, highlight: Boolean = false) {
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
        Text("输入吧名和关键词开始搜索", fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "例：吧名「诡秘之主吧」+ 关键词「诡秘之主」",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

