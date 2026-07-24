plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.microx.enhancer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.microx.enhancer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.14"
    }

    signingConfigs {
        create("release") {
            // 优先从环境变量读取(GitHub Actions Secrets), 其次 local.properties, 最后默认值
            val storeFilePath = System.getenv("MJH_STORE_FILE") ?: rootProject.file("../keystore/mjh-release.jks").path
            storeFile = file(storeFilePath)
            storePassword = System.getenv("MJH_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("MJH_KEY_ALIAS")?.takeIf { it.isNotEmpty() } ?: "mjh"
            keyPassword = System.getenv("MJH_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Xposed API
    compileOnly("de.robv.android.xposed:api:82")

    // Shizuku API (compileOnly, 模板一致性保留，本模块不主动调用)
    compileOnly("dev.rikka.shizuku:api:13.1.5")
    compileOnly("dev.rikka.shizuku:provider:13.1.5")

    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Compose + Material3
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.ui:ui-graphics:1.5.4")
    implementation("androidx.compose.foundation:foundation:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation("androidx.navigation:navigation-compose:2.7.4")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("com.google.code.gson:gson:2.10.1")
}
