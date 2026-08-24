package com.asmr.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.utilities.DislikeAnalyzer
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeTonalSpot

private const val MONOCHROME_CHROMA_THRESHOLD = 5.0
private const val MONET_PRIMARY_STRONG_CHROMA_SCALE = 0.90
private const val MONET_PRIMARY_SOFT_CHROMA_SCALE = 0.80
private const val MONET_SECONDARY_CHROMA_SCALE = 0.85

internal fun deriveHuePalette(
    primary: Color,
    mode: ThemeMode,
    neutral: NeutralPalette,
    fallbackOnPrimary: Color,
    forceMonochrome: Boolean = false
): HuePalette {
    if (forceMonochrome) {
        return deriveMonochromeHuePalette(mode, neutral, fallbackOnPrimary)
    }

    val sourceHct = sanitizedSourceHct(primary, forceMonochrome)
    val scheme = dynamicSchemeFor(sourceHct, mode, forceMonochrome)
    val dynamicColors = MaterialDynamicColors()

    val primarySoft = softenDynamicAccentColor(
        Color(dynamicColors.primaryContainer().getArgb(scheme)),
        chromaScale = MONET_PRIMARY_SOFT_CHROMA_SCALE
    )
    val primaryStrong = softenDynamicAccentColor(
        Color(dynamicColors.primary().getArgb(scheme)),
        chromaScale = MONET_PRIMARY_STRONG_CHROMA_SCALE
    )
    val secondary = softenDynamicAccentColor(
        Color(dynamicColors.secondary().getArgb(scheme)),
        chromaScale = MONET_SECONDARY_CHROMA_SCALE
    )
    val onPrimary = Color(dynamicColors.onPrimary().getArgb(scheme))
    val onPrimaryContainer = Color(dynamicColors.onPrimaryContainer().getArgb(scheme))
    val onSecondary = Color(dynamicColors.onSecondary().getArgb(scheme))
    val background = Color(dynamicColors.background().getArgb(scheme))
    val onBackground = Color(dynamicColors.onBackground().getArgb(scheme))
    val surface = Color(dynamicColors.surfaceContainer().getArgb(scheme))
    val onSurface = Color(dynamicColors.onSurface().getArgb(scheme))
    val surfaceVariant = Color(dynamicColors.surfaceVariant().getArgb(scheme))
    val onSurfaceVariant = Color(dynamicColors.onSurfaceVariant().getArgb(scheme))
    val blendedBackground = blendOpaqueWithFallback(
        dynamic = background,
        fallback = neutral.background,
        amount = neutralBlendAmount(mode)
    )
    val blendedSurface = blendOpaqueWithFallback(
        dynamic = surface,
        fallback = neutral.surface,
        amount = surfaceBlendAmount(mode)
    )
    val blendedSurfaceVariant = blendOpaqueWithFallback(
        dynamic = surfaceVariant,
        fallback = neutral.surfaceVariant,
        amount = surfaceVariantBlendAmount(mode)
    )

    return HuePalette(
        primary = primary,
        primarySoft = primarySoft,
        primaryStrong = primaryStrong,
        onPrimary = bestForeground(
            background = primaryStrong,
            candidate = onPrimary,
            fallback = fallbackOnPrimary
        ),
        secondary = secondary,
        onPrimaryContainer = onPrimaryContainerFor(
            primaryContainer = primarySoft,
            candidate = onPrimaryContainer,
            fallback = neutral.onSurface
        ),
        onSecondary = bestForeground(
            background = secondary,
            candidate = onSecondary,
            fallback = onPrimary
        ),
        background = blendedBackground,
        onBackground = bestForeground(
            background = blendedBackground,
            candidate = blendWithFallback(
                dynamic = onBackground,
                fallback = neutral.onBackground,
                amount = textBlendAmount(mode)
            ),
            fallback = neutral.onBackground
        ),
        surface = blendedSurface,
        onSurface = bestForeground(
            background = blendedSurface,
            candidate = blendWithFallback(
                dynamic = onSurface,
                fallback = neutral.onSurface,
                amount = textBlendAmount(mode)
            ),
            fallback = neutral.onSurface
        ),
        surfaceVariant = blendedSurfaceVariant,
        onSurfaceVariant = bestForeground(
            background = blendedSurfaceVariant,
            candidate = blendWithFallback(
                dynamic = onSurfaceVariant,
                fallback = neutral.onSurfaceVariant,
                amount = textSecondaryBlendAmount(mode)
            ),
            fallback = neutral.onSurfaceVariant
        ),
        textPrimary = bestForeground(
            background = blendedSurface,
            candidate = blendWithFallback(
                dynamic = onSurface,
                fallback = neutral.textPrimary,
                amount = textBlendAmount(mode)
            ),
            fallback = neutral.textPrimary
        ),
        textSecondary = blendWithFallback(
            dynamic = onSurfaceVariant,
            fallback = neutral.textSecondary,
            amount = textSecondaryBlendAmount(mode)
        ),
        textTertiary = blendWithFallback(
            dynamic = onSurfaceVariant,
            fallback = neutral.textTertiary,
            amount = textTertiaryBlendAmount(mode)
        )
    )
}

private fun deriveMonochromeHuePalette(
    mode: ThemeMode,
    neutral: NeutralPalette,
    fallbackOnPrimary: Color
): HuePalette {
    val primary = when (mode) {
        ThemeMode.Light -> Color(0xFF5D6670)
        ThemeMode.Dark -> Color(0xFFBBC4CE)
        ThemeMode.SoftDark -> Color(0xFFAFB8C2)
    }
    val primarySoft = when (mode) {
        ThemeMode.Light -> Color(0xFFDCE2E8)
        ThemeMode.Dark -> Color(0xFF59616A)
        ThemeMode.SoftDark -> Color(0xFF525A64)
    }
    val primaryStrong = when (mode) {
        ThemeMode.Light -> Color(0xFF4E5660)
        ThemeMode.Dark -> Color(0xFFCDD5DE)
        ThemeMode.SoftDark -> Color(0xFFC1CAD4)
    }
    val secondary = when (mode) {
        ThemeMode.Light -> Color(0xFF737C86)
        ThemeMode.Dark -> Color(0xFFAAB3BD)
        ThemeMode.SoftDark -> Color(0xFF9EA8B3)
    }
    val background = when (mode) {
        ThemeMode.Light -> Color(0xFFF4F6F8)
        ThemeMode.Dark -> Color(0xFF16181C)
        ThemeMode.SoftDark -> Color(0xFF1A1D22)
    }
    val surface = when (mode) {
        ThemeMode.Light -> Color(0xFFFFFFFF)
        ThemeMode.Dark -> Color(0xFF20242A)
        ThemeMode.SoftDark -> Color(0xFF232830)
    }
    val surfaceVariant = when (mode) {
        ThemeMode.Light -> Color(0xFFE6EBF0)
        ThemeMode.Dark -> Color(0xFF343A43)
        ThemeMode.SoftDark -> Color(0xFF373E48)
    }

    val onPrimary = bestForeground(
        background = primaryStrong,
        candidate = when (mode) {
            ThemeMode.Light -> Color.White
            ThemeMode.Dark -> Color(0xFF1F2328)
            ThemeMode.SoftDark -> Color(0xFF1F2328)
        },
        fallback = fallbackOnPrimary
    )
    val onPrimaryContainer = bestForeground(
        background = primarySoft,
        candidate = when (mode) {
            ThemeMode.Light -> Color(0xFF2F353C)
            ThemeMode.Dark -> Color(0xFFF3F6F9)
            ThemeMode.SoftDark -> Color(0xFFEFF3F7)
        },
        fallback = neutral.onSurface
    )
    val onSecondary = bestForeground(
        background = secondary,
        candidate = when (mode) {
            ThemeMode.Light -> Color.White
            ThemeMode.Dark -> Color(0xFF20242A)
            ThemeMode.SoftDark -> Color(0xFF20242A)
        },
        fallback = onPrimary
    )
    val onBackground = bestForeground(
        background = background,
        candidate = when (mode) {
            ThemeMode.Light -> Color(0xFF252A31)
            ThemeMode.Dark -> Color(0xFFE7ECF2)
            ThemeMode.SoftDark -> Color(0xFFE4EAF0)
        },
        fallback = neutral.onBackground
    )
    val onSurface = bestForeground(
        background = surface,
        candidate = when (mode) {
            ThemeMode.Light -> Color(0xFF252A31)
            ThemeMode.Dark -> Color(0xFFE7ECF2)
            ThemeMode.SoftDark -> Color(0xFFE4EAF0)
        },
        fallback = neutral.onSurface
    )
    val onSurfaceVariant = bestForeground(
        background = surfaceVariant,
        candidate = when (mode) {
            ThemeMode.Light -> Color(0xFF5B6470)
            ThemeMode.Dark -> Color(0xFFC3CBD4)
            ThemeMode.SoftDark -> Color(0xFFBAC3CD)
        },
        fallback = neutral.onSurfaceVariant
    )

    return HuePalette(
        primary = primary,
        primarySoft = primarySoft,
        primaryStrong = primaryStrong,
        onPrimary = onPrimary,
        secondary = secondary,
        onPrimaryContainer = onPrimaryContainer,
        onSecondary = onSecondary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        textPrimary = onSurface,
        textSecondary = when (mode) {
            ThemeMode.Light -> Color(0xFF68717C)
            ThemeMode.Dark -> Color(0xFFB3BCC6)
            ThemeMode.SoftDark -> Color(0xFFAAB4BE)
        },
        textTertiary = when (mode) {
            ThemeMode.Light -> Color(0xFF88919B)
            ThemeMode.Dark -> Color(0xFF8D97A2)
            ThemeMode.SoftDark -> Color(0xFF87919C)
        }
    )
}

private fun sanitizedSourceHct(primary: Color, forceMonochrome: Boolean): Hct {
    val source = Hct.fromInt(primary.toArgb())
    val fixed = DislikeAnalyzer.fixIfDisliked(source)
    return if (forceMonochrome || fixed.chroma < MONOCHROME_CHROMA_THRESHOLD) {
        Hct.from(fixed.hue, 0.0, fixed.tone)
    } else {
        fixed
    }
}

private fun softenDynamicAccentColor(color: Color, chromaScale: Double): Color {
    if (chromaScale >= 0.999) return color
    val hct = Hct.fromInt(color.toArgb())
    val softened = Hct.from(
        hct.hue,
        (hct.chroma * chromaScale).coerceAtLeast(0.0),
        hct.tone
    )
    return Color(softened.toInt())
}

private fun dynamicSchemeFor(source: Hct, mode: ThemeMode, forceMonochrome: Boolean): DynamicScheme {
    return if (forceMonochrome || source.chroma < MONOCHROME_CHROMA_THRESHOLD) {
        SchemeMonochrome(source, mode.isDark, 0.0)
    } else {
        SchemeTonalSpot(source, mode.isDark, 0.0)
    }
}

internal fun bestOnPrimary(primary: Color, fallback: Color): Color {
    val bg = asOpaqueArgb(primary)
    val white = Color.White.toArgb()
    val black = Color.Black.toArgb()
    val contrastWhite = ColorUtils.calculateContrast(white, bg)
    val contrastBlack = ColorUtils.calculateContrast(black, bg)
    val pick = if (contrastWhite >= contrastBlack) Color.White else Color.Black
    val contrastPick = if (pick == Color.White) contrastWhite else contrastBlack
    if (contrastPick >= 4.5) return pick
    return fallback
}

private fun bestForeground(
    background: Color,
    candidate: Color,
    fallback: Color
): Color {
    val opaqueBackground = asOpaqueColor(background)
    val opaqueBackgroundArgb = opaqueBackground.toArgb()

    val candidateContrast = ColorUtils.calculateContrast(candidate.toArgb(), opaqueBackgroundArgb)
    if (candidateContrast >= 4.5) return candidate

    val fallbackContrast = ColorUtils.calculateContrast(fallback.toArgb(), opaqueBackgroundArgb)
    if (fallbackContrast >= 4.5) return fallback

    return bestOnPrimary(opaqueBackground, fallback)
}

private fun onPrimaryContainerFor(
    primaryContainer: Color,
    candidate: Color,
    fallback: Color
): Color {
    val opaquePrimaryContainer = asOpaqueColor(primaryContainer)
    val contrast = ColorUtils.calculateContrast(
        candidate.toArgb(),
        opaquePrimaryContainer.toArgb()
    )
    if (contrast >= 4.5) return candidate

    val fallbackContrast = ColorUtils.calculateContrast(
        fallback.toArgb(),
        opaquePrimaryContainer.toArgb()
    )
    if (fallbackContrast >= 4.5) return fallback

    return bestOnPrimary(opaquePrimaryContainer, candidate)
}

private fun blendWithFallback(dynamic: Color, fallback: Color, amount: Float): Color {
    if (amount <= 0f) return fallback
    if (amount >= 1f) return dynamic
    return Color(ColorUtils.blendARGB(fallback.toArgb(), dynamic.toArgb(), amount))
}

private fun blendOpaqueWithFallback(dynamic: Color, fallback: Color, amount: Float): Color {
    return asOpaqueColor(blendWithFallback(dynamic = dynamic, fallback = fallback, amount = amount))
}

private fun asOpaqueColor(color: Color): Color {
    return Color(asOpaqueArgb(color))
}

private fun asOpaqueArgb(color: Color): Int {
    return ColorUtils.setAlphaComponent(color.toArgb(), 0xFF)
}

private fun neutralBlendAmount(mode: ThemeMode): Float {
    return when (mode) {
        ThemeMode.Light -> 0.38f
        ThemeMode.Dark -> 0.46f
        ThemeMode.SoftDark -> 0.42f
    }
}

private fun surfaceBlendAmount(mode: ThemeMode): Float {
    return when (mode) {
        ThemeMode.Light -> 0.44f
        ThemeMode.Dark -> 0.52f
        ThemeMode.SoftDark -> 0.48f
    }
}

private fun surfaceVariantBlendAmount(mode: ThemeMode): Float {
    return when (mode) {
        ThemeMode.Light -> 0.48f
        ThemeMode.Dark -> 0.56f
        ThemeMode.SoftDark -> 0.52f
    }
}

private fun textBlendAmount(mode: ThemeMode): Float {
    return when (mode) {
        ThemeMode.Light -> 0.26f
        ThemeMode.Dark -> 0.30f
        ThemeMode.SoftDark -> 0.28f
    }
}

private fun textSecondaryBlendAmount(mode: ThemeMode): Float {
    return when (mode) {
        ThemeMode.Light -> 0.22f
        ThemeMode.Dark -> 0.26f
        ThemeMode.SoftDark -> 0.24f
    }
}

private fun textTertiaryBlendAmount(mode: ThemeMode): Float {
    return when (mode) {
        ThemeMode.Light -> 0.16f
        ThemeMode.Dark -> 0.20f
        ThemeMode.SoftDark -> 0.18f
    }
}

internal fun clampPrimaryHslForMode(hsl: FloatArray, mode: ThemeMode) {
    val hue = ((hsl[0] % 360f) + 360f) % 360f
    val saturation = hsl[1].coerceIn(0f, 1f)
    val lightnessMin = when (mode) {
        ThemeMode.Light -> 0.18f
        ThemeMode.Dark -> when {
            hue in 38f..85f -> 0.58f
            hue in 85f..170f -> 0.54f
            hue in 170f..260f -> 0.48f
            else -> 0.52f
        }
        ThemeMode.SoftDark -> when {
            hue in 38f..85f -> 0.54f
            hue in 85f..170f -> 0.50f
            hue in 170f..260f -> 0.45f
            else -> 0.48f
        }
    }
    val lightnessMax = when (mode) {
        ThemeMode.Light -> when {
            hue in 38f..85f -> if (saturation >= 0.45f) 0.38f else 0.42f
            hue in 85f..170f -> if (saturation >= 0.45f) 0.40f else 0.44f
            hue in 170f..260f -> if (saturation >= 0.45f) 0.46f else 0.50f
            else -> if (saturation >= 0.45f) 0.42f else 0.46f
        }
        ThemeMode.Dark -> when {
            hue in 38f..85f -> 0.74f
            hue in 85f..170f -> 0.70f
            hue in 170f..260f -> 0.66f
            else -> 0.68f
        }
        ThemeMode.SoftDark -> when {
            hue in 38f..85f -> 0.70f
            hue in 85f..170f -> 0.66f
            hue in 170f..260f -> 0.62f
            else -> 0.64f
        }
    }
    val saturationMax = when (mode) {
        ThemeMode.Light -> 0.76f
        ThemeMode.Dark -> 0.78f
        ThemeMode.SoftDark -> 0.76f
    }
    hsl[1] = hsl[1].coerceIn(0f, saturationMax)
    hsl[2] = hsl[2].coerceIn(lightnessMin, lightnessMax)
}
