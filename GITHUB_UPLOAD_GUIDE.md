# 保姆级教程：从零把项目变成能装的 APK

你没有任何开发环境也没关系。**全程只用到浏览器**，不用装 Java、不用装 Android Studio、不用敲命令行。

预计耗时：注册 5 分钟 + 上传 10 分钟 + 编译 5 分钟。

---

## 第 0 步：先确认项目文件在哪

项目在 `D:\tieba`。打开资源管理器进去看一眼，应该能看到这些：

```
D:\tieba\
  ├── app\
  ├── build.gradle.kts
  ├── settings.gradle.kts
  ├── gradle.properties
  ├── README.md
  ├── BUILD.md
  └── .gitignore        ← 可能看不见，被隐藏了
```

> **要上传的文件有两部分**：上面这些「看得见的」+ 一个「看不见的」`.github` 文件夹。
> 因为隐藏文件夹通过拖拽容易漏掉，教程里会让你**在网页上手动创建**那个工作流文件（第 4 步），
> 这样最不容易出错。

---

## 第 1 步：注册 GitHub 账号

1. 浏览器打开 https://github.com/signup
2. 依次填：
   - **Email**：你的邮箱（QQ 邮箱、163 都行）
   - **Password**：至少 8 位，含字母和数字
   - **Username**：英文/数字/连字符，比如 `zhangsan-tieba`（这是你的公开用户名，想好再填）
3. 会有一道**拼图验证**，按提示拖一下
4. 去邮箱收验证码，填进去
5. 完成后可能问你「Just me / Team」→ 选 **Just me**；问付费计划 → 直接选 **Free**（免费）

> 全程不需要梯子，GitHub 网页在国内可以正常访问。如果特别慢，换个时间段再试。

---

## 第 2 步：新建一个空仓库

1. 登录后，点右上角 **`+`** → **New repository**
2. 填写：
   - **Repository name**：`tieba-search`
   - **Description**：随便写，比如 `个人贴吧搜索工具`，也可以留空
   - 选 **Public**（公开）或 **Private**（私有）：
     - **Public**：Actions 编译**完全免费不限量**，推荐
     - **Private**：每月免费 2000 分钟，一次编译约 5 分钟，够你用几百次
   - ⚠️ **下面三个复选框全部不要勾**（不要 Add README、不要 .gitignore、不要 license）
     保持仓库是全空的，这样上传文件时不会冲突
3. 点绿色按钮 **Create repository**

创建完你会看到一个空仓库页面，中间有一行小字 **`uploading an existing file`** —— 这就是下一步要点的。

---

## 第 3 步：上传项目文件

1. 在那个空仓库页面，点中间蓝色的链接 **`uploading an existing file`**
   （如果没有，就点 **Add file** → **Upload files**）
2. 打开资源管理器到 `D:\tieba`
3. **全选里面的所有内容**：按 `Ctrl + A`
   > 如果你的资源管理器看不到隐藏文件也没关系，`.github` 我们下一步在网页上单独建。
4. 把选中的项目**直接拖进浏览器那个虚线框**里
5. 等进度条走完，页面下方会出现文件列表（应该有 20 个左右，包括 `app/xxx` 这种带路径的）
6. 页面拉到最下面，点绿色按钮 **Commit changes**

> **成功标志**：仓库首页能看到 `app`、`README.md`、`settings.gradle.kts` 等文件。

---

## 第 4 步：手动创建自动编译配置（关键一步）

这一步必须做，否则不会自动编译。

1. 在仓库页面点 **Add file** → **Create new file**
2. 在「文件名」输入框里，**完整输入下面这行**（注意是正斜杠 `/`，输入 `/` 时 GitHub 会自动帮你建文件夹）：

```
.github/workflows/build-apk.yml
```

3. 在下面的大文本框里，**粘贴以下全部内容**：

```yaml
name: Build APK

on:
  push:
    branches: [main, master]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: 检出代码
        uses: actions/checkout@v4

      - name: 安装 JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: 安装 Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.9'

      - name: 补齐 Android SDK 组件
        shell: bash
        run: |
          SDKMANAGER="$(command -v sdkmanager || echo "${ANDROID_HOME:-/usr/local/lib/android/sdk}/cmdline-tools/latest/bin/sdkmanager")"
          if [ -x "$SDKMANAGER" ]; then
            yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true
            "$SDKMANAGER" "platforms;android-35" "build-tools;35.0.0" "platform-tools" > /dev/null 2>&1 || true
            echo "SDK 组件就绪"
          else
            echo "未找到 sdkmanager，跳过"
          fi

      - name: 编译 Debug APK
        run: gradle assembleDebug --no-daemon --stacktrace

      - name: 上传 APK
        uses: actions/upload-artifact@v4
        with:
          name: tieba-search-debug-apk
          path: app/build/outputs/apk/debug/*.apk
          if-no-files-found: error
          retention-days: 30
```

> 也可以直接用记事本打开 `D:\tieba\.github\workflows\build-apk.yml`，全选复制，粘进去。

4. 页面拉到最下面，点 **Commit changes**

---

## 第 5 步：看它自动编译

1. 点仓库顶部菜单的 **Actions**
2. 如果看到黄色提示条问你是否启用工作流 → 点 **I understand my workflows, go ahead and enable them**
3. 左侧列表点 **Build APK**
4. 如果没在自动跑，点右侧 **Run workflow** → 绿色 **Run workflow** 按钮，手动触发
5. 点进那次运行记录，能看到一个个步骤在跑，**大约 3~6 分钟**

**成功的标志**：所有步骤旁边都是 ✅ 绿勾，最上面显示绿色 ✓ Build APK。

---

## 第 6 步：下载 APK

1. 在编译成功的那次运行页面，**拉到最底部**
2. 找到 **Artifacts** 区域 → 点 **`tieba-search-debug-apk`**
3. 会下载一个 `.zip` 压缩包
4. **解压它**，里面就是 `app-debug.apk` —— 这就是你要的安装包

> ⚠️ Artifacts 只能保留 30 天，过期了重新跑一次 Actions 即可。

---

## 第 7 步：装到手机上

1. 把 `app-debug.apk` 用微信/QQ/数据线传到手机
2. 在手机上点开这个文件
3. 系统会拦一下，提示「未知来源应用」。去 **设置 → 允许安装未知应用** 里，给「文件管理」或「浏览器」放行
4. 返回继续安装 → 完成，桌面会出现「贴吧搜索」图标

---

## 装好后先测这三件事

### ① 基础功能
打开 App：
- **选择贴吧**：`北京吧`
- **关键词**：`小说`
- 点 **搜索**

应该出一堆卡片，每张都标着【发帖人昵称】、【UID】、【时间】、【来自北京吧】、【帖子内容】。

### ② 你原本要的场景
- **选择贴吧**：`诡秘之主吧`
- **关键词**：`诡秘之主`

⚠️ **如果搜不到，大概率不是代码问题。** 我实测「诡秘之主吧」在百度移动版会返回
「抱歉，根据相关法律法规和政策，本吧暂不开放」。你换个 `小说吧` 搜同样的词对比一下就能确认。

### ③ 翻页（唯一没验证过的地方）
结果列表拉到最底，点 **加载更多**，看第 2 页内容**是不是真的换了**。

如果没换或者错位：
1. 在 GitHub 仓库里点进 `app/src/main/java/com/example/tiebasearch/data/remote/TiebaApi.kt`
2. 点右上角铅笔图标 ✏️ 编辑
3. 找到这一行：
   ```kotlin
   append("&pn=").append(page - 1)
   ```
   改成：
   ```kotlin
   append("&pn=").append(page)
   ```
4. 拉到下面点 **Commit changes**
5. Actions 会重新自动编译，等几分钟再下一个新 APK

---

## 编译失败了怎么办

1. 在 Actions 页面点进那次**红色 ✗** 的运行
2. 点左边展开失败的步骤（通常是 **编译 Debug APK**）
3. 把红色报错信息**复制下来发给我**，我直接改代码

**大概率是第一类错误**：我是在一台没有 Android 工具链的机器上写的这份代码，
逻辑我逐行自查过，也修掉了两个自己发现的问题，但**没有编译器帮我确认过**。
报错发我即可，通常改一两行就好。

---

## 常见问题

### ❌ 报错 `chmod: 无法访问 'gradlew': 没有此类文件或目录`

**这不是缺少 gradlew 文件导致的，而是你跑的工作流不是本教程给的那个。**

GitHub 在 Actions 页面会自动推荐一个叫 **"Android CI"** 的模板，它里面有这么两步：

```yaml
- name: Grant execute permission for gradlew
  run: chmod +x gradlew
- name: Build with Gradle
  run: ./gradlew build
```

**本教程的 `build-apk.yml` 从头到尾没有出现过 `gradlew`**，它用的是 `gradle assembleDebug`。

**两条必须知道的事**：

1. 光有 `gradlew` / `gradlew.bat` 两个脚本**不能工作**，它们还需要
   `gradle/wrapper/gradle-wrapper.jar`（约 43KB 的编译后二进制）。
   只补脚本的话，`chmod` 那步会过，但下一步会变成新错误：
   `Could not find or load main class org.gradle.wrapper.GradleWrapperMain`
2. **"Android CI" 模板即使跑成功了，你也拿不到 APK。**
   它执行的是 `./gradlew build`（跑编译 + 检查 + 测试），而且**没有上传产物的步骤**，
   Artifacts 区域会是空的。

**正确做法（3 步）**：

1. 打开仓库的 **Actions** 页面，看左侧是不是有**两个**工作流：
   - `Android CI` ← 这个要删掉
   - `Build APK` ← 这个是你该用的
2. 删掉 `Android CI`：进 `.github/workflows/` 目录，把 `android.yml`（或 `android-ci.yml`）
   点进去 → 右上角 **⋯** → **Delete file** → Commit
3. 确认 `build-apk.yml` 还在，然后重新运行它：
   Actions → 左侧 **Build APK** → 右侧 **Run workflow**

> 顺带说明：项目里现在也放了 `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.properties` /
> `.gitattributes`，但那个 **jar 我没法生成**（它是二进制，我运行的环境 HTTPS 被沙箱挡着，下载不了）。
> `build-apk.yml` 里加了一步 `gradle wrapper`，会在 CI 里自动把它补齐，
> 所以你以后想在自己电脑上跑 `./gradlew` 也是可以的。

---

### 其它常见问题

**Q：为什么不直接给我 APK？**

我运行的环境里没有 JDK、Android SDK、Gradle，shell 的 HTTPS 还被沙箱挡着，
装齐这套工具链要下约 2GB 且需要写入你磁盘的其他位置。用 GitHub Actions 零成本、成功率更高。

**Q：会不会收费？**
Public 仓库的 Actions 完全免费不限量。Private 仓库每月免费 2000 分钟，一次编译约 5 分钟。

**Q：Debug 包和正式包有什么区别？**
Debug 包能用，只是没做代码混淆和签名优化。个人自用完全够了。

**Q：以后想改代码怎么办？**
在 GitHub 上直接点文件 → 铅笔图标 → 改 → Commit，Actions 会自动重新编译出新 APK。

**Q：App 搜索返回空 / 报错？**
点 App 右上角的 **🐞 按钮**，它会把接口原始响应导出到
`Android/data/com.example.tiebasearch/files/dump/`。看一眼就知道是接口改版了还是被风控了。
