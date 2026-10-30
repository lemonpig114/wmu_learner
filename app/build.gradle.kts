plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wmu.keban"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.wmu.keban"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.2.5"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // 供界面显示当前版本号(交付的每个 APK 都要能自查版本)
        buildConfig = true
    }
}

// Room schema 导出(迁移用:生成的 JSON 是迁移 SQL 的权威依据)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// 单元测试输出回显(解析调试需要)
tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.material.icons.core)

    // PDF 文本提取(第二阶段:课表解析)
    implementation(libs.pdfbox.android)

    // 单元测试:桌面版 pdfbox 与 App 内 pdfbox-android 同源(2.0.x),用于验证解析核心
    testImplementation(libs.junit)
    testImplementation(libs.pdfbox.desktop)
}
