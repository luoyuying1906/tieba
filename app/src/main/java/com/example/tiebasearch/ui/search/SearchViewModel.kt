package com.example.tiebasearch.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tiebasearch.data.remote.TiebaApi
import com.example.tiebasearch.data.remote.TiebaBlockedException
import com.example.tiebasearch.data.remote.TiebaHttp
import com.example.tiebasearch.data.repository.TiebaRepository
import com.example.tiebasearch.domain.model.TiebaFloor
import com.example.tiebasearch.domain.model.TiebaPost
import com.example.tiebasearch.util.DebugDumper
import com.example.tiebasearch.util.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val keywordInput: String = "",
    /** 请求间隔（毫秒），可在界面上调 */
    val intervalMs: Long = SettingsStore.DEFAULT,
    val posts: List<TiebaPost> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val page: Int = 0,
    val error: String? = null,
    /** 中性提示：本页拿到多少条、是否触发了降级 */
    val notice: String? = null,

    // ---- 详情页状态 ----
    val selectedPost: TiebaPost? = null,
    val detailFloors: List<TiebaFloor> = emptyList(),
    val isLoadingDetail: Boolean = false,
    val detailMessage: String? = null
) {
    val canSearch: Boolean get() = keywordInput.isNotBlank() && !isLoading
    val inDetail: Boolean get() = selectedPost != null
}

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TiebaRepository()
    private val api = TiebaApi()
    private val settings = SettingsStore(app)

    private val _state = MutableStateFlow(
        SearchUiState(intervalMs = settings.requestIntervalMs)
    )
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        // 启动时把上次保存的间隔应用到网络层
        TiebaHttp.minIntervalMs = settings.requestIntervalMs
    }

    // ------------------------------------------------------------ 输入

    fun onKeywordChange(v: String) = _state.update { it.copy(keywordInput = v) }

    /**
     * 调整请求间隔。
     * 立刻生效（网络层是全局的），并持久化，避免用户被风控后调大了、下次打开又退回默认。
     */
    fun onIntervalChange(ms: Long) {
        val clamped = ms.coerceIn(SettingsStore.MIN, SettingsStore.MAX)
        TiebaHttp.minIntervalMs = clamped
        settings.requestIntervalMs = clamped
        _state.update { it.copy(intervalMs = clamped) }
    }

    fun dismissMessages() = _state.update { it.copy(error = null, notice = null) }

    // ------------------------------------------------------------ 搜索

    /** 重新搜索（回到第 1 页） */
    fun search() {
        val keyword = _state.value.keywordInput
        if (keyword.isBlank()) return

        _state.update {
            it.copy(
                posts = emptyList(), page = 0, hasMore = false,
                isLoading = true, error = null, notice = null
            )
        }
        loadPage(page = 1, append = false, keyword = keyword)
    }

    /** 上拉加载下一页 */
    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        _state.update { it.copy(isLoadingMore = true) }
        loadPage(page = s.page + 1, append = true, keyword = s.keywordInput)
    }

    private fun loadPage(page: Int, append: Boolean, keyword: String) {
        viewModelScope.launch {
            try {
                val result = repo.search(keyword, page)

                val notice = if (result.rawCount > result.posts.size) {
                    "本页接口返回 ${result.rawCount} 条，去重后 ${result.posts.size} 条"
                } else {
                    "本页 ${result.posts.size} 条"
                }

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
                fail(
                    append = append,
                    msg = e.message ?: "触发百度安全验证",
                    hintRateLimit = true
                )
            } catch (e: Exception) {
                fail(append, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun fail(append: Boolean, msg: String, hintRateLimit: Boolean = false) {
        val extra = if (hintRateLimit) {
            "\n建议把上方「请求间隔」调大（当前 ${TiebaHttp.minIntervalMs}ms）后重试。"
        } else ""
        _state.update {
            it.copy(
                isLoading = false,
                isLoadingMore = false,
                error = msg + extra,
                hasMore = if (append) it.hasMore else false
            )
        }
    }

    // ------------------------------------------------------------ 详情页

    /** 点「展开全文」→ 进入详情页 */
    fun openDetail(post: TiebaPost) {
        _state.update {
            it.copy(selectedPost = post, detailFloors = emptyList(), detailMessage = null)
        }
    }

    fun closeDetail() {
        _state.update {
            it.copy(selectedPost = null, detailFloors = emptyList(), detailMessage = null)
        }
    }

    /**
     * 点「加载完整正文」→ 按 tid 抓帖子页。
     * 因为搜索接口只返回截断摘要，真正想看全文必须走这一步。
     */
    fun loadFullThread() {
        val post = _state.value.selectedPost ?: return
        if (_state.value.isLoadingDetail) return
        if (post.threadId <= 0) {
            _state.update { it.copy(detailMessage = "这条结果没有有效的帖子 id，只能点下方按钮去原帖查看。") }
            return
        }

        _state.update { it.copy(isLoadingDetail = true, detailMessage = null) }
        viewModelScope.launch {
            try {
                val floors = repo.loadThreadFloors(post.threadId.toString())
                _state.update {
                    it.copy(
                        isLoadingDetail = false,
                        detailFloors = floors,
                        detailMessage = if (floors.isEmpty()) {
                            "帖子页没能解析出内容（页面结构可能已变）。建议直接点下方「在浏览器中打开原帖」。"
                        } else {
                            "已加载 ${floors.size} 层"
                        }
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingDetail = false,
                        detailMessage = "加载失败：${e.message ?: e.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------ 自检

    /**
     * 一键自检：把当前搜索的真实原始响应导出到文件。
     * 接口改版时靠它定位问题，不用连电脑。
     */
    fun exportDebugDump() {
        val s = _state.value
        if (s.keywordInput.isBlank()) {
            _state.update { it.copy(error = "请先输入关键词再导出原始响应") }
            return
        }
        viewModelScope.launch {
            try {
                val url = buildString {
                    append("https://tieba.baidu.com/mo/q/search/thread?word=")
                    append(java.net.URLEncoder.encode(s.keywordInput, "UTF-8"))
                    append("&ie=utf-8&pn=0")
                }
                val body = api.raw(url)
                val f = DebugDumper.dump(getApplication<Application>(), "search_${s.keywordInput}", body)
                _state.update {
                    it.copy(
                        notice = if (f != null) "原始响应已导出：${f.absolutePath}" else "导出失败"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "导出失败：${e.message}") }
            }
        }
    }
}
