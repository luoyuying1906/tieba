package com.example.tiebasearch.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tiebasearch.data.remote.TiebaApi
import com.example.tiebasearch.data.remote.TiebaBlockedException
import com.example.tiebasearch.data.remote.TiebaHttp
import com.example.tiebasearch.data.repository.TiebaRepository
import com.example.tiebasearch.domain.model.SearchQuery
import com.example.tiebasearch.domain.model.TiebaPost
import com.example.tiebasearch.util.DebugDumper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val forumInput: String = "",
    val keywordInput: String = "",
    val strictForumOnly: Boolean = true,
    val posts: List<TiebaPost> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val page: Int = 0,
    val error: String? = null,
    /** 中性提示：过滤掉了多少条、是否走了兜底路径 */
    val notice: String? = null,
    val debugInfo: String? = null
) {
    val canSearch: Boolean get() = keywordInput.isNotBlank() && !isLoading
}

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TiebaRepository()
    private val api = TiebaApi()

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun onForumChange(v: String) = _state.update { it.copy(forumInput = v) }
    fun onKeywordChange(v: String) = _state.update { it.copy(keywordInput = v) }
    fun onStrictChange(v: Boolean) = _state.update { it.copy(strictForumOnly = v) }
    fun dismissMessages() = _state.update { it.copy(error = null, notice = null) }

    /** 重新搜索（回到第 1 页） */
    fun search() {
        val s = _state.value
        _state.update {
            it.copy(
                posts = emptyList(), page = 0, hasMore = false,
                isLoading = true, error = null, notice = null, debugInfo = null
            )
        }
        loadPage(page = 1, append = false, query = buildQuery(s))
    }

    /** 上拉加载下一页 */
    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        _state.update { it.copy(isLoadingMore = true) }
        loadPage(page = s.page + 1, append = true, query = buildQuery(s))
    }

    private fun buildQuery(s: SearchUiState) = SearchQuery(
        forumName = s.forumInput,
        keyword = s.keywordInput,
        strictForumOnly = s.strictForumOnly
    )

    private fun loadPage(page: Int, append: Boolean, query: SearchQuery) {
        viewModelScope.launch {
            try {
                val result = repo.search(query, page)

                val notice = buildString {
                    if (result.filteredOut > 0) {
                        append("本次拉取 ${result.rawCount} 条，其中 ${result.filteredOut} 条不属于目标吧，已过滤")
                    }
                    result.fallbackReason?.let {
                        if (isNotEmpty()) append("；")
                        append("已降级为吧内翻页模式（$it）")
                    }
                }.ifBlank { null }

                _state.update { st ->
                    st.copy(
                        posts = if (append) st.posts + result.posts else result.posts,
                        hasMore = result.hasMore,
                        page = page,
                        isLoading = false,
                        isLoadingMore = false,
                        notice = notice,
                        error = null
                    )
                }
            } catch (e: TiebaBlockedException) {
                fail(append, e.message ?: "触发百度安全验证", hintRateLimit = true)
            } catch (e: Exception) {
                fail(append, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun fail(append: Boolean, msg: String, hintRateLimit: Boolean = false) {
        val extra = if (hintRateLimit) "\n建议把请求间隔调大（当前 ${TiebaHttp.minIntervalMs}ms）后重试。" else ""
        _state.update {
            it.copy(
                isLoading = false,
                isLoadingMore = false,
                error = msg + extra,
                hasMore = if (append) it.hasMore else false
            )
        }
    }

    /** 调整限速间隔 */
    fun setMinInterval(ms: Long) {
        TiebaHttp.minIntervalMs = ms.coerceIn(200L, 10_000L)
    }

    /**
     * 一键自检：把当前搜索的真实原始响应导出到文件。
     * 接口改版时靠它定位问题，不用连电脑。
     */
    fun exportDebugDump() {
        val s = _state.value
        viewModelScope.launch {
            try {
                val url = buildString {
                    append("https://tieba.baidu.com/mo/q/search/thread?word=")
                    append(java.net.URLEncoder.encode(s.keywordInput, "UTF-8"))
                    if (s.forumInput.isNotBlank()) {
                        append("&kw=").append(java.net.URLEncoder.encode(s.forumInput, "UTF-8"))
                    }
                    append("&ie=utf-8&pn=0")
                }
                val body = api.raw(url)
                val f = DebugDumper.dump(getApplication<Application>(), "search_${s.forumInput}", body)
                _state.update {
                    it.copy(
                        notice = if (f != null) "原始响应已导出：${f.absolutePath}" else "导出失败",
                        debugInfo = f?.absolutePath
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "导出失败：${e.message}") }
            }
        }
    }
}
