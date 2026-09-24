plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mijia4k.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mijia4k.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "1.0.0"
    }

    // AGP's default ~/.android/debug.keystore is unique per machine, so a
    // CI-built APK and a locally-built one are signed differently and won't
    // install over each other ("package conflicts with an existing package").
    // A shared key fixes that — but it's a private key, so it is NOT in the
    // repo: keep it at app/debug.keystore locally, and in CI restore it from
    // the MIJIA_KEYSTORE_B64 secret (see .github/workflows/build.yml).
    //
    // When it's absent the build still works, falling back to AGP's default
    // key — otherwise a fresh clone (or CI without the secret set) couldn't
    // build at all.
    val sharedDebugKey = file("debug.keystore")
    if (sharedDebugKey.exists()) {
        signingConfigs {
            getByName("debug") {
                storeFile = sharedDebugKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
    sourceSets["main"].res.srcDirs("src/main/res")
    sourceSets["main"].manifest.srcFile("src/main/AndroidManifest.xml")

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.ui)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.ui.tooling)
}
