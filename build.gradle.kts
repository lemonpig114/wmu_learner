// 课伴 (KeBan) 根构建脚本
// AGP 9 内置 Kotlin 支持,不再 apply org.jetbrains.kotlin.android
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
