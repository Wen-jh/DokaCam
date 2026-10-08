package com.dokacam.camera.gl

/**
 * 滤镜渲染着色器。
 *
 * 设计要点：
 * 1. **单一 Uber-Shader**：全部滤镜效果（约 20 个环节）在同一个片元着色器内
 *    通过 uniform 开关，避免为每个滤镜编译独立 program（编译慢、切换贵）。
 * 2. **双采样器变体**：相机预览用 samplerExternalOES，离线处理用 sampler2D，
 *    主体逻辑 100% 共享，保证「预览即成片」。
 * 3. **uQuality 开关**：预览时可关闭色散/光晕/锐化等多次采样环节保帧率，
 *    拍照时全开保画质。
 */
object FilterShaders {

    // ---------------- 顶点着色器（共用） ----------------
    const val VERTEX_SHADER = """
#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec2 aTexCoord;
uniform mat4 uTexMatrix;
out vec2 vTexCoord;
void main() {
    vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
    gl_Position = aPosition;
}
"""

    // ---------------- 片元着色器主体 ----------------
    // %SAMPLER% 会被替换为 sampler2D 或 samplerExternalOES
    private const val FRAGMENT_BODY = """
#version 300 es
%SAMPLER_EXTENSION%
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

%SAMPLER_TYPE% uTexture;

uniform vec2  uTexelSize;      // 1/width, 1/height
uniform float uTime;           // 秒，用于动态颗粒
uniform float uMirror;         // 前置摄像头镜像
uniform float uAspect;         // 宽/高
uniform float uQuality;        // 0 = 精简（预览），1 = 全效（拍照）
uniform float uUseMatrix;
uniform mat3  uColorMatrix;

// ---- 基础 ----
uniform float uExposure;
uniform float uContrast;
uniform float uSaturation;
uniform float uVibrance;
uniform float uTemperature;
uniform float uTint;

// ---- 影调 ----
uniform float uHighlights;
uniform float uShadows;
uniform float uWhites;
uniform float uBlacks;
uniform float uFade;
uniform float uGamma;

// ---- 三级调色 ----
uniform vec3  uLift;
uniform vec3  uGain;
uniform vec3  uShadowTint;
uniform vec3  uHighlightTint;
uniform float uSplitBalance;

// ---- 质感 ----
uniform float uGrain;
uniform float uGrainSize;
uniform float uVignette;
uniform float uChroma;
uniform float uHalation;
uniform float uBloom;
uniform float uSoften;
uniform float uSharpen;
uniform float uPosterize;
uniform float uScanline;

// ---- 人像 ----
uniform float uSkinSoften;
uniform float uSkinBrighten;

const vec3 LUMA_W = vec3(0.2126, 0.7152, 0.0722);

float luma(vec3 c) { return dot(c, LUMA_W); }

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// 肤色掩码：只对「人皮肤颜色」生效，避免磨到背景
float skinMask(vec3 c) {
    vec3 hsv = rgb2hsv(clamp(c, 0.0, 1.0));
    float hMask = smoothstep(0.0, 0.035, hsv.x) * (1.0 - smoothstep(0.10, 0.17, hsv.x));
    float sMask = smoothstep(0.10, 0.24, hsv.y) * (1.0 - smoothstep(0.50, 0.72, hsv.y));
    float vMask = smoothstep(0.18, 0.38, hsv.z);
    return hMask * sMask * vMask;
}

vec2 mapUV(vec2 uv) {
    if (uMirror > 0.5) uv.x = 1.0 - uv.x;
    return uv;
}

vec3 sampleScene(vec2 uv) {
    return texture(uTexture, mapUV(uv)).rgb;
}

// 9-tap 高斯（用于柔焦 / 磨皮 / 光晕）
vec3 blur9(vec2 uv, float radius) {
    vec2 r = uTexelSize * radius;
    vec3 acc = sampleScene(uv) * 4.0;
    acc += sampleScene(uv + vec2(-r.x, -r.y)) + sampleScene(uv + vec2(r.x, -r.y));
    acc += sampleScene(uv + vec2(-r.x,  r.y)) + sampleScene(uv + vec2(r.x,  r.y));
    acc += sampleScene(uv + vec2(-r.x * 2.0, 0.0)) * 2.0 + sampleScene(uv + vec2(r.x * 2.0, 0.0)) * 2.0;
    acc += sampleScene(uv + vec2(0.0, -r.y * 2.0)) * 2.0 + sampleScene(uv + vec2(0.0, r.y * 2.0)) * 2.0;
    return acc / 16.0;
}

// 亮部提取（光晕/红晕的原料）
vec3 brightPass(vec2 uv, float radius, float threshold) {
    vec3 b = blur9(uv, radius);
    float l = luma(b);
    return b * smoothstep(threshold, threshold + 0.25, l);
}

void main() {
    vec2 uv = vTexCoord;

    // ============ 0. 采样（含镜头轴向色散） ============
    float ca = uChroma * uQuality;
    vec3 col;
    if (ca > 0.002) {
        vec2 dir = (uv - 0.5) * 0.014 * ca;
        col.r = texture(uTexture, mapUV(uv + dir)).r;
        col.g = sampleScene(uv).g;
        col.b = texture(uTexture, mapUV(uv - dir)).b;
    } else {
        col = sampleScene(uv);
    }

    // ============ 1. 曝光 & 白平衡 ============
    col *= exp2(uExposure);
    col *= vec3(1.0 + uTemperature * 0.18, 1.0 + uTint * 0.05 - abs(uTemperature) * 0.02, 1.0 - uTemperature * 0.18);
    col = clamp(col, 0.0, 4.0);

    // ============ 2. Lift / Gain（ASC CDL） ============
    col = col * uGain + uLift;

    // ============ 3. 通道混合矩阵 ============
    if (uUseMatrix > 0.5) col = clamp(uColorMatrix * col, 0.0, 4.0);

    // ============ 4. 对比度（绕胶片支点，保护高光） ============
    const float pivot = 0.435;
    col = (col - pivot) * uContrast + pivot;

    // ============ 5. 高光 / 阴影 / 白场 / 黑场 ============
    float l = luma(col);
    float hMask = smoothstep(0.55, 1.0, l);
    float sMask = 1.0 - smoothstep(0.0, 0.45, l);
    col += uHighlights * hMask * 0.35 * (1.0 - hMask * 0.5);
    col += uShadows * sMask * 0.35 * (1.0 - sMask * 0.3);
    col += uWhites * 0.25 * hMask * hMask;
    col += uBlacks * 0.25 * sMask * sMask;

    // ============ 6. Gamma ============
    if (abs(uGamma - 1.0) > 0.001) col = pow(max(col, 0.0), vec3(1.0 / uGamma));

    // ============ 7. 饱和度 & 自然饱和度 ============
    float gLuma = luma(col);
    col = mix(vec3(gLuma), col, uSaturation);
    if (abs(uVibrance) > 0.001) {
        float sat = rgb2hsv(clamp(col, 0.0, 1.0)).y;
        float vibAmt = uVibrance * (1.0 - sat);
        col = mix(vec3(luma(col)), col, 1.0 + vibAmt);
    }

    // ============ 8. 分离色调 ============
    float lum2 = luma(col);
    float sW = 1.0 - smoothstep(0.0, uSplitBalance + 0.001, lum2);
    float hW = smoothstep(uSplitBalance, 1.0, lum2);
    col += uShadowTint * sW + uHighlightTint * hW;

    // ============ 9. 褪色（提黑） ============
    if (uFade > 0.001) {
        col = col * (1.0 - 0.16 * uFade) + vec3(0.105, 0.098, 0.092) * uFade;
    }

    // ============ 10. 柔焦 ============
    if (uSoften > 0.005) {
        col = mix(col, blur9(uv, 3.0 + uSoften * 5.0), uSoften * 0.8);
    }

    // ============ 11. 光晕 & 卤化红晕 ============
    if (uQuality > 0.5) {
        if (uBloom > 0.005) {
            col += brightPass(uv, 4.0 + uBloom * 6.0, 0.72) * uBloom * 0.9;
        }
        if (uHalation > 0.005) {
            vec3 hp = brightPass(uv, 5.0 + uHalation * 8.0, 0.65);
            col += hp * vec3(1.15, 0.35, 0.20) * uHalation * 0.9;
        }
        if (uSharpen > 0.005) {
            vec3 blur = blur9(uv, 1.4);
            col += (col - blur) * uSharpen * 1.6;
        }
    }

    // ============ 12. 人像：磨皮 + 提亮肤色 ============
    if (uSkinSoften > 0.005 || uSkinBrighten > 0.005) {
        float mask = skinMask(col);
        if (uSkinSoften > 0.005) {
            vec3 soft = blur9(uv, 2.5 + uSkinSoften * 3.5);
            // 保边：细节（毛孔/轮廓）回混，避免「塑料脸」
            float detail = clamp(1.0 - abs(luma(col) - luma(soft)) * 4.0, 0.0, 1.0);
            vec3 blended = mix(soft, col, detail * 0.55);
            col = mix(col, blended, mask * uSkinSoften);
        }
        if (uSkinBrighten > 0.005) {
            col *= 1.0 + uSkinBrighten * 0.22 * mask;
            col += vec3(0.012, 0.006, -0.004) * uSkinBrighten * mask;
        }
    }

    // ============ 13. 胶片颗粒 ============
    if (uGrain > 0.005) {
        vec2 gp = floor(gl_FragCoord.xy / max(uGrainSize, 0.5));
        float n = hash12(gp + floor(uTime * 12.0) * 7.31) - 0.5;
        float grainLuma = 1.0 - abs(luma(col) - 0.5) * 1.2;  // 中调颗粒最重
        col += n * uGrain * 0.16 * clamp(grainLuma, 0.15, 1.0);
    }

    // ============ 14. 暗角 ============
    if (abs(uVignette) > 0.005) {
        float d = distance(uv, vec2(0.5));
        float v = smoothstep(0.28, 0.80, d);
        col *= 1.0 - uVignette * v;
    }

    // ============ 15. CCD 色阶量化 ============
    if (uPosterize > 1.0) {
        float levels = max(uPosterize, 2.0);
        col = floor(clamp(col, 0.0, 1.0) * levels + 0.5) / levels;
    }

    // ============ 16. VHS 扫描线 ============
    if (uScanline > 0.005) {
        float sl = 0.5 + 0.5 * sin(gl_FragCoord.y * 2.1);
        col *= 1.0 - uScanline * 0.22 * sl;
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""

    /** 相机预览版：外部纹理（OES） */
    fun fragmentForOes(): String = FRAGMENT_BODY
        .replace("%SAMPLER_EXTENSION%", "#extension GL_OES_EGL_image_external_essl3 : require")
        .replace("%SAMPLER_TYPE%", "uniform samplerExternalOES")

    /** 离线处理版：普通 2D 纹理 */
    fun fragmentFor2D(): String = FRAGMENT_BODY
        .replace("%SAMPLER_EXTENSION%", "")
        .replace("%SAMPLER_TYPE%", "uniform sampler2D")
}
