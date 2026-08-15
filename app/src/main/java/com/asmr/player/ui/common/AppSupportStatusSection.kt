package com.asmr.player.ui.common

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
import com.asmr.player.ui.drawer.DrawerStatusViewModel
import com.asmr.player.ui.theme.AsmrTheme

@Composable
fun AppSupportStatusSection(
    modifier: Modifier = Modifier,
    drawerStatusViewModel: DrawerStatusViewModel = hiltViewModel()
) {
    // 完整收听数据集中在「ASMR 看板」页面，这里仅保留站点状态测试。
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SiteStatusSection(viewModel = drawerStatusViewModel)
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "站点状态测试",
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
    }
}
