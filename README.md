# 贴吧搜索（个人自用 Android 工具）

在**指定吧内**按关键词搜索帖子，列表展示【发帖人】【时间】【来自XX吧】【帖子内容】。

---

## 一、技术选型：原生 Kotlin + Jetpack Compose（而不是 Flutter）

**结论：用原生 Android（Kotlin），不用 Flutter。**

理由是按这个项目的实际重心排序的：

| 维度 | 原生 Kotlin | Flutter | 本项目谁赢 |
|---|---|---|---|
| 数据抓取生态 | OkHttp + Jsoup + kotlinx.serialization，JVM 上这套组合是最成熟的爬虫工具链 | Dart 侧 http/dio + html 包，能用但生态明显薄 | **原生** |
| 反爬/异常处理 | 直接控制 UA、Cookie、TLS、拦截器、限速 | 要写 Platform Channel 才能碰到底层网络栈 | **原生** |
| UI 开发效率 | Compose 的声明式写法和 Flutter 基本等价 | 略好一点点 | 平 |
| 需求规模 | 2 个输入框 + 1 个列表 | 同样简单 | 平 |
| 调试爬虫 | 能直接 `adb logcat` / 断点看原始响应 | 跨了一层，排查更绕 | **原生** |
| 包体积 | ~8MB | ~20MB+ | **原生** |
| 以后要上 iOS | 得重写 | 直接复用 | Flutter |

**决定性因素是最后两行之外的这一条**：这个 App 的难点 100% 在网络与解析层，UI 复杂度基本为零。
Flutter 只在「UI 复杂 + 需要双端」时才划算，而这里两条都不成立。

> 什么时候该改用 Flutter：如果你以后确定要出 iOS 版，且愿意为了复用 UI 而接受
> 在 Dart 里重写整套抓取逻辑、并在遇到反爬时更难受的调试体验。

---

## 二、数据抓取方案（本次已实测验证，不是猜的）

### 结论：用移动版 JSON 接口，**不需要**签名、不需要登录、不需要 Cookie

```
GET https://tieba.baidu.com/mo/q/search/thread?word={关键词}&ie=utf-8&rn=30&pn={页码-1}
```

实测返回 `application/json`（HTTP 200），一次请求就拿到全部所需字段：

| 你的需求 | JSON 字段 | 说明 |
|---|---|---|
| 帖子内容 | `title` + `content` | 搜索结果的粒度是**楼层**，同一 tid 可能重复出现，代码里按 `pid` 去重 |
| 发帖人用户ID | `user.user_id` | 数字 UID，永久唯一 ✅ |
| 发帖人用户名 | `user.user_name` | 登录名，**实测经常是空字符串**（用户开了隐私） |
| 发帖人昵称 | `user.show_nickname` | 界面上应主显示这个，用户才认得出 |
| 发帖时间 | `time` / `create_time` | Unix 秒，精确 ✅（另有 `modified_time`） |
| 来源吧名 | `forum_name` + `forum_id` | 界面上的【来自XX吧】 |
| 分页 | `data.has_more`（1/0）+ `data.current_page` | 翻页终止条件 |

### ⚠️ 三个必须知道的实测坑

**坑 1：这个接口的 `kw` 参数不生效 —— 它是「全吧搜索」。**
我实测传 `kw=北京`，返回结果里却是「反激女吧 / 文学吧 / 李毅吧 / 修罗武神吧」的帖子，跟北京毫无关系。
所以「吧内搜索」的**正确实现方式是：请求全吧搜索 → 用返回的 `forum_name` 做本地过滤**。
代价是严格过滤后一页 30 条可能只剩 2 条，所以 `TiebaRepository` 里加了「向后再追 4 页」的前瞻逻辑，
UI 上也会把「过滤掉了几条」明确显示出来。

**坑 2：桌面版接口已经全部 SPA 化，抓不到东西。**
实测这些 URL 的正文是空的（Jsoup 会一无所获），加 `mo_device=1` 也救不回来：

- ❌ `https://tieba.baidu.com/f/search/res?kw={吧名}&qw={关键词}` ← 网上大量老教程还在教这个
- ❌ `https://tieba.baidu.com/p/{tid}`

**必须伪装移动端 UA**（见 `TiebaHttp.MOBILE_UA`，这是全项目最关键的一行）。

**坑 3：你举的例子「诡秘之主吧」在移动版被限制。**
实测 `https://tieba.baidu.com/mo/q/m?kw=诡秘之主吧` 返回的是
「抱歉，根据相关法律法规和政策，本吧暂不开放」。JSON 搜索接口或许仍能命中它的帖子，
但**请先用 App 试一下这个吧**，如果目标吧本身被限制，那不是代码能解决的问题。

### 关于「国内开源且长期维护的贴吧 API 库」

直说：**Java/Kotlin 生态里没有长期维护的贴吧库。** 我不会推荐一个不存在的库给你。

- 维护得最好的是 Python 的 **`aiotieba`**（作者 Starry-OvO）。它是目前贴吧协议的权威参考实现，
  包含签名算法、protobuf 结构、各接口字段定义。**但它是 Python，Android 上不能直接用。**
  （我本次无法联网核实它的最新状态，用之前请自行确认。）
- 其它能找到的 Java/Kotlin 贴吧项目基本都停更在 2019 年前后，协议早就对不上了。

**好消息是：本项目根本不需要这个库。** 上面那个 JSON 接口把需求全覆盖了。
客户端签名 API（`c.tieba.baidu.com/c/f/...`，需要 `sign = MD5(排序后参数串 + "tiebaclient!!!")`
以及部分接口的 protobuf 解析）是这个项目**最大的可维护性陷阱**，能绕开就绕开。
代码里保留了完整的注释说明它是什么、为什么不用，将来真要加也知道从哪下手。

### 官方接口呢？

百度开放平台确实有贴吧相关的 API，但需要企业开发者资质申请 key，个人拿不到，
且配额和权限受限。**个人自用场景下，走移动版公开页面/接口是更现实的选择。**

---

## 三、三级降级策略

```
第 1 级  /mo/q/search/thread  JSON 全吧搜索 → 按 forum_name 本地过滤   ← 主路径，字段最全
   │        （某一页命中太少 → 自动向后追最多 4 页）
   │  失败 / 被风控
   ▼
第 2 级  /mo/q/m?kw={吧名}&pn=N  HTML 吧内主题翻页 → 标题关键词匹配
   │
   ▼
第 3 级  /mo/q/m?kz={tid}&pn=N  HTML 楼层页 → 补全正文/作者/精确时间
```

风控（返回百度安全验证页而不是数据）会抛 `TiebaBlockedException`，**不会**被静默降级掩盖 ——
这种情况下 UI 会明确提示你调大请求间隔。

---

## 四、文件结构

```
D:\tieba\
├── build.gradle.kts                  根构建脚本（版本集中管理）
├── settings.gradle.kts               含阿里云镜像加速
├── gradle.properties
└── app\
    ├── build.gradle.kts
    ├── proguard-rules.pro            含 kotlinx.serialization 的 keep 规则
    └── src\main\
        ├── AndroidManifest.xml
        ├── res\values\themes.xml
        └── java\com\example\tiebasearch\
            ├── MainActivity.kt
            │
            ├── data\
            │   ├── remote\
            │   │   ├── TiebaHttp.kt              ★ OkHttp 配置：UA / 限速 / Cookie
            │   │   ├── TiebaApi.kt               ★ 接口定义 + 全部实测结论记录在注释里
            │   │   └── dto\
            │   │       ├── TiebaDto.kt           ★ JSON 响应模型（对齐实测字段）
            │   │       └── FlexibleSerializers.kt ★ 应对「同一字段有时数字有时字符串」
            │   ├── parser\
            │   │   └── MoHtmlParser.kt           ★ HTML 兜底解析（不依赖具体 class 名）
            │   ├── mapper\
            │   │   └── PostMapper.kt             DTO → 领域模型
            │   └── repository\
            │       └── TiebaRepository.kt        ★ 三级策略编排
            │
            ├── domain\model\
            │   └── TiebaPost.kt                  领域模型 + SearchQuery
            │
            ├── ui\
            │   ├── search\
            │   │   ├── SearchViewModel.kt        ★ 状态管理 + 分页 + 自检导出
            │   │   └── SearchScreen.kt           ★ Compose 界面（含结果卡片）
            │   └── theme\Theme.kt
            │
            └── util\
                ├── TimeFormat.kt                 解析贴吧的 4 种时间格式
                └── DebugDumper.kt                接口自检：一键导出原始响应
```

★ = 核心文件。如果只看一个，看 `TiebaRepository.kt`；如果只看两个，加 `TiebaApi.kt`。

---

## 五、编译运行

```bash
# 用 Android Studio 直接打开 D:\tieba
# 或命令行：
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

环境要求：JDK 17、Android SDK 35、AGP 8.7.2、Kotlin 2.0.21。

---

## 六、接口改版了怎么办（这个项目最重要的运维知识）

爬虫项目的死因永远是「对方改了，而你不知道改成什么样」。
所以 App 右上角有个 **🐞 按钮 —— 一键导出原始响应**：

1. 复现问题（比如搜索返回空）
2. 点 🐞，会弹出导出路径
3. 去 `内部存储/Android/data/com.example.tiebasearch/files/dump/` 拿文件

对着原始响应，你能立刻分辨出是哪种情况：

| 看到什么 | 结论 | 改哪里 |
|---|---|---|
| 文件以 `<!DOCTYPE html>` 开头且含「验证」 | 被风控了 | 调大 `TiebaHttp.minIntervalMs` |
| JSON 结构变了（字段改名/嵌套调整） | 接口改版 | `dto/TiebaDto.kt` 的 `@SerialName` |
| `no != 0` | 接口报错 | 看 `error` 字段 |
| HTML 但解析结果为空 | 页面结构变了 | `MoHtmlParser.guessAuthor()` 等启发式函数 |

---

## 七、需要你自己确认的两件事（我没法在这台机器上验证）

我只验证了**首页**，这两条留了明确的调试出口：

1. **分页参数 `pn` 的语义。** 首页返回 `current_page: 1`（1-based 页号语义），
   但贴吧 `/mo/` 系列在别处用 `pn` 表示偏移量。代码现在传的是 `pn = page - 1`。
   如果翻页错位，把 `TiebaApi.searchThreads()` 里那一行改成 `pn = page` 即可，
   注释里已经标出来了。
2. **`kw` 是否在所有部署下都不生效。** 代码仍然会把吧名作为 `kw` 发出去（万一某处生效呢），
   但**不依赖它** —— 真正的过滤在客户端做。所以即使 `kw` 生效了，结果也是对的。

---

## 八、合规与风控（不是废话，直接影响你能不能用起来）

- **只抓公开数据、只自己看。** 不要商用、不要二次分发、不要把抓到的用户 ID 汇总成数据集。
- **限速是硬要求。** 默认 800ms/请求。这是保护你自己不被封 IP 的最有效手段，
  也是对百度服务器最基本的礼貌。要更快只会在几小时后吃验证码。
- **遵守 robots.txt。** 百度用户协议是禁止爬取的，个人小工具的实际风险主要来自风控（验证码/封 IP），
  而不是法律；但请让请求量保持在「一个人手动翻页」的量级。
- **别存敏感数据。** 本项目不做任何持久化存储，每次搜索都是即时的。
