import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.lifebutler.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lifebutler.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 24
        versionName = "2.11"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 接入 Filament(真 3D)之后,native 库有 arm64-v8a / armeabi-v7a / x86 / x86_64 四份,
            // 全带上会让安装包凭空多十几 MB。minSdk 26 的机器几乎都是 arm64,release 只留这一份。
            // debug 不限制,模拟器(x86_64)要能装来实测。
            ndk { abiFilters += listOf("arm64-v8a") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // 真 3D:Filament 渲染 + glTF 加载,Compose 原生 API。v2.6 起智能管家页的机器人是模型不是图片。
    // 卡在 4.18.0:4.20+ 用 kotlin-stdlib 2.4 编译(元数据 2.4,本项目 Kotlin 2.2.10 读不了),4.35+ 还要求 compileSdk 37。
    implementation("io.github.sceneview:sceneview:4.18.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
