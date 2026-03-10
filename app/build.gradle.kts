plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("androidx.baselineprofile")
}

import java.util.Properties

android {
    namespace = "com.asmr.player"
    compileSdk = 34

    val earaKeystoreProps =
        Properties().apply {
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.isFile) {
                propsFile.inputStream().use { load(it) }
            }
        }

    val earaReleaseStoreFile =
        System.getenv("EARA_RELEASE_STORE_FILE")
            ?: (project.findProperty("EARA_RELEASE_STORE_FILE") as? String)
            ?: earaKeystoreProps.getProperty("EARA_RELEASE_STORE_FILE")
            ?: "eara-release.jks"
    val earaReleaseStorePassword =
        System.getenv("EARA_RELEASE_STORE_PASSWORD")
            ?: (project.findProperty("EARA_RELEASE_STORE_PASSWORD") as? String)
            ?: earaKeystoreProps.getProperty("EARA_RELEASE_STORE_PASSWORD")
            ?: ""
    val earaReleaseKeyAlias =
        System.getenv("EARA_RELEASE_KEY_ALIAS")
            ?: (project.findProperty("EARA_RELEASE_KEY_ALIAS") as? String)
            ?: earaKeystoreProps.getProperty("EARA_RELEASE_KEY_ALIAS")
            ?: ""
    val earaReleaseKeyPassword =
        System.getenv("EARA_RELEASE_KEY_PASSWORD")
            ?: (project.findProperty("EARA_RELEASE_KEY_PASSWORD") as? String)
            ?: earaKeystoreProps.getProperty("EARA_RELEASE_KEY_PASSWORD")
            ?: ""

    val earaReleaseStoreFileOnDisk = rootProject.file(earaReleaseStoreFile)
    val hasEaraReleaseKeystore =
        earaReleaseStoreFileOnDisk.isFile &&
            earaReleaseStorePassword.isNotBlank() &&
            earaReleaseKeyAlias.isNotBlank() &&
            earaReleaseKeyPassword.isNotBlank()

    signingConfigs {
        create("earaRelease") {
            if (hasEaraReleaseKeystore) {
                storeFile = earaReleaseStoreFileOnDisk
                storePassword = earaReleaseStorePassword
                keyAlias = earaReleaseKeyAlias
                keyPassword = earaReleaseKeyPassword
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    defaultConfig {
        applicationId = "com.asmr.player"
        minSdk = 24
        targetSdk = 34
        versionCode = 10000
        versionName = "1.0.0"
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"eValDoll\"")
        buildConfigField("String", "UPDATE_REPO_NAME", "\"EaraAsmrPlayer\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("earaRelease")
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
            signingConfig = signingConfigs.getByName("earaRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    testOptions {
        unitTests.all {
            it.systemProperty("asmr.latency", (project.findProperty("asmr.latency") as? String).orEmpty())
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val compose_version = "1.6.1"
    val media3_version = "1.2.1"
    val room_version = "2.6.1"
    val hilt_version = "2.49"
    val paging_version = "3.2.1"

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.savedstate:savedstate:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.google.android.material:material:1.11.0")

    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:$media3_version")
    implementation("androidx.media3:media3-session:$media3_version")
    implementation("androidx.media3:media3-ui:$media3_version")
    implementation("androidx.media3:media3-common:$media3_version")

    // Room
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    implementation("androidx.room:room-paging:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Hilt
    implementation("com.google.dagger:hilt-android:$hilt_version")
    ksp("com.google.dagger:hilt-android-compiler:$hilt_version")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Jsoup
    implementation("org.jsoup:jsoup:1.17.2")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Paging 3
    implementation("androidx.paging:paging-runtime-ktx:$paging_version")
    implementation("androidx.paging:paging-compose:$paging_version")

    implementation("androidx.metrics:metrics-performance:1.0.0-beta01")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    baselineProfile(project(":baselineprofile"))

    // Coil (Image Loading)
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
