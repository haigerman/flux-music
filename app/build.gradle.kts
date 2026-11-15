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
            // Nur mit dem eigenen Release-Schlüssel signieren, nie still mit dem Debug-Key
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("release")
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

// Release-Build ohne Keystore abbrechen: eine debug-signierte "Release"-APK löst
// Play-Protect-Warnungen aus und lässt sich nicht über die Obtainium-Version updaten.
val hasReleaseKeystore = keystoreFile != null
tasks.configureEach {
    if (name == "packageRelease" || name == "signReleaseBundle") {
        doFirst {
            if (!hasReleaseKeystore) {
                throw GradleException(
                    "Kein Release-Keystore: KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS und KEY_PASSWORD setzen " +
                        "(siehe README, Abschnitt Release). Zum Testen assembleDebug verwenden."
                )
            }
        }
    }
}

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
