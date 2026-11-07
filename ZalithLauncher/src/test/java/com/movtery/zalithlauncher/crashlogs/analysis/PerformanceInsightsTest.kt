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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceInsightsTest {
    private fun line(avg: Float, low1: Float, max: Float = 40f, stutters: Int = 3) =
        "[NusaFrameStats] frames=300 avg=${avg}fps low1pct=${low1}fps median=5.0ms max=${max}ms jitter=2.0ms stutters=$stutters"

    private fun log(version: String, renderer: String, vararg lines: String) =
        "==== NusaLauncher Session ====\nMinecraft version: $version\nRenderer: $renderer\n====\n" + lines.joinToString("\n")

    private fun session(version: String, renderer: String, low1: Float, windows: Int = 8): SessionSummary {
        val ls = Array(windows) { line(200f, low1) }
        return PerformanceInsights.summarize(log(version, renderer, *ls), "NusaLogs-2026-11-07-07.29.$renderer.log", 1L)!!
    }

    @Test
    fun loadingFreezeWindowsAreExcluded() {
        val text = log("1.21.1", "GL4ES",
            line(20f, 2f, max = 3500f), line(60f, 5f, max = 400f), line(200f, 30f), line(220f, 40f), line(210f, 36f))
        val s = PerformanceInsights.summarize(text, "NusaLogs-2026-11-07-07.29.GL4ES.log", 1L)!!
        // only windows up to the last freeze (> 1000 ms) are dropped; the 400 ms window still counts
        assertEquals(4, s.windows)
        assertEquals(205f, s.avgFps, 0.01f)   // median of 60, 200, 220, 210
        assertEquals(33f, s.low1Fps, 0.01f)   // median of 5, 30, 40, 36
        assertEquals("GL4ES", s.renderer)
        assertEquals("1.21.1", s.versionName)
    }

    @Test
    fun tooFewWindowsGivesNoSummary() {
        assertNull(PerformanceInsights.summarize(log("1.21.1", "GL4ES", line(200f, 30f)), "a.log", 1L))
        assertNull(PerformanceInsights.summarize("no stats here", "a.log", 1L))
    }

    @Test
    fun rendererFallsBackToFileName() {
        val text = "Minecraft version: 1.21.1\n" + line(200f, 30f) + "\n" + line(200f, 30f)
        val s = PerformanceInsights.summarize(text, "NusaLogs-2026-11-07-07.29.KopperZink.log", 1L)!!
        assertEquals("KopperZink", s.renderer)
    }

    @Test
    fun recommendsMoreStableRendererOnSameVersionOnly() {
        val sessions = listOf(
            session("Vanilla", "GL4ES", 36f), session("Vanilla", "MobileGlues", 28f), session("Vanilla", "KopperZink", 10f),
            session("Modpack", "KopperZink", 90f) // different version must not influence
        )
        val r = PerformanceInsights.recommend(sessions, "Vanilla")!!
        assertEquals("GL4ES", r.renderer)
        assertEquals("MobileGlues", r.otherRenderer)
        assertNull(PerformanceInsights.recommend(sessions, "Modpack")) // only one renderer there
        assertNull(PerformanceInsights.recommend(sessions, null))
    }

    @Test
    fun noRecommendationWhenDifferenceIsTooSmallOrDataTooShort() {
        val close = listOf(session("V", "GL4ES", 30f), session("V", "MobileGlues", 29f))
        assertNull(PerformanceInsights.recommend(close, "V"))
        val short = listOf(session("V", "GL4ES", 40f, windows = 3), session("V", "MobileGlues", 10f, windows = 3))
        assertNull(PerformanceInsights.recommend(short, "V"))
    }

    @Test
    fun graphUsesWorstFrameTime() {
        val s = session("V", "GL4ES", 40f)
        assertTrue(s.worstFrameMs.isNotEmpty())
        assertEquals(25f, s.worstFrameMs[0], 0.01f)
        assertNotNull(s)
    }
}
