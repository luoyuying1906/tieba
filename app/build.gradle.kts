plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.example.tiebasearch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tiebasearch"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    // --- 基础 ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // --- Compose ---
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // 刻意不引入 material-icons-extended：
    // 该依赖在部分 Compose BOM 版本中已被移除，会出现 "Could not find ...:material-icons-extended:" 的
    // 依赖解析失败。本项目只用了一个图标，改用 material3 自带的核心图标即可。
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- 网络 ---
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // --- JSON：必须配 serialization 插件（见根 build.gradle.kts）---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // --- HTML 兜底解析 ---
    implementation("org.jsoup:jsoup:1.18.1")

    // --- 头像加载 ---
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
