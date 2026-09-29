// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0
// Adapted from Haze's ProgressiveBlurRenderEffect.kt: inline the vertical EaseInOut mask,
// use continuous kernel coverage, pass direction as a uniform, and constrain samples
// to the captured pixel centers.
// https://github.com/chrisbanes/haze/blob/9e4cee7c42a4ed9b3e9994256ae96da107dd6cd0/haze-utils/src/commonMain/kotlin/dev/chrisbanes/haze/ProgressiveBlurRenderEffect.kt

package com.asmr.player.ui.common

internal const val ProgressiveHeaderBlurShader = """
    uniform shader content;
    uniform float2 bounds;
    uniform float2 direction;
    uniform float radius;
    uniform float blurStart;
    uniform float blurEnd;

    const float maxRadius = 150.0;

    float gaussian(float x, float sigma) {
        return exp(-(x * x) / (2.0 * sigma * sigma));
    }

    float4 blur(float2 coord, float blurRadius) {
        float r = min(blurRadius, maxRadius - 1.0);
        float sigma = max(blurRadius / 2.0, 1.0);
        float weightSum = 1.0;
        // Full precision prevents accumulation bands, including on bright backgrounds.
        float4 result = content.eval(coord);

        for (float i = 1.0; i < maxRadius; i += 2.0) {
            if (i >= r + 1.0) { break; }
            // Fade the outer pixel into the kernel instead of rounding the radius.
            // This also approaches the original pixel continuously as the radius reaches zero.
            float weightL = gaussian(i, sigma) * clamp(r - i + 1.0, 0.0, 1.0);
            float weightH = gaussian(i + 1.0, sigma) * clamp(r - i, 0.0, 1.0);
            float weight = weightL + weightH;
            // Pair adjacent pixels through bilinear sampling, keeping the pixel grid fixed.
            float2 offset = direction * (i + weightH / weight);

            float2 sampleCoord = coord - offset;
            if (sampleCoord.x >= 0.5 && sampleCoord.y >= 0.5) {
                result += weight * content.eval(sampleCoord);
                weightSum += weight;
            }
            sampleCoord = coord + offset;
            if (sampleCoord.x <= bounds.x - 0.5 && sampleCoord.y <= bounds.y - 0.5) {
                result += weight * content.eval(sampleCoord);
                weightSum += weight;
            }
        }

        return result / weightSum;
    }

    float4 main(float2 coord) {
        float progress = clamp((coord.y - blurStart) / max(blurEnd - blurStart, 1.0), 0.0, 1.0);
        float t = progress;
        // Invert the x component of EaseInOut (0.42, 0, 0.58, 1).
        for (int i = 0; i < 4; i++) {
            float x = t * (1.26 + t * (-0.78 + 0.52 * t));
            float slope = 1.26 + t * (-1.56 + 1.56 * t);
            t = clamp(t - (x - progress) / slope, 0.0, 1.0);
        }
        float intensity = 1.0 - t * t * (3.0 - 2.0 * t);
        return blur(coord, radius * intensity);
    }
"""
