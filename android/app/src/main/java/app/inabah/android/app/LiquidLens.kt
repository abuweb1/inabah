package app.inabah.android.app

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb

// Капля «жидкого стекла» iOS 26 для панели вкладок (записи пользователя IMG_9750, IMG_9752, IMG_9754).
// В iOS это системный эффект TabView (своего кода в проекте нет), здесь — шейдер AGSL (с Android 13):
// - под каплей содержимое чуть мельче, к ободку — сжимается сильнее и «перегибается»;
// - на ободке — радужная кайма: каналы цвета преломляются чуть по-разному;
// - значки и подписи под каплей окрашиваются в цвет выбранного раздела;
// - у края — лёгкая дымка.
// На Android 12 панель обходится без преломления.

private const val LENS_SHADER = """
uniform shader content;
uniform float2 center;
uniform float2 halfSize;
uniform float progress;
uniform float zoom;
uniform float rimSqueeze;
uniform float edgeWidth;
uniform float dispersion;
uniform float haze;
uniform float tintAmount;
layout(color) uniform half4 tint;

float roundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 coord) {
    float2 p = coord - center;
    float d = roundedBox(p, halfSize, min(halfSize.x, halfSize.y));
    if (d > 0.0 || progress <= 0.0) {
        return content.eval(coord);
    }
    // 0 — на ободке, 1 — в глубине капли.
    float t = smoothstep(0.0, 1.0, clamp(-d / edgeWidth, 0.0, 1.0));
    // Центр — лёгкое увеличение, ободок — выборка дальше от центра (содержимое сжимается).
    float k = mix(mix(1.0, rimSqueeze, progress), 1.0 / mix(1.0, zoom, progress), t);
    float spread = (1.0 - t) * dispersion * progress;
    half4 r = content.eval(center + p * (k * (1.0 + spread)));
    half4 g = content.eval(center + p * k);
    half4 b = content.eval(center + p * (k * (1.0 - spread)));
    half4 c = half4(r.r, g.g, b.b, max(max(r.a, g.a), b.a));
    // Содержимое под каплей — в цвет раздела (значки белые, сохраняем их яркость и прозрачность).
    half3 tinted = tint.rgb * c.a;
    c.rgb = mix(c.rgb, tinted, tintAmount * progress * t);
    float glow = (1.0 - t) * (1.0 - t) * haze * progress;
    return c + half4(glow, glow, glow, glow);
}
"""

/**
 * Масштаб в центре (< 1 — содержимое под каплей мельче, как в iOS: IMG_9754), сжатие на ободке
 * (выборка дальше от центра), ширина ободка (доля высоты).
 */
private const val LENS_ZOOM = 0.9f
private const val LENS_RIM_SQUEEZE = 1.4f
private const val LENS_EDGE_FRACTION = 0.3f

/** Радужная кайма, дымка у края, сила окраски содержимого в цвет раздела. */
private const val LENS_DISPERSION = 0.06f
private const val LENS_HAZE = 0.12f
private const val LENS_TINT = 0.9f

/** Шейдер капли: один на панель, перед кадром — новые координаты и [effect]. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class LiquidLensShader {
    private val shader = RuntimeShader(LENS_SHADER)

    /** Капля с центром [centerX]/[centerY] и половинами размера (px), раскрытая на [progress], цвет раздела [tint]. */
    fun effect(centerX: Float, centerY: Float, halfWidth: Float, halfHeight: Float, progress: Float, tint: Color) =
        shader.run {
            setFloatUniform("center", centerX, centerY)
            setFloatUniform("halfSize", halfWidth, halfHeight)
            setFloatUniform("progress", progress)
            setFloatUniform("zoom", LENS_ZOOM)
            setFloatUniform("rimSqueeze", LENS_RIM_SQUEEZE)
            setFloatUniform("edgeWidth", halfHeight * 2 * LENS_EDGE_FRACTION)
            setFloatUniform("dispersion", LENS_DISPERSION)
            setFloatUniform("haze", LENS_HAZE)
            setFloatUniform("tintAmount", LENS_TINT)
            setColorUniform("tint", tint.toArgb())
            RenderEffect.createRuntimeShaderEffect(this, "content").asComposeRenderEffect()
        }
}
