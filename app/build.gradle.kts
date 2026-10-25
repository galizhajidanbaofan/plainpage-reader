import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 起 Kotlin 支持内置，不再需要 org.jetbrains.kotlin.android 插件。
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.galizhajidanbaofan.plainpage"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.galizhajidanbaofan.plainpage"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("zh", "en")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ""
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 发布签名由本机 keystore.properties 提供，绝不入库
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }

    testOptions {
        // Robolectric 需要真实的资源与 manifest 才能把 Compose 渲染成像素。
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // 离屏截图（Roborazzi）默认不写文件，这里固定成「记录」模式。
            test.systemProperty("roborazzi.test.record", "true")
            test.systemProperty("roborazzi.test.verify", "false")
            // Robolectric 默认把 android-all jar 下到用户目录并在那里建锁文件，本机写
            // C:\Users\<user> 被拦；改成离线模式，jar 预先放进工作区的 .toolchain/robolectric。
            test.systemProperty("robolectric.offline", "true")
            test.systemProperty(
                "robolectric.dependency.dir",
                "C:/Users/19853/Documents/deepseek-harness/default-workspace/.toolchain/robolectric",
            )
            test.maxHeapSize = "2g"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xjvm-default=all")
    }
}

ksp {
    // 导出 Room schema：数据库升级时才有据可查（提交进仓库，供 migration 测试使用）。
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.kotlin.test.junit)
}
