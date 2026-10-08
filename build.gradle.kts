// 顶层构建文件：仅声明插件，不配置具体模块
// 注意：AGP 9.0+ 内置 Kotlin，不再声明 org.jetbrains.kotlin.android
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
