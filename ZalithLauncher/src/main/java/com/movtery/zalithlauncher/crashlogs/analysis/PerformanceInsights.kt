/*
 * Zalith Launcher 2
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.crashlogs.analysis

import com.movtery.zalithlauncher.utils.logging.GameLogArchiver
import java.io.File
import java.io.RandomAccessFile

/** Ringkasan satu sesi game dari baris [NusaFrameStats] di log arsip. */
data class SessionSummary(
    val fileName: String,
    val timeMillis: Long,
    val versionName: String?,
    val renderer: String,
    /** Jumlah jendela 5 detik setelah fase loading */
    val windows: Int,
    val avgFps: Float,
    val low1Fps: Float,
    /** Median jumlah sentakan per jendela 5 detik */
    val stutters: Float,
    /** Waktu frame terburuk (1% low) tiap jendela, dalam ms, dari yang tertua */
    val worstFrameMs: List<Float>
)

data class RendererRecommendation(
    val renderer: String,
    val low1Fps: Float,
    val otherRenderer: String,
    val otherLow1Fps: Float
)

/**
 * NusaLauncher: membaca log arsip (NusaLogs-*.log) untuk ringkasan performa dan rekomendasi renderer.
 * Kotlin murni, tanpa Android, supaya bisa diuji.
 */
object PerformanceInsights {
    private val statRegex = Regex(
        "\\[NusaFrameStats\\] frames=(\\d+) avg=([\\d.]+)fps low1pct=([\\d.]+)fps median=([\\d.]+)ms max=([\\d.]+)ms jitter=([\\d.]+)ms stutters=(\\d+)"
    )

    private const val FREEZE_MS = 1000f
    private const val HEAD_BYTES = 2048
    private const val TAIL_BYTES = 600 * 1024
    private const val MIN_WINDOWS_FOR_RECOMMENDATION = 6
    private const val MIN_RELATIVE_GAIN = 1.10f

    private class Window(val avg: Float, val low1: Float, val maxMs: Float, val stutters: Float)

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2f
    }

    private fun headerValue(text: String, key: String): String? {
        val prefix = key + ": "
        val head = text.take(HEAD_BYTES)
        return head.lineSequence().firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * @param text isi log (awal dan akhir sudah cukup)
     * @return null jika belum ada jendela yang cukup setelah fase loading
     */
    fun summarize(text: String, fileName: String, timeMillis: Long): SessionSummary? {
        val all = statRegex.findAll(text).map { m ->
            val g = m.groupValues
            Window(g[2].toFloat(), g[3].toFloat(), g[5].toFloat(), g[7].toFloat())
        }.toList()
        if (all.isEmpty()) return null

        val lastFreeze = all.indexOfLast { it.maxMs > FREEZE_MS }
        val steady = all.drop(lastFreeze + 1)
        if (steady.size < 2) return null

        val renderer = headerValue(text, "Renderer")
            ?: GameLogArchiver.parseName(fileName)?.renderer
            ?: "Unknown"

        return SessionSummary(
            fileName = fileName,
            timeMillis = timeMillis,
            versionName = headerValue(text, "Minecraft version"),
            renderer = renderer,
            windows = steady.size,
            avgFps = median(steady.map { it.avg }),
            low1Fps = median(steady.map { it.low1 }),
            stutters = median(steady.map { it.stutters }),
            worstFrameMs = steady.takeLast(24).map { 1000f / it.low1.coerceAtLeast(1f) }
        )
    }

    /** Membaca awal dan akhir file saja agar file besar tetap cepat. */
    private fun readSample(file: File): String {
        val length = file.length()
        if (length <= TAIL_BYTES + HEAD_BYTES) return file.readText()
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(HEAD_BYTES).also { raf.seek(0); raf.readFully(it) }
            val tail = ByteArray(TAIL_BYTES).also { raf.seek(length - TAIL_BYTES); raf.readFully(it) }
            return String(head, Charsets.UTF_8) + "\n" + String(tail, Charsets.UTF_8)
        }
    }

    /** Memuat ringkasan sesi dari arsip, terbaru lebih dulu. */
    fun load(dir: File, limit: Int = 10): List<SessionSummary> {
        val files = dir.listFiles()?.filter { it.isFile && GameLogArchiver.isArchiveName(it.name) } ?: return emptyList()
        return files
            .map { it to (GameLogArchiver.parseName(it.name)?.timeMillis ?: it.lastModified()) }
            .sortedByDescending { it.second }
            .take(limit)
            .mapNotNull { (f, t) -> runCatching { summarize(readSample(f), f.name, t) }.getOrNull() }
    }

    /**
     * Rekomendasi renderer untuk satu versi: hanya membandingkan sesi pada versi yang sama
     * (supaya mod yang berbeda tidak mengacaukan perbandingan) dan butuh dua renderer atau lebih
     * dengan selisih yang jelas.
     */
    fun recommend(sessions: List<SessionSummary>, versionName: String?): RendererRecommendation? {
        if (versionName == null) return null
        val perRenderer = sessions
            .filter { it.versionName == versionName && it.windows >= MIN_WINDOWS_FOR_RECOMMENDATION }
            .groupBy { it.renderer }
            .mapValues { (_, list) -> median(list.map { it.low1Fps }) }
            .entries.sortedByDescending { it.value }
        if (perRenderer.size < 2) return null
        val best = perRenderer[0]
        val second = perRenderer[1]
        if (best.value < second.value * MIN_RELATIVE_GAIN) return null
        return RendererRecommendation(best.key, best.value, second.key, second.value)
    }
}
