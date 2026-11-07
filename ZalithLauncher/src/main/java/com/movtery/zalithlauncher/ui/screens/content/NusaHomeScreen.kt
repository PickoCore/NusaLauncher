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

package com.movtery.zalithlauncher.ui.screens.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.BuildConfig
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.crashlogs.analysis.PerformanceInsights
import com.movtery.zalithlauncher.crashlogs.analysis.SessionSummary
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.getAccountTypeName
import com.movtery.zalithlauncher.game.renderer.Renderers
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.nusa.NusaButton
import com.movtery.zalithlauncher.ui.nusa.NusaButtonStyle
import com.movtery.zalithlauncher.ui.nusa.NusaCard
import com.movtery.zalithlauncher.ui.nusa.NusaColors
import com.movtery.zalithlauncher.ui.nusa.NusaShapes
import com.movtery.zalithlauncher.ui.nusa.NusaSkeleton
import com.movtery.zalithlauncher.ui.nusa.NusaType
import com.movtery.zalithlauncher.ui.screens.NestedNavKey
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.content.elements.PlayerFace
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * NusaLauncher: Beranda baru ("Nusa Dark"). Menggantikan LauncherScreen dari Zalith.
 * Spesifikasi: docs/design/DESIGN.md
 */
@Composable
fun NusaHomeScreen(
    backStackViewModel: ScreenBackStackViewModel,
    navigateToVersions: (Version) -> Unit,
    onLaunchGame: (Version?) -> Unit,
) {
    BaseScreen(
        screenKey = NormalNavKey.LauncherMain,
        currentKey = backStackViewModel.mainScreen.currentKey
    ) { isVisible ->
        val version by VersionsManager.currentVersion.collectAsStateWithLifecycle()
        val isRefreshing by VersionsManager.isRefreshing.collectAsStateWithLifecycle()
        val account by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()

        //Ringkasan sesi dibaca dari log arsip; dimuat ulang tiap Beranda terlihat lagi
        val sessions by produceState<List<SessionSummary>?>(initialValue = null, isVisible) {
            value = withContext(Dispatchers.IO) {
                runCatching { PerformanceInsights.load(PathManager.DIR_GAME_LOGS) }.getOrDefault(emptyList())
            }
        }

        val toVersionManage: () -> Unit = {
            backStackViewModel.mainScreen.removeAndNavigateTo(
                remove = NestedNavKey.VersionSettings::class,
                screenKey = NormalNavKey.VersionsManager
            )
        }
        val toAccountManage: () -> Unit = {
            backStackViewModel.mainScreen.navigateTo(
                screenKey = NormalNavKey.AccountManager(FirstLoginMenu.NONE)
            )
        }
        val toLogHistory: () -> Unit = {
            backStackViewModel.mainScreen.navigateTo(NormalNavKey.LogHistory)
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                //Kolom kiri: versi dan tombol Main, lalu sesi terakhir
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HeroCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        version = version,
                        isRefreshing = isRefreshing,
                        onPlay = { onLaunchGame(null) },
                        onChangeVersion = toVersionManage,
                        onOpenSettings = { version?.let(navigateToVersions) }
                    )
                    SessionCard(
                        modifier = Modifier.fillMaxWidth(),
                        sessions = sessions,
                        onClick = toLogHistory
                    )
                }

                //Kolom kanan: akun, renderer, dan keterangan lisensi
                Column(
                    modifier = Modifier
                        .width(236.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AccountCard(
                            account = account,
                            onChangeAccount = toAccountManage
                        )
                        RendererCard(
                            version = version,
                            sessions = sessions,
                            onOpenSettings = {
                                val v = version
                                if (v != null) navigateToVersions(v) else toVersionManage()
                            }
                        )
                    }
                    //Syarat lisensi: penanda versi modifikasi tidak resmi harus tampil di antarmuka utama
                    Text(
                        text = stringResource(R.string.nusa_home_unofficial, BuildConfig.VERSION_NAME),
                        style = NusaType.Caption
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    modifier: Modifier,
    version: Version?,
    isRefreshing: Boolean,
    onPlay: () -> Unit,
    onChangeVersion: () -> Unit,
    onOpenSettings: () -> Unit
) {
    NusaCard(
        modifier = modifier,
        shape = NusaShapes.Large,
        contentPadding = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                isRefreshing -> {
                    NusaSkeleton(Modifier.width(240.dp).height(30.dp))
                    NusaSkeleton(Modifier.width(160.dp).height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NusaSkeleton(Modifier.width(140.dp).height(48.dp))
                        NusaSkeleton(Modifier.width(110.dp).height(48.dp))
                    }
                    NusaSkeleton(Modifier.width(220.dp).height(56.dp), shape = NusaShapes.Medium)
                }

                version == null -> {
                    Text(
                        text = stringResource(R.string.nusa_home_no_version),
                        style = NusaType.DisplayLarge.copy(fontSize = 28.sp, lineHeight = 32.sp)
                    )
                    Text(
                        text = stringResource(R.string.nusa_home_no_version_hint),
                        style = NusaType.Caption
                    )
                    NusaButton(
                        text = stringResource(R.string.nusa_home_choose_version),
                        onClick = onChangeVersion
                    )
                }

                else -> {
                    val name = remember(version) { version.getVersionName() }
                    val info = remember(version) { version.getVersionInfo()?.getInfoString() }
                    val rendererName = remember(version) { rendererDisplayName(version) }
                    val ramGb = remember(version) {
                        String.format(Locale.US, "%.1f", version.getRamAllocation() / 1024f)
                    }

                    Text(
                        text = name,
                        style = NusaType.DisplayLarge.copy(fontSize = 28.sp, lineHeight = 32.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!info.isNullOrBlank()) {
                        Text(
                            text = info,
                            style = NusaType.Caption.copy(fontSize = 14.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NusaButton(
                            text = stringResource(R.string.nusa_home_renderer_chip, rendererName),
                            onClick = onOpenSettings,
                            style = NusaButtonStyle.Secondary
                        )
                        NusaButton(
                            text = stringResource(R.string.nusa_home_ram_chip, ramGb),
                            onClick = onOpenSettings,
                            style = NusaButtonStyle.Secondary
                        )
                        NusaButton(
                            text = stringResource(R.string.nusa_home_change_version),
                            onClick = onChangeVersion,
                            style = NusaButtonStyle.Ghost
                        )
                    }
                    NusaButton(
                        modifier = Modifier.width(220.dp),
                        text = stringResource(R.string.nusa_home_play),
                        onClick = onPlay,
                        enabled = version.isValid()
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    modifier: Modifier,
    sessions: List<SessionSummary>?,
    onClick: () -> Unit
) {
    NusaCard(
        modifier = modifier,
        onClick = onClick,
        shape = NusaShapes.Small,
        contentPadding = 14.dp
    ) {
        val last = sessions?.firstOrNull()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.nusa_home_session_title), style = NusaType.Title.copy(fontSize = 16.sp, lineHeight = 20.sp))
                when {
                    sessions == null -> {
                        Spacer(Modifier.height(8.dp))
                        NusaSkeleton(Modifier.fillMaxWidth().height(14.dp))
                        Spacer(Modifier.height(6.dp))
                        NusaSkeleton(Modifier.width(180.dp).height(12.dp))
                    }

                    last == null -> {
                        Text(
                            text = stringResource(R.string.nusa_home_session_empty),
                            style = NusaType.Caption,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    else -> {
                        FrameGraph(
                            values = last.worstFrameMs,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            text = stringResource(
                                R.string.nusa_home_session_summary,
                                last.avgFps.roundToInt(),
                                last.low1Fps.roundToInt(),
                                last.stutters.roundToInt()
                            ),
                            style = NusaType.Caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.nusa_home_log_history),
                style = NusaType.Button.copy(fontSize = 14.sp),
                color = NusaColors.AccentText
            )
        }
    }
}

/** Grafik batang: waktu frame terburuk tiap jendela 5 detik. Batang tinggi berarti ada sentakan. */
@Composable
private fun FrameGraph(
    values: List<Float>,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    val median = remember(values) { values.sorted()[values.size / 2].coerceAtLeast(1f) }
    val description = stringResource(R.string.nusa_home_session_graph_desc)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEach { v ->
            val ratio = (v / median).coerceIn(1f, 3f)
            val barHeight = 8.dp + 22.dp * ((ratio - 1f) / 2f)
            Spacer(
                modifier = Modifier
                    .width(6.dp)
                    .height(barHeight)
                    .background(if (v > median * 2f) NusaColors.AccentStart else Color(0xFF6B6D73))
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: com.movtery.zalithlauncher.game.account.Account?,
    onChangeAccount: () -> Unit
) {
    NusaCard(
        modifier = Modifier.fillMaxWidth(),
        shape = NusaShapes.Medium
    ) {
        val acc = account
        if (acc != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlayerFace(account = acc, avatarSize = 52.dp)
                Column {
                    Text(
                        text = acc.username,
                        style = NusaType.Title.copy(fontSize = 16.sp, lineHeight = 20.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = getAccountTypeName(acc), style = NusaType.Caption)
                }
            }
            Spacer(Modifier.height(12.dp))
            NusaButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.nusa_home_change_account),
                onClick = onChangeAccount,
                style = NusaButtonStyle.Secondary
            )
        } else {
            Text(text = stringResource(R.string.account_add_new_account), style = NusaType.Title.copy(fontSize = 16.sp, lineHeight = 20.sp))
            Spacer(Modifier.height(12.dp))
            NusaButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.account_add_new_account),
                onClick = onChangeAccount
            )
        }
    }
}

@Composable
private fun RendererCard(
    version: Version?,
    sessions: List<SessionSummary>?,
    onOpenSettings: () -> Unit
) {
    val recommendation = remember(sessions, version) {
        sessions?.let { PerformanceInsights.recommend(it, version?.getVersionName()) }
    }
    NusaCard(
        modifier = Modifier.fillMaxWidth(),
        shape = NusaShapes.Medium
    ) {
        Text(text = stringResource(R.string.nusa_home_renderer_title), style = NusaType.Caption.copy(fontSize = 13.sp))
        if (recommendation != null) {
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = recommendation.renderer,
                style = NusaType.Title
            )
            Text(
                modifier = Modifier.padding(top = 2.dp),
                text = stringResource(
                    R.string.nusa_home_reco_text,
                    recommendation.low1Fps.roundToInt(),
                    recommendation.otherLow1Fps.roundToInt(),
                    recommendation.otherRenderer
                ),
                style = NusaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp)
            )
        } else {
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = stringResource(R.string.nusa_home_reco_empty),
                style = NusaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp)
            )
        }
        Spacer(Modifier.height(10.dp))
        NusaButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.nusa_home_renderer_settings),
            onClick = onOpenSettings,
            style = NusaButtonStyle.Ghost
        )
    }
}

/** Nama renderer yang mudah dibaca untuk versi ini; jatuh ke id jika daftar renderer belum siap. */
private fun rendererDisplayName(version: Version): String {
    val id = version.getRenderer()
    return runCatching {
        Renderers.getRenderers().firstOrNull { it.getUniqueIdentifier() == id }?.getRendererName()
    }.getOrNull() ?: id
}
