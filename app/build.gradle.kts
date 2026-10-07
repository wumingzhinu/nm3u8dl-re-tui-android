import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val vendorAssets = listOf(
    mapOf(
        "name" to "N_m3u8DL-RE",
        "url" to "https://github.com/wumingzhinu/nm3u8dl-re-tui-android/releases/download/vendor-v1/N_m3u8DL-RE",
        "sha256" to "a958e2a0f1d0b6b31dcb2218599c2a38c0e9b664850906e289036bd45d01b31c",
    ),
    mapOf(
        "name" to "ffmpeg",
        "url" to "https://github.com/wumingzhinu/nm3u8dl-re-tui-android/releases/download/vendor-v1/ffmpeg",
        "sha256" to "6e7b1d7d1aa8c35e3fedd78a140aa0968717aeb7386ecfb0ee00773d9f0a4503",
    ),
    mapOf(
        "name" to "libssl.so.3",
        "url" to "https://github.com/wumingzhinu/nm3u8dl-re-tui-android/releases/download/vendor-v1/libssl.so.3",
        "sha256" to "9bf626fadf689237cf641bf1108057f0cc87c587c17fbdeaa16bd4a12807d7ce",
    ),
    mapOf(
        "name" to "libcrypto.so.3",
        "url" to "https://github.com/wumingzhinu/nm3u8dl-re-tui-android/releases/download/vendor-v1/libcrypto.so.3",
        "sha256" to "4122f5c95088f11a43c14e49e257cc1ce700960e855840cf9b9f39a8acbcd7e8",
    ),
)

val nativeDir = layout.projectDirectory.dir("src/main/assets/native")

val fetchVendorAssets by tasks.registering {
    description = "Downloads pinned native runtime assets into assets/native with sha256 verification"
    val cacheDir = layout.buildDirectory.dir("vendor-assets")
    outputs.dir(nativeDir)
    doLast {
        val dest = nativeDir.asFile
        dest.mkdirs()
        val cache = cacheDir.get().asFile
        cache.mkdirs()
        vendorAssets.forEach { asset ->
            val name = asset["name"]!!
            val url = asset["url"]!!
            val expected = asset["sha256"]!!
            val destFile = File(dest, name)
            fun sha256(f: File): String {
                val md = MessageDigest.getInstance("SHA-256")
                f.inputStream().use { input ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        md.update(buf, 0, n)
                    }
                }
                return md.digest().joinToString("") { "%02x".format(it) }
            }
            if (destFile.exists() && sha256(destFile) == expected) {
                logger.lifecycle("asset up-to-date: $name")
                return@forEach
            }
            val cacheFile = File(cache, name)
            if (!cacheFile.exists() || sha256(cacheFile) != expected) {
                logger.lifecycle("downloading $name ...")
                URI(url).toURL().openStream().use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val got = sha256(cacheFile)
            check(got == expected) { "sha256 mismatch for $name: expected $expected got $got" }
            cacheFile.copyTo(destFile, overwrite = true)
            destFile.setExecutable(true, false)
            logger.lifecycle("installed asset: $name")
        }
    }
}

android {
    namespace = "com.nm3u8dl.tui"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nm3u8dl.tui"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "termux-tui-ci"
            keyAlias = System.getenv("KEY_ALIAS") ?: "nm3u8dl"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "termux-tui-ci"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val store = file("../keystore/release.jks")
            signingConfig = if (store.exists()) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    sourceSets.getByName("main") {
        assets.srcDir("src/main/assets")
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(fetchVendorAssets)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
