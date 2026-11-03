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
    compileSdk = 36

    defaultConfig {
        applicationId = "app.omalauncher.trending"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.5.1"
    }

    val allAbis = project.hasProperty("allAbis")

    splits {
        abi {
            isEnable = true
            reset()
            if (allAbis) {
                include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                isUniversalApk = true
            } else {
                include("arm64-v8a")
                isUniversalApk = false
            }
        }
    }

    // The keystore shipped with the source, or production release key if configured
    val releaseStore = System.getenv("TRENDING_KEYSTORE") ?: project.findProperty("trendingKeystore") as String?
    val releaseStorePass = System.getenv("TRENDING_STORE_PASSWORD") ?: project.findProperty("trendingStorePass") as String?
    val releaseKeyAlias = System.getenv("TRENDING_KEY_ALIAS") ?: project.findProperty("trendingKeyAlias") as String?
    val releaseKeyPass = System.getenv("TRENDING_KEY_PASSWORD") ?: project.findProperty("trendingKeyPass") as String?

    signingConfigs {
        create("shipped") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseStore != null && file(releaseStore).exists()) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = releaseStorePass ?: "android"
                keyAlias = releaseKeyAlias ?: "trending"
                keyPassword = releaseKeyPass ?: "android"
            }
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
            signingConfig = if (signingConfigs.findByName("release") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("shipped")
            }
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

val home = System.getProperty("user.home")

// cargo from $CARGO, ~/.cargo, /root/.cargo (the phone build), or PATH.
val cargo = listOfNotNull(System.getenv("CARGO"), "$home/.cargo/bin/cargo", "/root/.cargo/bin/cargo")
    .firstOrNull { File(it).canExecute() } ?: "cargo"

val sdkDir = localProps.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
val ndkDir: File? = System.getenv("ANDROID_NDK_HOME")?.let(::File)?.takeIf { it.isDirectory }
    ?: sdkDir?.let { File(it, "ndk").listFiles()?.filter { dir -> dir.isDirectory }?.maxByOrNull { dir -> dir.name } }

val osName = System.getProperty("os.name").lowercase()
val hostTag = when {
    osName.contains("mac") -> "darwin-x86_64"
    osName.contains("win") -> "windows-x86_64"
    else -> "linux-x86_64"
}
val armHost = System.getProperty("os.arch") in setOf("aarch64", "arm64") && !osName.contains("mac")
val ndkBin = ndkDir?.resolve("toolchains/llvm/prebuilt/$hostTag/bin")
val androidAr: String = ndkBin?.resolve("llvm-ar")?.takeIf { !armHost && it.isFile }?.absolutePath ?: "/usr/bin/llvm-ar"

data class RustTargetConfig(
    val abi: String,
    val rustTarget: String,
    val clangPrefix: String,
)

val allAbis = project.hasProperty("allAbis")
val rustTargets = if (allAbis) {
    listOf(
        RustTargetConfig("arm64-v8a", "aarch64-linux-android", "aarch64-linux-android26-clang"),
        RustTargetConfig("armeabi-v7a", "armv7-linux-androideabi", "armv7a-linux-androideabi26-clang"),
        RustTargetConfig("x86_64", "x86_64-linux-android", "x86_64-linux-android26-clang"),
        RustTargetConfig("x86", "i686-linux-android", "i686-linux-android26-clang"),
    )
} else {
    listOf(
        RustTargetConfig("arm64-v8a", "aarch64-linux-android", "aarch64-linux-android26-clang")
    )
}

val copyTasks = rustTargets.map { target ->
    val buildTask = tasks.register<Exec>("buildTrendingRust_${target.abi}") {
        group = "build"
        description = "Optimized Rust release library for ${target.abi}"
        workingDir = rootProject.file("core")
        inputs.dir(rootProject.file("core/src"))
        inputs.file(rootProject.file("core/Cargo.toml"))
        outputs.file(rootProject.file("core/target/${target.rustTarget}/release/libtrending.so"))

        val clang = ndkBin?.resolve(if (osName.contains("win")) "${target.clangPrefix}.cmd" else target.clangPrefix)
        val linker = if (!armHost && clang?.isFile == true) clang else rootProject.file("core/android-link.sh")
        val targetEnv = target.rustTarget.replace("-", "_").uppercase()
        val targetUnder = target.rustTarget.replace("-", "_")

        environment("PATH", "$home/.cargo/bin:" + (System.getenv("PATH") ?: ""))
        environment("CARGO_TARGET_${targetEnv}_LINKER", linker.absolutePath)
        environment("CC_${targetUnder}", linker.absolutePath)
        environment("CARGO_TARGET_${targetEnv}_AR", androidAr)
        environment("AR_${targetUnder}", androidAr)
        ndkDir?.let { environment("ANDROID_NDK_HOME", it.absolutePath) }
        commandLine(cargo, "build", "--release", "--target", target.rustTarget)
    }

    tasks.register<Copy>("copyTrendingRust_${target.abi}") {
        dependsOn(buildTask)
        from(rootProject.file("core/target/${target.rustTarget}/release/libtrending.so"))
        into(layout.buildDirectory.dir("rustJniLibs/${target.abi}"))
    }
}

val copyAllRust = tasks.register("copyTrendingRust") {
    dependsOn(copyTasks)
}

tasks.register<Exec>("testTrendingRust") {
    group = "verification"
    description = "Host unit tests for the Rust core"
    workingDir = rootProject.file("core")
    commandLine(cargo, "test")
}

tasks.named("preBuild").configure {
    dependsOn(copyAllRust)
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
