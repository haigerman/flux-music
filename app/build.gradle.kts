import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version kommt beim Release aus dem Git-Tag (siehe .github/workflows/release.yml)
val appVersionName = (findProperty("versionName") as String?) ?: "0.0.0-dev"
val appVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1

val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "io.github.haigerman.organicmusic"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.haigerman.organicmusic"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreFile != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Ogg-Dateien unkomprimiert ins APK, damit sie direkt gelesen werden können (openFd)
    androidResources {
        noCompress += "ogg"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            // Länge der WAV-Vorschauen: ./gradlew :app:testDebugUnitTest -PpreviewSeconds=180
            it.systemProperty("previewSeconds", (findProperty("previewSeconds") as String?) ?: "60")
            it.testLogging.showStandardStreams = true
            it.maxHeapSize = "3g"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Quellenangaben der Instrumente (CREDITS.md) auch ins APK packen
val creditsDir = layout.buildDirectory.dir("generated/credits")
val copyCredits by tasks.registering(Copy::class) {
    from(rootProject.file("CREDITS.md"))
    into(creditsDir)
}
android.sourceSets["main"].assets.srcDir(creditsDir)
tasks.named("preBuild") { dependsOn(copyCredits) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-service:2.9.4")

    testImplementation("junit:junit:4.13.2")
}
