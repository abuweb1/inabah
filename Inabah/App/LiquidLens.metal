// Капля «жидкого стекла» iOS 26 для своей панели вкладок — перенос шейдера Android `LiquidLens.kt` (AGSL)
// один в один (решение пользователя 2026-10-25: панель «как на Android»):
// - под каплей содержимое чуть мельче, к ободку — сжимается сильнее и «перегибается»;
// - на ободке — радужная кайма: каналы цвета преломляются чуть по-разному;
// - значки и подписи под каплей окрашиваются в цвет выбранного раздела;
// - у края — лёгкая дымка.

#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

static float roundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

[[ stitchable ]] half4 liquidLens(
    float2 position,
    SwiftUI::Layer layer,
    float2 center,
    float2 halfSize,
    float progress,
    float zoom,
    float rimSqueeze,
    float edgeWidth,
    float dispersion,
    float haze,
    float tintAmount,
    half4 tint
) {
    float2 p = position - center;
    float d = roundedBox(p, halfSize, min(halfSize.x, halfSize.y));
    if (d > 0.0 || progress <= 0.0) {
        return layer.sample(position);
    }
    // 0 — на ободке, 1 — в глубине капли.
    float t = smoothstep(0.0, 1.0, clamp(-d / edgeWidth, 0.0, 1.0));
    // Центр — лёгкое уменьшение, ободок — выборка дальше от центра (содержимое сжимается).
    float k = mix(mix(1.0, rimSqueeze, progress), 1.0 / mix(1.0, zoom, progress), t);
    float spread = (1.0 - t) * dispersion * progress;
    half4 r = layer.sample(center + p * (k * (1.0 + spread)));
    half4 g = layer.sample(center + p * k);
    half4 b = layer.sample(center + p * (k * (1.0 - spread)));
    half4 c = half4(r.r, g.g, b.b, max(max(r.a, g.a), b.a));
    // Содержимое под каплей — в цвет раздела (значки белые, сохраняем их яркость и прозрачность).
    half3 tinted = tint.rgb * c.a;
    c.rgb = mix(c.rgb, tinted, half(tintAmount * progress * t));
    half glow = half((1.0 - t) * (1.0 - t) * haze * progress);
    return c + half4(glow, glow, glow, glow);
}

// Зерно стекла панели (Android Haze `noiseFactor`): без него размытие выглядит «пластиком». Накладывается
// на заливку формы (капсулы): каждая точка внутри — чуть светлее или темнее (белый или чёрный с малой прозрачностью).
[[ stitchable ]] half4 glassGrain(float2 position, half4 color, float amount) {
    float n = fract(sin(dot(floor(position), float2(12.9898, 78.233))) * 43758.5453) - 0.5;
    half alpha = half(abs(n) * 2.0 * amount) * color.a;
    half white = n > 0.0 ? alpha : 0.0h;
    return half4(white, white, white, alpha);
}
