# AICam —— Doka 风格开源相机

> 简约纯粹的 AI 相机：实时胶片/CCD 滤镜 · AI 构图引导 · AI 滤镜推荐 · 人像美颜
>
> Android 原生（Kotlin + CameraX + OpenGL ES 3.0 + Jetpack Compose），完全离线运行。

## 功能总览

### 拍摄核心
| 功能 | 说明 |
|---|---|
| 多镜头焦段 | 0.5x/1x/2x/3x/5x 自动探测（超广角/主摄/长焦） |
| 变焦 | 焦段条点按切换 |
| 点按对焦 | 取景器任意位置点按 |
| 闪光灯 | 关/自动/开/常亮 四态循环 |
| 定时拍摄 | 3s / 10s 倒计时 |
| 网格 | 三分法 / 黄金分割 / 对角线 / 中心 / 方形 |
| 画幅 | 4:3 / 16:9 / 1:1 / 3:4 / 9:16 |
| 前后摄切换 | 前置自动镜像 |

### 滤镜系统（所见即所得）
- **26 个预设**，五大分组：胶片 / CCD / 黑白 / 创意 / 原片
- 单一 Uber-Shader 驱动：曝光、对比、饱和、色温、三级调色(Lift/Gain)、分离色调、
  颗粒、暗角、色散、光晕、卤化红晕、柔焦、锐化、色阶量化(CCD)、扫描线(VHS)
- **强度滑杆**：所有滤镜 0-100% 可调（参数线性插值）
- 预览与拍照共用同一管线 —— 取景即成片

### AI 功能（全端侧、离线）
| 功能 | 实现 |
|---|---|
| AI 构图引导 | MediaPipe 人脸+物体检测 → 三分法/主体占比/视线空间评分 → 主体框 + 方向建议 |
| AI 滤镜推荐 | 场景分类 + 帧亮度/对比/色温/饱和统计 → Top-3 推荐条 |
| 人像美颜 | GPU 肤色掩码磨皮（保边防塑料脸）+ 肤色提亮，可与任意滤镜叠加 |

### CCD / 复古特色
- **CCD 低清模式**：降采样再放大 + 色阶量化 + 颗粒增强，还原千禧年数码相机质感
- **复古日期水印**：`'98 7 14` 橙色等宽字体日期戳（可自选格式）
- **机型水印**：角落写入设备型号

### 相册与设置
- 相册只展示本 App 拍摄的照片（隐私边界清晰），支持大图查看/分享/删除
- 完整设置：保存原图、JPEG 画质(60-100)、HEIF、快门音效、触屏拍摄等

## 架构

```
┌────────────────────────── UI (Jetpack Compose) ──────────────────────────┐
│  CameraScreen      GalleryScreen      SettingsScreen                     │
│  （取景器/网格/AI引导/推荐条/焦段条/快门/滤镜抽屉）                          │
└──────────────┬───────────────────────────────────────────────────────────┘
               │ StateFlow（单向数据流）
┌──────────────▼───────────────────────────────────────────────────────────┐
│  CameraViewModel                                                          │
│   ├─ CameraController (CameraX: Preview→GL / ImageCapture / ImageAnalysis)│
│   ├─ CaptureProcessor  (离屏EGL → 滤镜 → 水印 → JPEG)                     │
│   ├─ CompositionAnalyzer (MediaPipe 构图评分)                             │
│   └─ FilterRecommender (场景+光线 → 滤镜推荐)                              │
└──────────────┬───────────────────────────────────────────────────────────┘
               │
┌──────────────▼───────────────────────────────────────────────────────────┐
│  GL 渲染层 (OpenGL ES 3.0)                                                │
│   CameraGlView (OES纹理实时渲染)  ⇄  OffscreenFilterSession (全分辨率拍照)  │
│   FilterProgram / FilterShaders（单一 Uber-Shader，参数化滤镜）             │
└──────────────────────────────────────────────────────────────────────────┘
```

## 构建

要求：Android Studio（AGP 8.13+）、JDK 17、Android SDK 36。

```bash
# 直接用 Gradle Wrapper
./gradlew :app:assembleDebug
```

首次构建会从 Google Maven 拉取依赖；国内网络可在 `settings.gradle.kts`
取消阿里云镜像注释。

## 开源致谢

本项目参考/借鉴了以下开源项目（详见 `docs/01-GitHub开源调研报告.md`）：

| 项目 | 许可证 | 借鉴点 |
|---|---|---|
| [dazz-retro-camera](https://github.com/ganjmeng/dazz-retro-camera) | MIT | 复古相机产品架构、GPU 参数化渲染思路 |
| [android-gpuimage-plus](https://github.com/wysaid/android-gpuimage-plus) | MIT | GL 滤镜管线组织方式 |
| [CameraFilter](https://github.com/nekocode/CameraFilter) | Apache-2.0 | Camera2+GL 实时滤镜参考 |
| [camera-samples](https://github.com/android/camera-samples) | Apache-2.0 | CameraX 最佳实践 |
| [FadCam](https://github.com/anonfaded/FadCam) | GPL-3.0 | 工程组织参考 |

本项目自身以 **MIT** 协议开源（不含上述依赖的各自协议要求）。
