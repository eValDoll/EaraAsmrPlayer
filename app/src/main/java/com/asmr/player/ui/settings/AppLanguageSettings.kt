package com.asmr.player.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.asmr.player.R
import com.asmr.player.i18n.AppLanguage

@Composable
internal fun AppLanguageSettings() {
    SettingsDropdownSelector(
        label = stringResource(R.string.app_language),
        value = AppLanguage.selected(),
        options = linkedMapOf("" to stringResource(R.string.follow_system), "zh-CN" to "简体中文", "en" to "English"),
        onSelect = AppLanguage::select,
        tagPrefix = "app_language"
    )
}
