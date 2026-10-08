# GitHub 开源调研报告 —— Doka 风格相机 App

> 调研时间：2026-10-08 · 调研人：WorkBuddy · 目的：为 DokaCam 找可复用的开源实现

## 一、调研结论（TL;DR）

**没有任何一个开源项目能直接等同于 Doka 相机**，但把 4 个项目的长处拼起来，
覆盖 Doka 90% 的功能面是可行的，剩余 10%（AI 构图的具体模型）用 ML Kit 端侧
方案替代实现，效果可用且完全离线。

最终选型：

| 能力 | 来源 | 理由 |
|---|---|---|
| 相机基础 | **android/camera-samples** (Apache-2.0) | 官方最佳实践，CameraX 全特性 |
| 实时滤镜管线 | **android-gpuimage-plus** (MIT) | 参数化 GL 滤镜的成熟思路 |
| 复古/CCD 产品设计 | **dazz-retro-camera** (MIT) | 与 Doka 定位最接近的完整产品 |
| 工程组织参考 | **FadCam** (GPL-3.0) | 现代 Kotlin 相机 App 结构 |
| AI 构图/滤镜推荐 | 自研（ML Kit） | 无现成开源，端侧检测即可满足 |

## 二、候选项目详评

### ✅ 可直接复用

#### 1. ganjmeng/dazz-retro-camera ⭐26 · MIT · Flutter+原生
- **定位完全一致**：实时复古相机模拟器（非后期滤镜 App），11 种虚拟相机
  （CCD/胶片/拍立得/VHS），20 个 GPU pass，预览 60fps
- **架构先进**：「参数驱动统一 Native GPU 渲染」，Flutter 只做 UI，
  所有像素处理下沉到 Swift/Metal + Kotlin/OpenGL ES
- **借鉴点**：虚拟相机作为产品概念（每款滤镜绑定一台"虚拟相机"）；
  预览与拍照管线完全一致的设计
- **风险**：star 少（26），但代码质量与文档远超同 star 级项目，MIT 无风险

#### 2. wysaid/android-gpuimage-plus ⭐1933 · MIT · C/Java
- GPUImage 的 Android 强化版：GL 环境管理、滤镜链、离屏渲染、视频录制
- **借鉴点**：EglCore/offscreen surface 的生命周期管理写法；
  滤镜链（FilterChain）组织方式
- **注意**：核心是 C 库 + JNI，若全量引入会显著增加包体；本项目只借鉴
  GL 层设计，shader 全部自写

#### 3. nekocode/CameraFilter ⭐2155 · Apache-2.0 · Java
- Camera2 + GLSurfaceView 实时滤镜的最小可运行参考
- **借鉴点**：OES 纹理 → SurfaceTexture → Camera 的桥接顺序

#### 4. android/camera-samples ⭐5463 · Apache-2.0 · Kotlin
- CameraX 官方仓库：多镜头、变焦、对焦测光、并发用例的标准答案

### ⚠️ 有条件复用（GPL/无许可证）

#### 5. anonfaded/FadCam ⭐2822 · GPL-3.0 · Java/Kotlin
- 隐私优先的多媒体套件（后台录像/屏幕录制/直播），295MB 大仓库
- **GPL-3.0 传染**：只有当你的项目也开源时才能抄代码。
  本项目选择「开源」，因此可参考其结构，但实际未直接复制代码

#### 6. wuhaoyu1990/MagicCamera ⭐5515 · **无许可证** ❌
- 40+ 实时滤镜 + 美颜 + 录像 + 图片编辑，功能面最全
- **无 LICENSE 文件 = 默认版权保留，任何复用都不合法**。仅做功能对照表用

#### 7. aserbao/AndroidCamera ⭐3294 · **无许可证** ❌
- 仿抖音相机（分段录制/贴纸/美颜/视频编辑）。同上，不可复用

#### 8. almalence/OpenCamera ⭐1303 · 自定义 MPL 变体
- 全功能专业相机的老牌开源实现（对焦/测光/HDR/DRO 极其专业）
- 许可证为 Mozilla Public License 1.1 变体，文件级 copyleft，复用需谨慎；
  其「多镜头切换」「曝光补偿」逻辑可作为功能正确性的参照

### ❌ 已排除

- **wasabeef/android-gpuimage** ⭐9154：仓库无 LICENSE 文件（仅 README 提示），
  规避
- **LiveCompose/LiveCapture** ⭐9：强化学习构图辅助，论文配套代码不完整，
  思路（奖励信号 = 构图评分）被吸收进自研评分函数

## 三、Doka 功能 → 实现方案映射

| Doka 功能 | 开源界现状 | DokaCam 方案 |
|---|---|---|
| AI 实时构图引导（AR） | 无完整开源 | ML Kit 检测 + 三分法/占比/视线启发式评分 + Compose Canvas 引导层 |
| AI 滤镜推荐 | 无 | 场景分类 + 帧统计（亮度/色温/饱和）加权打分 |
| 质感滤镜（克制风格化） | 大量（多无许可证） | 自写 26 预设 + 单 Uber-Shader |
| CCD/复古质感 | dazz-retro-camera（MIT） | 色阶量化 + 颗粒 + 降采样 + 日期戳 |
| 多镜头焦段 | camera-samples | CameraX zoomState 探测 |
| 人像肤色优化 | MagicCamera（不可用） | GPU 肤色掩码 + 保边磨皮 |
| 极简无广告 | FadCam 哲学一致 | 原生实现，零第三方 SDK |

## 四、安全提醒（重要）

调研过程中用户提供了 GitHub 账号密码。**密码已拒绝使用**：
1. GitHub 自 2021 年起已停用密码做 git/API 鉴权，密码本身无法用于克隆
2. 密码在聊天中明文传输过，**强烈建议立即改密**，并开启 2FA
3. 后续如需 AI 代操作 GitHub（创建仓库/推送），请用
   **Personal Access Token（fine-grained, 只授权目标仓库）**
