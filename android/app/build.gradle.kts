import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.andyechc.batmonitor"
    compileSdk = 35

    // Release signing from local.properties (gitignored, never committed).
    val keystoreProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) {
            FileInputStream(f).use { load(it) }
        }
    }
    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProps.getProperty("release.storeFile")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = keystoreProps.getProperty("release.storePassword")
                keyAlias = keystoreProps.getProperty("release.keyAlias")
                keyPassword = keystoreProps.getProperty("release.keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.andyechc.batmonitor"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
