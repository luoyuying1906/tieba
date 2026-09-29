package com.example.tiebasearch.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * 全局 HTTP 配置。
 *
 * 这里集中处理三件爬虫必须做对的事：
 *   1. User-Agent —— 贴吧会根据 UA 决定返回「服务端渲染的 HTML」还是「JS 空壳 SPA」。
 *      桌面 UA 访问 /f/search/res 拿到的正文是空的（实测），所以必须伪装移动端。
 *   2. 限速 —— 既是避免被封 IP 的技术手段，也是不给对方服务器添麻烦的基本礼貌。
 *   3. Cookie 保持 —— 保住 BAIDUID，能让请求看起来更像真实浏览器，降低触发验证码的概率。
 */
object TiebaHttp {

    /**
     * 移动端 UA。**这个字符串是整个项目里最关键的一行**。
     * 用桌面 UA 访问 /mo/ 系列接口会拿到 SPA 空壳。
     */
    const val MOBILE_UA: String =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    const val REFERER = "https://tieba.baidu.com/"

    /**
     * 两次贴吧请求之间的最小间隔（毫秒）。
     * 个人自用建议 800~1500。调太小会很快吃验证码，调太大会让翻页很慢。
     * 想临时加快可以在设置页里改这个值。
     */
    @Volatile
    var minIntervalMs: Long = 800L

    /** 限速闸门 */
    private val gate = Any()
    private var lastRequestAt = 0L

    private fun awaitSlot() {
        synchronized(gate) {
            val elapsed = System.currentTimeMillis() - lastRequestAt
            val wait = minIntervalMs - elapsed
            if (wait > 0) {
                // 拦截器跑在 OkHttp 自己的线程上，这里的阻塞是刻意为之：
                // 它把并发请求串行化，保证无论业务层怎么并发都不会冲破限速。
                Thread.sleep(wait)
            }
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private class ThrottleInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            awaitSlot()
            return chain.proceed(chain.request())
        }
    }

    /** 极简内存 CookieJar。不落盘：这些接口不需要登录态，丢了也无所谓。 */
    private class MemoryCookieJar : CookieJar {
        private val store = mutableMapOf<String, MutableList<Cookie>>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = store.getOrPut(url.host) { mutableListOf() }
            cookies.forEach { incoming ->
                list.removeAll { it.name == incoming.name }
                list.add(incoming)
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host].orEmpty()
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .cookieJar(MemoryCookieJar())
            .addInterceptor(ThrottleInterceptor())
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", MOBILE_UA)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Referer", REFERER)
                    .header("X-Requested-With", "com.baidu.tieba")
                    .build()
                chain.proceed(req)
            }
            .retryOnConnectionFailure(true)
            .build()
    }
}
