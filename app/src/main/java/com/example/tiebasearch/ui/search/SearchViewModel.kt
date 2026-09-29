package com.example.tiebasearch.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tiebasearch.data.remote.TiebaApi
import com.example.tiebasearch.data.remote.TiebaBlockedException
import com.example.tiebasearch.data.remote.TiebaHttp
import com.example.tiebasearch.data.parser.MoHtmlParser
import com.example.tiebasearch.data.repository.SummaryRepository
import com.example.tiebasearch.data.repository.TiebaRepository
import com.example.tiebasearch.domain.model.Forums
import com.example.tiebasearch.domain.model.TiebaFloor
import com.example.tiebasearch.domain.model.TiebaPost
import com.example.tiebasearch.domain.model.TimeRange
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

    /** 当前勾选的吧（不带「吧」字）。默认全选 */
    val selectedForums: Set<String> = Forums.ALL.toSet(),

    /** 时间过滤范围。默认近3年 */
    val timeRange: TimeRange = TimeRange.DEFAULT,

    /** DeepSeek API Key 输入框的当前内容 */
    val deepSeekKeyInput: String = "",

    val posts: List<TiebaPost> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    /** 下次 loadMore 该从哪个全网页码继续 */
    val nextPage: Int = 1,
    val error: String? = null,
    /** 中性提示：扫描了几页、筛出几条、是否走了兜底 */
    val notice: String? = null,

    // ---- 详情页 ----
    val selectedPost: TiebaPost? = null,
    val detailFloors: List<TiebaFloor> = emptyList(),
    val isLoadingDetail: Boolean = false,
    val detailMessage: String? = null,

    // ---- AI 总结 ----
    val summary: String? = null,
    val summaryLoading: Boolean = false,
    val summaryError: String? = null
) {
    val canSearch: Boolean
        get() = keywordInput.isNotBlank() && !isLoading && selectedForums.isNotEmpty()

    val inDetail: Boolean get() = selectedPost != null
}

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TiebaRepository()
    private val api = TiebaApi()
    private val summaryRepo = SummaryRepository(app)
    private val settings = SettingsStore(app)

    private val _state = MutableStateFlow(
        SearchUiState(
            intervalMs = settings.requestIntervalMs,
            selectedForums = settings.selectedForums,
            timeRange = settings.timeRange,
            deepSeekKeyInput = settings.deepSeekApiKey
        )
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

    /** 勾选/取消某个吧（需求二的多选） */
    fun onToggleForum(forum: String) {
        val current = _state.value.selectedForums
        val next = if (forum in current) current - forum else current + forum
        settings.selectedForums = next
        _state.update { it.copy(selectedForums = next) }
    }

    fun onSelectAllForums() {
        val next = Forums.ALL.toSet()
        settings.selectedForums = next
        _state.update { it.copy(selectedForums = next) }
    }

    /** 切换时间过滤范围（需求五）。立即持久化，下次打开还是这个选择 */
    fun onTimeRangeChange(range: TimeRange) {
        settings.timeRange = range
        _state.update { it.copy(timeRange = range) }
    }

    /** DeepSeek Key 边输边存，省得用户忘了保存 */
    fun onDeepSeekKeyChange(v: String) {
        settings.deepSeekApiKey = v
        _state.update { it.copy(deepSeekKeyInput = v) }
    }

    fun dismissMessages() = _state.update { it.copy(error = null, notice = null) }

    // ------------------------------------------------------------ 搜索

    fun search() {
        val s = _state.value
        if (s.keywordInput.isBlank() || s.selectedForums.isEmpty()) return

        _state.update {
            it.copy(
                posts = emptyList(), nextPage = 1, hasMore = false,
                isLoading = true, error = null, notice = null
            )
        }
        loadPage(page = 1, append = false)
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        _state.update { it.copy(isLoadingMore = true) }
        loadPage(page = s.nextPage, append = true)
    }

    private fun loadPage(page: Int, append: Boolean) {
        val s = _state.value
        viewModelScope.launch {
            try {
                val result = repo.search(s.keywordInput, s.selectedForums, s.timeRange, page)

                val notice = if (result.usedTitleFallback) {
                    "全网搜索（${s.timeRange.label}）在这几个吧里没有命中，" +
                        "已改用「吧内标题检索」兜底。注意：兜底只匹配标题，正文含关键词的会漏掉。"
                } else {
                    "扫描 ${result.scannedPages} 页全网数据（共 ${result.rawCount} 条），" +
                        "按「${s.timeRange.label}」+ 所选 ${s.selectedForums.size} 个吧" +
                        "筛出 ${result.posts.size} 条" +
                        "（吧名剔除 ${result.droppedByForum} 条，时间剔除 ${result.droppedByTime} 条）"
                }

                _state.update { st ->
                    st.copy(
                        posts = if (append) st.posts + result.posts else result.posts,
                        hasMore = result.hasMore,
                        nextPage = result.nextPage,
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
        val extra = if (hintRateLimit) {
            "\n建议把「请求间隔」调大（当前 ${TiebaHttp.minIntervalMs}ms）后重试。"
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

    // ------------------------------------------------------------ 详情页 + AI 总结

    /** 点「展开全文」→ 进入详情页，并**自动**触发 AI 总结（命中缓存则不调 API） */
    fun openDetail(post: TiebaPost) {
        val cached = summaryRepo.cached(post.threadId)
        _state.update {
            it.copy(
                selectedPost = post,
                detailFloors = emptyList(),
                detailMessage = null,
                summary = cached,
                summaryError = null,
                summaryLoading = false
            )
        }
        if (cached == null) autoSummarize(post)
    }

    fun closeDetail() {
        _state.update {
            it.copy(
                selectedPost = null,
                detailFloors = emptyList(),
                detailMessage = null,
                summary = null,
                summaryError = null,
                summaryLoading = false
            )
        }
    }

    private fun autoSummarize(post: TiebaPost) {
        val key = settings.deepSeekApiKey
        if (key.isBlank()) {
            _state.update {
                it.copy(summaryError = "尚未配置 DeepSeek API Key。请返回首页展开「设置」，填入后点「重新总结」。")
            }
            return
        }
        runSummarize(post, key, force = false)
    }

    /** 手动重试 / 重新生成（会跳过缓存，但结果仍写回缓存） */
    fun retrySummary() {
        val post = _state.value.selectedPost ?: return
        val key = settings.deepSeekApiKey
        if (key.isBlank()) {
            _state.update { it.copy(summaryError = "请先在首页「设置」里填入 DeepSeek API Key") }
            return
        }
        runSummarize(post, key, force = true)
    }

    private fun runSummarize(post: TiebaPost, key: String, force: Boolean) {
        if (_state.value.summaryLoading) return
        _state.update { it.copy(summaryLoading = true, summaryError = null) }
        viewModelScope.launch {
            try {
                val text = if (force) {
                    summaryRepo.resummarize(post, key)
                } else {
                    summaryRepo.summarize(post, key)
                }
                _state.update { it.copy(summaryLoading = false, summary = text, summaryError = null) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        summaryLoading = false,
                        summaryError = e.message ?: e.javaClass.simpleName
                    )
                }
            }
        }
    }

    /**
     * 点「加载完整正文」→ 按 tid 抓帖子页。
     * 抓完顺手把完整正文也交给 AI 重新总结一次会更准，但那会多花钱，
     * 所以这里**不**自动重算；用户想重算可以点 AI 卡片上的「重新总结」。
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
                            "帖子页没能解析出内容（页面结构可能已变）。建议点下方「在浏览器中打开原帖」。"
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

    /**
     * 结构诊断：把帖子页的「解析指纹」导出。
     *
     * 为什么需要它：楼层解析靠的是结构特征（头像 + 作者链 + 时间），不是写死的 class 名。
     * 万一贴吧改版导致解析不出楼层，光知道「解析不出来」没法定位。
     * 这个方法会输出一段**很短**的文本（出现最多的 class + 次数 + 各锚点数量），
     * 复制发给我就能知道该换成什么选择器 —— 不用把几百 KB 的 HTML 传出来。
     */
    fun exportThreadDiagnostics() {
        val post = _state.value.selectedPost ?: return
        if (_state.value.isLoadingDetail) return
        if (post.threadId <= 0) {
            _state.update { it.copy(detailMessage = "这条结果没有有效的 tid，无法做结构诊断。") }
            return
        }

        _state.update { it.copy(isLoadingDetail = true, detailMessage = "正在下载帖子页做结构诊断…") }

        viewModelScope.launch {
            try {
                val html = api.threadFloorsHtml(post.threadId.toString(), 1)
                val fingerprint = MoHtmlParser.structureFingerprint(html)
                val saved = DebugDumper.dump(
                    getApplication<Application>(),
                    "thread_${post.threadId}",
                    html
                )
                _state.update {
                    it.copy(
                        isLoadingDetail = false,
                        detailMessage = buildString {
                            append("—— 结构诊断（可长按复制发我）——\n")
                            append(fingerprint)
                            append("\n原始 HTML 已保存到：")
                            append(saved?.absolutePath ?: "导出失败")
                        }
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingDetail = false,
                        detailMessage = "结构诊断失败：${e.message ?: e.javaClass.simpleName}"
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
