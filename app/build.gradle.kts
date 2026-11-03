import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

android {
    namespace = "app.omalauncher.trending"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.omalauncher.trending"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "1.5.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // The keystore shipped with the source, so a rebuilt APK updates the installed one.
    signingConfigs {
        create("shipped") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("shipped")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shipped")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    lint { checkReleaseBuilds = false }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs { useLegacyPackaging = false }
    }

    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("rustJniLibs"))
}

val rustTarget = "aarch64-linux-android"
val home = System.getProperty("user.home")

// cargo from $CARGO, ~/.cargo, /root/.cargo (the phone build), or PATH.
val cargo = listOfNotNull(System.getenv("CARGO"), "$home/.cargo/bin/cargo", "/root/.cargo/bin/cargo")
    .firstOrNull { File(it).canExecute() } ?: "cargo"

val sdkDir = localProps.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
val ndkDir: File? = System.getenv("ANDROID_NDK_HOME")?.let(::File)?.takeIf { it.isDirectory }
    ?: sdkDir?.let { File(it, "ndk").listFiles()?.filter { dir -> dir.isDirectory }?.maxByOrNull { dir -> dir.name } }

// The NDK ships x86-64 host tools. On an aarch64 machine they cannot run, so
// android-link.sh drives the host clang against the NDK sysroot instead.
val osName = System.getProperty("os.name").lowercase()
val hostTag = when {
    osName.contains("mac") -> "darwin-x86_64"
    osName.contains("win") -> "windows-x86_64"
    else -> "linux-x86_64"
}
val armHost = System.getProperty("os.arch") in setOf("aarch64", "arm64") && !osName.contains("mac")
val ndkBin = ndkDir?.resolve("toolchains/llvm/prebuilt/$hostTag/bin")
val ndkClang = ndkBin?.resolve(if (osName.contains("win")) "aarch64-linux-android26-clang.cmd" else "aarch64-linux-android26-clang")
val androidLinker: File = if (!armHost && ndkClang?.isFile == true) ndkClang else rootProject.file("core/android-link.sh")
val androidAr: String = ndkBin?.resolve("llvm-ar")?.takeIf { !armHost && it.isFile }?.absolutePath ?: "/usr/bin/llvm-ar"

tasks.register<Exec>("buildTrendingRust") {
    group = "build"
    description = "Optimized Rust release library for arm64 Android"
    workingDir = rootProject.file("core")
    inputs.dir(rootProject.file("core/src"))
    inputs.file(rootProject.file("core/Cargo.toml"))
    inputs.file(rootProject.file("core/android-link.sh"))
    outputs.file(rootProject.file("core/target/$rustTarget/release/libtrending.so"))
    environment("CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER", androidLinker.absolutePath)
    environment("CC_aarch64_linux_android", androidLinker.absolutePath)
    environment("CARGO_TARGET_AARCH64_LINUX_ANDROID_AR", androidAr)
    environment("AR_aarch64_linux_android", androidAr)
    ndkDir?.let { environment("ANDROID_NDK_HOME", it.absolutePath) }
    commandLine(cargo, "build", "--release", "--target", rustTarget)
}

tasks.register<Exec>("testTrendingRust") {
    group = "verification"
    description = "Host unit tests for the Rust core"
    workingDir = rootProject.file("core")
    commandLine(cargo, "test")
}

tasks.register<Copy>("copyTrendingRust") {
    dependsOn("buildTrendingRust")
    from(rootProject.file("core/target/$rustTarget/release/libtrending.so"))
    into(layout.buildDirectory.dir("rustJniLibs/arm64-v8a"))
}

tasks.named("preBuild").configure {
    dependsOn("copyTrendingRust")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
