package com.nm3u8dl.tui.core

import android.content.Context
import java.io.File

class NativeAssets(private val context: Context) {

    data class Paths(
        val binDir: File,
        val engine: File,
        val ffmpeg: File,
        val libDir: File,
    )

    fun ensureInstalled(): Paths {
        val base = File(context.filesDir, "runtime").apply { mkdirs() }
        val binDir = File(base, "bin").apply { mkdirs() }
        val libDir = File(base, "lib").apply { mkdirs() }

        val engine = File(binDir, "N_m3u8DL-RE")
        val ffmpeg = File(binDir, "ffmpeg")
        val ssl = File(libDir, "libssl.so.3")
        val crypto = File(libDir, "libcrypto.so.3")

        val manifest = File(base, "installed.txt")
        val stamp = listOf(engine, ffmpeg, ssl, crypto).joinToString(",") {
            "${it.name}:${it.length()}"
        }
        val alreadyOk = manifest.exists() &&
            engine.length() > 1_000_000 &&
            ffmpeg.length() > 1_000_000 &&
            ssl.length() > 100_000 &&
            crypto.length() > 100_000 &&
            manifest.readText() == stamp

        if (!alreadyOk) {
            copyAsset("native/N_m3u8DL-RE", engine, executable = true)
            copyAsset("native/ffmpeg", ffmpeg, executable = true)
            copyAsset("native/libssl.so.3", ssl, executable = false)
            copyAsset("native/libcrypto.so.3", crypto, executable = false)
            manifest.writeText(stamp)
        }

        return Paths(binDir, engine, ffmpeg, libDir)
    }

    private fun copyAsset(assetPath: String, dest: File, executable: Boolean) {
        context.assets.open(assetPath).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        if (executable) {
            dest.setReadable(true, false)
            dest.setExecutable(true, false)
        } else {
            dest.setReadable(true, false)
        }
    }
}