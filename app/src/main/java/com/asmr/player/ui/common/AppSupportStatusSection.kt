package com.asmr.player.ui.common

import com.asmr.player.i18n.appString
import com.asmr.player.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmr.player.data.settings.AppProxyMode
import com.asmr.player.data.settings.NetworkRouteSettings
import com.asmr.player.ui.drawer.DrawerStatusViewModel
import com.asmr.player.data.remote.crawler.AlbumResourceSource
import com.asmr.player.ui.settings.SettingsDropdownSelector
import com.asmr.player.ui.theme.AsmrTheme

@Composable
fun AppSupportStatusSection(
    networkSettings: NetworkRouteSettings,
    onUseSystemProxy: () -> Unit,
    onAdvancedProxyApplied: (AppProxyMode, String, Int, Boolean, String, String) -> Unit,
    onUseSystemDns: () -> Unit,
    onCustomDnsServerApplied: (String) -> Unit,
    modifier: Modifier = Modifier,
    drawerStatusViewModel: DrawerStatusViewModel = hiltViewModel()
) {
    // 完整收听数据集中在「ASMR 看板」页面，这里负责服务测试与网络路由配置。
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SiteStatusSection(viewModel = drawerStatusViewModel)
        NetworkRouteSettingsSection(
            settings = networkSettings,
            onUseSystemProxy = onUseSystemProxy,
            onAdvancedProxyApplied = onAdvancedProxyApplied,
            onUseSystemDns = onUseSystemDns,
            onCustomDnsServerApplied = onCustomDnsServerApplied
        )
    }
}

@Composable
fun SiteStatusSection(
    viewModel: DrawerStatusViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val dlsite by viewModel.dlsite.collectAsStateWithLifecycle()
    val asmr by viewModel.asmr.collectAsStateWithLifecycle()
    val site by viewModel.asmrOneSite.collectAsStateWithLifecycle()
    val japaneseAsmr by viewModel.japaneseAsmr.collectAsStateWithLifecycle()
    val preferredSource by viewModel.preferredResourceSource.collectAsStateWithLifecycle()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = appString(R.string.ui_site_status_test),
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textSecondary
        )
        SiteStatusTestRow(
            name = "dlsite.com",
            status = dlsite,
            onTest = viewModel::testDlsite
        )
        SiteStatusTestRow(
            status = asmr,
            onTest = viewModel::testAsmrOne,
            nameContent = {
                AsmrOneSiteSelector(
                    selectedSite = site,
                    onSiteSelected = viewModel::setAsmrOneSite,
                    lightweight = true
                )
            }
        )
        SiteStatusTestRow(
            name = "Japanese ASMR",
            status = japaneseAsmr,
            onTest = viewModel::testJapaneseAsmr
        )
        SettingsDropdownSelector(
            label = appString(R.string.ui_preferred_source),
            value = preferredSource.name,
            options = AlbumResourceSource.entries.associate { it.name to appString(R.string.ui_prefer, it.label) },
            onSelect = { viewModel.setPreferredResourceSource(AlbumResourceSource.valueOf(it)) },
            tagPrefix = "resource_source_priority",
            flatTrigger = true
        )
    }
}
