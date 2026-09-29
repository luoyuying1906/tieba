pluginManagement {
    repositories {
        // ⚠️ 顺序很重要，Gradle 会按顺序找，网络不通会直接失败而不是跳到下一个。
        // 现在的顺序是为「GitHub Actions（境外机器）」优化的。
        // 如果你以后改成在自己电脑上编、且没有代理，把下面三行 aliyun 挪到最前面。
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "TiebaSearch"
include(":app")
