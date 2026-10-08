package com.dokacam.camera.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.dokacam.camera.ai.FilterRecommender
import com.dokacam.camera.data.model.GridType
import com.dokacam.camera.filter.FilterPresets
import com.dokacam.camera.gl.CameraGlView
import com.dokacam.camera.ui.common.DokaIcons

/**
 * 拍摄主界面。
 * 布局：取景器（网格/水平仪/AI 引导）→ AI 推荐条 → 焦段条 → 快门排 → 滤镜抽屉。
 * 交互原则：一切可点元素 ≤ 2 击可达；取景画面是唯一主角。
 */
@Composable
fun CameraScreen(
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vm: CameraViewModel = viewModel()

    val settings by vm.settings.collectAsState()
    val composition by vm.composition.collectAsState()
    val recommendations by vm.recommendations.collectAsState()
    val currentPreset by vm.currentPreset.collectAsState()
    val captureState by vm.captureState.collectAsState()
    val lastPhoto by vm.lastPhoto.collectAsState()
    val countdown by vm.timerCountdown.collectAsState()

    // ---------------- 权限 ----------------
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { hasCameraPermission = it }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    // 焦段状态（绑定后由控制器刷新，UI 驱动重组）
    var lensOptions by remember {
        mutableStateOf(listOf(com.dokacam.camera.camera.LensOption(1f, 0, "1x")))
    }
    var activeZoom by remember { mutableStateOf(1f) }

    // ---------------- GL 预览视图 ----------------
    val glView = remember {
        CameraGlView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    // 滤镜/设置变化 → 下发渲染配置
    LaunchedEffect(settings, currentPreset) {
        glView.setRenderConfig(vm.buildRenderConfig())
    }

    // 相机绑定（含 AI 分析用例）
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            runCatching {
                vm.cameraController.bind(
                    lifecycleOwner = lifecycleOwner,
                    glView = glView,
                    executor = ContextCompat.getMainExecutor(context),
                    onAnalysisFrame = { bmp -> vm.feedAnalysisFrame(bmp) },
                )
                lensOptions = vm.cameraController.lensOptions
                activeZoom = vm.cameraController.activeZoom
            }
        }
    }

    if (!hasCameraPermission) {
        PermissionRationale { permLauncher.launch(Manifest.permission.CAMERA) }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // ============ 取景器 ============
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(settings.aspectRatio.ratio)
                .align(Alignment.Center)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        vm.cameraController.focusAt(
                            glView,
                            offset.x / size.width,
                            offset.y / size.height,
                        )
                    }
                },
        ) {
            AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
            GridOverlay(settings.gridType, Modifier.fillMaxSize())
            if (settings.aiCompositionEnabled) {
                AiGuideOverlay(composition, Modifier.fillMaxSize())
            }
        }

        // ============ 顶部栏 ============
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlChip(DokaIcons.Flash, settings.flashMode.label) { vm.toggleFlash() }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ControlChip(
                    DokaIcons.Sparkle, "AI",
                    tint = if (settings.aiCompositionEnabled) Color(0xFFF2C14E) else Color.White,
                ) { vm.toggleAi() }
                ControlChip(DokaIcons.Grid, settings.gridType.label) { vm.cycleGrid() }
                ControlChip(DokaIcons.Timer, settings.timerDelay.label) { vm.cycleTimer() }
            }
            Text(
                "···",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // ============ 底部控制区 ============
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f)),
        ) {
            if (settings.aiFilterRecommendEnabled && recommendations.isNotEmpty()) {
                RecommendRow(recommendations, currentPreset.id) { vm.selectPreset(it) }
            }

            ZoomLensBar(
                options = lensOptions,
                active = activeZoom,
                onZoom = {
                    vm.cameraController.applyZoom(it)
                    activeZoom = it
                },
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(0.4f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenGallery),
                ) {
                    lastPhoto?.let {
                        AsyncImage(
                            model = it.uri, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                ShutterButton(
                    enabled = captureState !is CameraViewModel.CaptureState.Capturing &&
                        captureState !is CameraViewModel.CaptureState.Processing,
                    countdown = countdown,
                ) { vm.capture(lifecycleOwner) }

                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.12f))
                        .clickable {
                            vm.switchCamera(lifecycleOwner)
                            lensOptions = vm.cameraController.lensOptions
                            activeZoom = vm.cameraController.activeZoom
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Image(
                        imageVector = DokaIcons.FlipCamera,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            FilterStrip(
                presets = FilterPresets.ALL,
                activeId = currentPreset.id,
                intensity = settings.filterIntensity,
                onPick = { vm.selectPreset(it.id) },
                onIntensity = { vm.setFilterIntensity(it) },
                recommendedIds = recommendations.map { it.preset.id }.toSet(),
            )
        }

        // ============ 状态提示 ============
        CaptureStateBanner(captureState, Modifier.align(Alignment.Center))
    }
}

// ------------------------------------------------------------------

@Composable
private fun ControlChip(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        androidx.compose.foundation.Image(
            imageVector = icon, contentDescription = label,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GridOverlay(gridType: GridType, modifier: Modifier = Modifier) {
    if (gridType == GridType.OFF) return
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = Color.White.copy(alpha = 0.30f)
        val draw = { a: Offset, b: Offset -> drawLine(line, a, b, 1.2f, cap = StrokeCap.Round) }

        when (gridType) {
            GridType.THIRDS, GridType.SQUARE -> {
                for (i in 1..2) {
                    draw(Offset(w * i / 3f, 0f), Offset(w * i / 3f, h))
                    draw(Offset(0f, h * i / 3f), Offset(w, h * i / 3f))
                }
            }
            GridType.GOLDEN -> {
                val gx = w / 1.618f
                val gy = h / 1.618f
                draw(Offset(gx, 0f), Offset(gx, h))
                draw(Offset(w - gx, 0f), Offset(w - gx, h))
                draw(Offset(0f, gy), Offset(w, gy))
                draw(Offset(0f, h - gy), Offset(w, h - gy))
            }
            GridType.DIAGONAL -> {
                draw(Offset(0f, 0f), Offset(w, h))
                draw(Offset(w, 0f), Offset(0f, h))
            }
            GridType.CENTER -> {
                draw(Offset(w / 2f, h / 2f - 40f), Offset(w / 2f, h / 2f + 40f))
                draw(Offset(w / 2f - 40f, h / 2f), Offset(w / 2f + 40f, h / 2f))
            }
            GridType.OFF -> Unit
        }
        if (gridType == GridType.SQUARE) {
            for (i in 1..5) {
                draw(Offset(w * i / 6f, 0f), Offset(w * i / 6f, h))
                draw(Offset(0f, h * i / 6f), Offset(w, h * i / 6f))
            }
        }
    }
}

/** AI 构图引导层：主体框 + 分数 + 一句话建议 */
@Composable
private fun AiGuideOverlay(
    composition: com.dokacam.camera.ai.CompositionResult?,
    modifier: Modifier = Modifier,
) {
    val c = composition ?: return
    Canvas(modifier) {
        c.subjects.forEach { r ->
            drawRect(
                Color(0xFFF2C14E).copy(alpha = 0.85f),
                topLeft = Offset(r.left * size.width, r.top * size.height),
                size = androidx.compose.ui.geometry.Size(
                    r.width() * size.width, r.height() * size.height,
                ),
                style = Stroke(width = 2.4f),
            )
        }
    }
}

@Composable
private fun RecommendRow(
    recommendations: List<FilterRecommender.Recommendation>,
    activeId: String,
    onSelect: (String) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            recommendations.size,
            key = { recommendations[it].preset.id },
        ) { idx ->
            val r = recommendations[idx]
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (r.preset.id == activeId) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.10f),
                modifier = Modifier.clickable { onSelect(r.preset.id) },
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.Image(
                        imageVector = DokaIcons.Sparkle,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        r.preset.name,
                        fontSize = 12.sp,
                        color = if (r.preset.id == activeId) Color.Black else Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, countdown: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(0.4f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (countdown > 0) {
            Text("$countdown", color = Color.Black, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CaptureStateBanner(
    state: CameraViewModel.CaptureState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is CameraViewModel.CaptureState.Processing ->
            Surface(
                modifier = modifier,
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.7f),
            ) {
                Text(
                    "${state.stage}…",
                    color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        is CameraViewModel.CaptureState.Failed ->
            Surface(
                modifier = modifier,
                shape = RoundedCornerShape(20.dp),
                color = Color(0xCCE5484D),
            ) {
                Text(
                    state.reason,
                    color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        else -> Unit
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("需要相机权限", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "DokaCam 需要访问相机才能取景拍摄。\n我们不会上传你的任何照片。",
            color = Color(0xFFB8B8B8), fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onRequest() },
        ) {
            Text(
                "授予权限",
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            )
        }
    }
}
