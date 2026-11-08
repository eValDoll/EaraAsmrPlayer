package com.asmr.player.ui.common

import com.asmr.player.i18n.appString
import com.asmr.player.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme

@Composable
internal fun CollectionPickerContent(
    windowSizeClass: WindowSizeClass,
    title: String,
    createPlaceholder: String,
    createName: String,
    onCreateNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    embeddedInDialog: Boolean,
    isAdding: Boolean,
    isEmpty: Boolean,
    emptyText: String,
    selectionSummary: String? = null,
    content: LazyListScope.() -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val listState = rememberLazyListState()
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = if (isCompact) Modifier.fillMaxSize() else Modifier
                .fillMaxHeight()
                .widthIn(max = 720.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (embeddedInDialog) {
                        IconButton(
                            onClick = onBack,
                            enabled = !isAdding,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = colorScheme.textPrimary,
                                disabledContentColor = colorScheme.textTertiary
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = appString(R.string.ui_back))
                        }
                    }
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (selectionSummary != null) {
                    Text(
                        text = selectionSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.textSecondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = createName,
                        onValueChange = onCreateNameChange,
                        modifier = Modifier.weight(1f),
                        enabled = !isAdding,
                        singleLine = true,
                        placeholder = { Text(createPlaceholder) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colorScheme.textPrimary,
                            unfocusedTextColor = colorScheme.textPrimary,
                            disabledTextColor = colorScheme.textTertiary,
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.textTertiary.copy(alpha = 0.5f),
                            disabledBorderColor = colorScheme.textTertiary.copy(alpha = 0.25f),
                            focusedPlaceholderColor = colorScheme.textSecondary,
                            unfocusedPlaceholderColor = colorScheme.textSecondary,
                            disabledPlaceholderColor = colorScheme.textTertiary,
                            cursorColor = colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    TextButton(
                        onClick = onCreate,
                        enabled = createName.isNotBlank() && !isAdding,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colorScheme.primary,
                            disabledContentColor = colorScheme.textTertiary
                        )
                    ) {
                        Text(androidx.compose.ui.res.stringResource(R.string.ui_create))
                    }
                }
            }
            if (isEmpty) {
                Text(
                    text = emptyText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    color = colorScheme.textSecondary
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    flingBehavior = rememberCalmScrollableFlingBehavior(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
internal fun CollectionPickerRow(
    name: String,
    artworkUri: String?,
    summary: String,
    enabled: Boolean,
    isAdding: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val cover = remember(artworkUri) { artworkUri?.trim().orEmpty() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surface.copy(alpha = 0.5f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsmrAsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholderCornerRadius = 12,
            peekAnySizeForInitial = true,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isAdding) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colorScheme.primary,
                trackColor = colorScheme.primarySoft,
                strokeWidth = 2.dp
            )
        }
    }
}
