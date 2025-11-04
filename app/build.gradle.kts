// file: app/build.gradle.kts
// ============================================================
// ✅ Android App Module — Compose + Whisper.cpp + Asset Safe
// ------------------------------------------------------------
// • Kotlin 2.2.x + Java 17 alignment (Gradle 8.13+)
// • Ensures app/src/main/assets/models/** are packaged in the APK
// • Uses Exec task (no deprecated Project.exec)
// ============================================================

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.Exec
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ------------------------------------------------------------
// 🔧 Initialize native submodule (Exec task; avoids deprecated Project.exec)
// ------------------------------------------------------------
tasks.register<Exec>("checkSubmodule") {
    description = "Recursively initialize the native submodule if not yet set up"

    // Run only when the submodule directory is missing or empty
    onlyIf {
        val dir = layout.projectDirectory.dir("nativelib/whisper_core").asFile
        val empty = !(dir.exists() && dir.listFiles()?.isNotEmpty() == true)
        if (empty) {
            println("🔄 Submodule not initialized or empty. Running: git submodule update --init --recursive")
        } else {
            println("✅ Submodule already initialized.")
        }
        empty
    }

    // Execute from repo root so .git is in scope
    workingDir = rootProject.projectDir
    commandLine("git", "submodule", "update", "--init", "--recursive")
}

// ------------------------------------------------------------
// 🔧 Execute model download script (safe check + permission fix)
// ------------------------------------------------------------
tasks.register<Exec>("downloadModel") {
    description = "Run the model download script safely"
    group = "setup"

    onlyIf {
        val script = file("download_models.sh")
        if (!script.exists()) {
            println("⚠️ download_models.sh not found. Please check its location.")
            return@onlyIf false
        }
        true
    }

    doFirst {
        val script = file("download_models.sh")
        if (!script.canExecute()) {
            println("🔧 Adding execute permission to download_models.sh")
            script.setExecutable(true)
        }
    }

    // Run from project directory so relative paths inside the script work
    workingDir = project.projectDir
    commandLine("bash", "./download_models.sh")
}

// ------------------------------------------------------------
// ✅ Ensure the setup tasks run before preBuild
// ------------------------------------------------------------
tasks.named("preBuild") {
    dependsOn("checkSubmodule", "downloadModel")
}

android {
    namespace = "com.negi.whispers"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.negi.whispers"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ------------------------------------------------------------
    // ✅ Explicit asset sourceSets — ensure models are packaged
    // ------------------------------------------------------------
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
            // Note: assets.srcDirs is a Set<File> (no function call)
            println("✅ Asset dirs: ${assets.srcDirs()}")
        }
    }

    // ------------------------------------------------------------
    // ✅ Build Types
    // ------------------------------------------------------------
    buildTypes {
        getByName("debug") {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // ------------------------------------------------------------
    // ✅ Java 17 / Kotlin 17 configuration
    // ------------------------------------------------------------
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-Xjvm-default=all",
                "-opt-in=kotlin.RequiresOptIn"
            )
        }
    }

    // ------------------------------------------------------------
    // ✅ Compose & Build Features
    // ------------------------------------------------------------
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ------------------------------------------------------------
    // ✅ Packaging settings (resources only)
    // ------------------------------------------------------------
    packaging {
        resources {
            excludes += listOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module"
            )
        }
    }

    // ------------------------------------------------------------
    // ✅ Lint & Unit Test settings
    // ------------------------------------------------------------
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// ============================================================
// ✅ Dependencies — Compose, Whisper JNI, Core libraries
// ============================================================
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(project(":nativelib"))

    // Core AndroidX + Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose UI
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime.saveable)

    // Material Design 3
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.extended)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debug tools
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// ============================================================
// ✅ Diagnostic Task — verify included assets in APK
// ============================================================
tasks.register("printAssets") {
    group = "diagnostic"
    description = "Print all assets included in the APK"
    doLast {
        val assetsDir = project.file("src/main/assets")
        if (assetsDir.exists()) {
            println("📦 Assets under: ${assetsDir.absolutePath}")
            assetsDir.walkTopDown().forEach { f ->
                if (f.isFile) println("  - ${f.relativeTo(assetsDir)} (${f.length()} bytes)")
            }
        } else {
            println("⚠️ No assets directory found!")
        }
    }
}
