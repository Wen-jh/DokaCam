package com.dokacam.camera.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aicam.camera.BuildConfig
import com.dokacam.camera.ai.ShotAdvice
import com.dokacam.camera.ai.ShotCoach
import com.dokacam.camera.camera.CameraController
import com.dokacam.camera.data.media.MediaRepository
import com.dokacam.camera.data.model.CameraSettings
import com.dokacam.camera.data.model.FilterParams
import com.dokacam.camera.data.model.FilterPreset
import com.dokacam.camera.data.model.GuideDirection
import com.dokacam.camera.data.model.GridType
import com.dokacam.camera.data.settings.SettingsRepository
import com.dokacam.camera.filter.FilterPresets
import com.dokacam.camera.gl.CameraGlView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class FlashUi { OFF, AUTO, ON, TORCH }

private const val TAG_AI_FOCUS = "AiAutoFocus"
private const val TAG_FOCUS_TAP = "FocusTap"
private const val TAG_LEVELER = "Leveler"
private const val TAG_ZOOM_SUGGEST = "AiZoomSuggest"
private const val TAG_NIGHT_LIGHT = "NightLight"

// AI 联动对焦节流参数
private const val USER_FOCUS_HOLD_MS = 5000L   // 手动对焦后的保护窗口
private const val AI_FOCUS_REFIRE_MS = 2500L   // 超过该间隔强制重新跟焦
private const val AI_FOCUS_MIN_GAP_MS = 800L   // 两次自动对焦的最小间隔
private const val AI_FOCUS_MOVE_THRESHOLD = 0.05f // 主体归一化位移阈值

private fun fmt2(v: Float): String = "%.2f".format(v)

@Composable
fun CameraScreen(
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val controller = remember { CameraController(context) }
    val glView = remember {
        CameraGlView(context).also { glv ->
            glv.controller = controller // EGL 重建时回调 rebindForNewSurface
            controller.glView = glv
        }
    }

    // 设置快照：DataStore Flow → Compose State，取景器行为随设置实时变化
    val settingsRepo = remember { SettingsRepository(context.applicationContext) }
    val settings by settingsRepo.settings.collectAsState(initial = CameraSettings())
    // 缩略图数据源：系统相册（Pictures/AICam）
    val mediaRepo = remember { MediaRepository(context.applicationContext) }

    // ---- AI 拍摄教练：构图评分 + 焦距/曝光建议 ----
    val aiOn = remember { mutableStateOf(true) }
    val coach = remember { ShotCoach(context, scope) }
    DisposableEffect(controller) {
        controller.onAnalysisFrame = { bmp ->
            if (aiOn.value) coach.submit(bmp) else bmp.recycle()
        }
        onDispose {
            controller.onAnalysisFrame = null
            coach.close()
        }
    }

    // GLSurfaceView 生命周期契约：暂停/恢复随宿主生命周期
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> glView.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> glView.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.onPause()
        }
    }

    LaunchedEffect(Unit) {
        (context as? Activity)?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
    }

    // API 26-28 写系统相册（Pictures/AICam）需要传统存储权限；29+ 走分区存储免权限
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT <= 28) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasPermission = result[Manifest.permission.CAMERA] == true }

    DisposableEffect(hasPermission) {
        if (hasPermission) controller.start(lifecycleOwner)
        onDispose { controller.shutdown() }
    }

    if (!hasPermission) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("AICam 需要相机权限", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "AICam 需要访问相机才能取景与拍摄，请授予权限后继续。",
                color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                Text("授予权限")
            }
        }
        return
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }
    fun poke() { controlsVisible = true; interactionTick++ }
    LaunchedEffect(interactionTick) {
        if (interactionTick > 0) { delay(4500); controlsVisible = false }
    }
    LaunchedEffect(Unit) { poke() }

    var flashUi by remember { mutableStateOf(FlashUi.OFF) }
    var gridOn by remember { mutableStateOf(true) }
    var timerSec by remember { mutableStateOf(0) }
    var drawerOpen by remember { mutableStateOf(false) }
    var activeZoom by remember { mutableStateOf(1f) }

    // 变焦范围：相机异步绑定完成后才可读，轮询等待；切镜头后重读
    var zoomRange by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var bindTick by remember { mutableStateOf(0) }
    LaunchedEffect(hasPermission, bindTick) {
        repeat(40) {
            val r = controller.getZoomRange()
            if (r != null) {
                zoomRange = r
                activeZoom = activeZoom.coerceIn(r.first, r.second)
                return@LaunchedEffect
            }
            delay(150)
        }
    }

    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusTick by remember { mutableStateOf(0) }
    val ringAnim = remember { Animatable(0f) }
    LaunchedEffect(focusTick) {
        if (focusPoint != null) {
            ringAnim.snapTo(0f)
            ringAnim.animateTo(1f, tween(550))
            focusPoint = null
        }
    }

    var countdown by remember { mutableStateOf(0) }
    var flashTick by remember { mutableStateOf(0) }
    val flashOverlay = remember { Animatable(0f) }
    LaunchedEffect(flashTick) {
        if (flashTick > 0) {
            flashOverlay.snapTo(0.85f)
            flashOverlay.animateTo(0f, tween(240))
        }
    }

    // ---- 滤镜管线：预设 + 强度插值 + 美颜/CCD 叠加 → 预览 GL 与拍照烘焙共用 ----
    var filterPanelOpen by remember { mutableStateOf(false) }
    val preset = FilterPresets.get(settings.selectedPresetId)
    var intensity by remember { mutableStateOf(settings.filterIntensity) }
    // 设置页/重启等外部改动时同步本地滑杆；滑动期间以本地值为准（松手才持久化）
    LaunchedEffect(settings.filterIntensity) { intensity = settings.filterIntensity }

    val effectiveParams = remember(
        preset, intensity, settings.beautyEnabled, settings.beautyIntensity, settings.ccdModeEnabled,
    ) {
        FilterParams.lerpParams(
            FilterParams.NEUTRAL, preset.params, intensity.coerceIn(0f, 1f),
        ).let { p ->
            var r = p
            if (settings.beautyEnabled) {
                val s = settings.beautyIntensity.coerceIn(0f, 1f)
                r = r.copy(
                    skinSoften = (r.skinSoften + 0.60f * s).coerceAtMost(0.9f),
                    skinBrighten = (r.skinBrighten + 0.30f * s).coerceAtMost(0.8f),
                )
            }
            if (settings.ccdModeEnabled) {
                // CCD 低清模式：颗粒 + 色阶量化 + 重暗角的稳妥组合近似
                r = r.copy(
                    grain = maxOf(r.grain, 0.30f),
                    grainSize = maxOf(r.grainSize, 2.2f),
                    posterize = if (r.posterize > 1f) r.posterize else 40f,
                    vignette = maxOf(r.vignette, 0.15f),
                )
            }
            r
        }
    }
    LaunchedEffect(effectiveParams) { glView.setFilterParams(effectiveParams) }

    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        mediaRepo.queryLatest()?.let { thumb = decodeThumb(context, it.uri) }
    }

    fun doCapture() {
        flashTick++
        val s = settings
        controller.takePhoto(
            jpegQuality = s.jpegQuality,
            saveOriginal = s.saveOriginal,
            mirrorFront = s.mirrorFrontCamera,
            aspectRatio = s.aspectRatio,
            shutterSoundOn = s.shutterSound,
            // 中性参数（原片+无美颜+无 CCD）传 null，跳过烘焙直出
            filterParams = effectiveParams.takeUnless { it.isNeutral() },
            onSaved = { uri ->
                scope.launch { thumb = decodeThumb(context, uri) }
                Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
            },
            onError = { msg -> Toast.makeText(context, "拍摄失败: $msg", Toast.LENGTH_SHORT).show() }
        )
    }
    fun startCapture() {
        poke()
        if (timerSec <= 0) { doCapture(); return }
        scope.launch {
            for (i in timerSec downTo 1) { countdown = i; delay(1000) }
            countdown = 0
            doCapture()
        }
    }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val advice by coach.advice.collectAsState()
    val gridType = settings.gridType
    val tapToCapture = settings.tapToCapture

    // ---- AI 主体联动对焦测光 ----
    // 用户手动点按对焦后 5 秒内不抢焦（elapsedRealtime 时间戳）
    val lastUserFocusAt = remember { longArrayOf(0L) }
    LaunchedEffect(aiOn.value) {
        if (!aiOn.value) return@LaunchedEffect
        var lastTarget: Offset? = null
        var lastFireAt = 0L
        coach.advice.collect { adv ->
            val primary = adv.faces.maxByOrNull { it.width() * it.height() }
                ?: adv.subjects.maxByOrNull { it.width() * it.height() }
                ?: return@collect
            val now = SystemClock.elapsedRealtime()
            if (now - lastUserFocusAt[0] < USER_FOCUS_HOLD_MS) return@collect
            // 与 overlay 同源的坐标映射：分析帧归一化 → 视图归一化
            val (vx, vy) = glView.mapAnalysisPointToView(primary.centerX(), primary.centerY())
            val target = Offset(vx, vy)
            val moved = lastTarget?.let { (it - target).getDistance() } ?: Float.MAX_VALUE
            val elapsed = now - lastFireAt
            // 节流：主体明显移动（>0.05）或超过 2.5s 未跟焦才重新发起
            if (elapsed > AI_FOCUS_REFIRE_MS ||
                (elapsed > AI_FOCUS_MIN_GAP_MS && moved > AI_FOCUS_MOVE_THRESHOLD)
            ) {
                val vw = viewSize.width.toFloat()
                val vh = viewSize.height.toFloat()
                if (vw > 0f && vh > 0f) {
                    // 视图归一化 → 缓冲归一化，focusAt 以缓冲为 surface 坐标系
                    val (bx, by) = glView.mapViewPointToBuffer(vx, vy)
                    if (controller.focusAt(bx, by)) {
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG_AI_FOCUS,
                                "focus subject view=(${fmt2(vx)},${fmt2(vy)})" +
                                    " buf=(${fmt2(bx)},${fmt2(by)})" +
                                    " face=${adv.faces.isNotEmpty()} moved=${fmt2(moved)}"
                            )
                        }
                        lastTarget = target
                        lastFireAt = now
                    }
                }
            }
        }
    }

    // ---- 水平仪：加速度计低通滤波 → 设备横滚角 ----
    var rollDeg by remember { mutableStateOf(0f) }
    DisposableEffect(settings.levelEnabled) {
        if (!settings.levelEnabled) return@DisposableEffect onDispose {}
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || sensor == null) {
            Log.w(TAG_LEVELER, "accelerometer unavailable")
            return@DisposableEffect onDispose {}
        }
        var fx = 0f; var fy = 0f; var fz = 0f; var primed = false
        var lastLogged = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val a = 0.15f // 低通系数：越小越平滑
                if (!primed) {
                    fx = e.values[0]; fy = e.values[1]; fz = e.values[2]; primed = true
                } else {
                    fx += a * (e.values[0] - fx)
                    fy += a * (e.values[1] - fy)
                    fz += a * (e.values[2] - fz)
                }
                // 近平放（z 主导）时横滚角无意义且噪声大，跳过更新
                if (sqrt(fx * fx + fy * fy) < 4f) return
                // Activity 锁竖屏，以竖握为基准：roll = 0 表示设备水平
                val roll = Math.toDegrees(atan2(fx.toDouble(), fy.toDouble())).toFloat()
                if (abs(roll - rollDeg) > 0.04f) rollDeg = roll
                if (BuildConfig.DEBUG && abs(roll - lastLogged) > 5f) {
                    Log.d(TAG_LEVELER, "roll=${fmt2(roll)}°")
                    lastLogged = roll
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())

        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .pointerInput(tapToCapture) {
                    // 触屏拍摄开启时禁用双击切镜头（与单拍手势冲突，镜头切换走底部按钮）
                    val switchLens: ((Offset) -> Unit)? = if (tapToCapture) null else { _ ->
                        controller.switchCamera(); activeZoom = 1f; bindTick++; poke()
                    }
                    detectTapGestures(
                        onTap = { offset ->
                            poke()
                            val vw = viewSize.width.toFloat()
                            val vh = viewSize.height.toFloat()
                            if (vw > 0f && vh > 0f) {
                                // 视图 → 缓冲逆映射：ViewPort 移除后视图是 FILL_CENTER 裁剪显示，
                                // 视图像素不能直接当对焦 surface 坐标
                                val (bx, by) = glView.mapViewPointToBuffer(offset.x / vw, offset.y / vh)
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        TAG_FOCUS_TAP,
                                        "tap view=(${fmt2(offset.x / vw)},${fmt2(offset.y / vh)})" +
                                            " buf=(${fmt2(bx)},${fmt2(by)}) ${glView.bufferWidth}x${glView.bufferHeight}"
                                    )
                                }
                                if (controller.focusAt(bx, by)) {
                                    lastUserFocusAt[0] = SystemClock.elapsedRealtime()
                                    focusPoint = offset
                                    focusTick++
                                }
                            }
                            if (tapToCapture) {
                                // 先对焦，稍等对焦环收敛后再按既有快门流程拍
                                scope.launch { delay(400); startCapture() }
                            }
                        },
                        onDoubleTap = switchLens
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            val current = controller.getZoomRatio()
                            val range = controller.getZoomRange()
                            val target = if (range != null)
                                (current * zoom).coerceIn(range.first, range.second)
                            else current * zoom
                            if (controller.setZoomRatio(target)) { activeZoom = target; poke() }
                        }
                    }
                }
        )

        if (gridOn && gridType != GridType.OFF) GridOverlay(gridType)

        // 水平仪：中央参考线 + 随设备横滚旋转的地平线（roll 状态仅在绘制域内读取，只触发重绘）
        if (settings.levelEnabled) LevelerOverlay(rollProvider = { rollDeg })

        // AI 主体框（金框标出检测到的人脸/物体，帮助小白对准）
        if (aiOn.value) {
            SubjectOverlay(
                subjects = advice.subjects,
                faces = advice.faces,
                glView = glView,
            )
        }

        // debug HUD：GL 几何状态 + crop 矩阵 A/B 开关（真机诊断预览拉伸/方向）
        if (BuildConfig.DEBUG) {
            var hud by remember { mutableStateOf("waiting…") }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(500)
                    hud = "buf=${glView.bufferWidth}x${glView.bufferHeight} " +
                        "rot=${glView.rotDeg} hasCT=${glView.hasCameraTransformNow}\n" +
                        "crop=${glView.cropRectNow?.toShortString() ?: "full"} " +
                        "comp=${glView.orientationCompCw}\n" +
                        "view=${glView.width}x${glView.height}"
                }
            }
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(4.dp)
            ) {
                Text(hud, color = Color(0xFF7CFC00), fontSize = 9.sp)
                Text(
                    if (glView.ignoreCropForDiagnosis) "[crop:OFF 点击恢复]"
                    else "[crop:ON 点击跳过]",
                    color = Color.Yellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        glView.ignoreCropForDiagnosis = !glView.ignoreCropForDiagnosis
                    },
                )
            }
        }

        focusPoint?.let { p ->
            val ringScale = lerp(1.35f, 1f, ringAnim.value)
            val ringAlpha = (1f - ringAnim.value).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White, radius = 46.dp.toPx() * ringScale, center = p, alpha = ringAlpha, style = Stroke(width = 3.dp.toPx()))
                drawCircle(Color.White, radius = 4.dp.toPx(), center = p, alpha = ringAlpha)
            }
        }

        if (flashOverlay.value > 0.01f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = flashOverlay.value)))
        }

        if (countdown > 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("$countdown", color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)), exit = fadeOut(tween(350)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PillButton(
                    text = when (flashUi) {
                        FlashUi.OFF -> "闪光 关"
                        FlashUi.AUTO -> "闪光 自动"
                        FlashUi.ON -> "闪光 开"
                        FlashUi.TORCH -> "手电"
                    },
                    active = flashUi != FlashUi.OFF
                ) {
                    flashUi = when (flashUi) {
                        FlashUi.OFF -> FlashUi.AUTO
                        FlashUi.AUTO -> FlashUi.ON
                        FlashUi.ON -> FlashUi.TORCH
                        FlashUi.TORCH -> FlashUi.OFF
                    }
                    when (flashUi) {
                        FlashUi.OFF  -> { controller.setFlashMode(ImageCapture.FLASH_MODE_OFF);  controller.setTorch(false) }
                        FlashUi.AUTO -> { controller.setFlashMode(ImageCapture.FLASH_MODE_AUTO); controller.setTorch(false) }
                        FlashUi.ON   -> { controller.setFlashMode(ImageCapture.FLASH_MODE_ON);   controller.setTorch(false) }
                        FlashUi.TORCH-> { controller.setFlashMode(ImageCapture.FLASH_MODE_OFF);  controller.setTorch(true) }
                    }
                    poke()
                }
                PillButton(
                    text = if (gridOn && gridType != GridType.OFF) "网格 ${gridType.label}" else "网格 关",
                    active = gridOn && gridType != GridType.OFF
                ) {
                    gridOn = !gridOn; poke()
                }
                PillButton(
                    text = when (timerSec) { 0 -> "定时 关"; 3 -> "3 秒"; else -> "10 秒" },
                    active = timerSec > 0
                ) {
                    timerSec = when (timerSec) { 0 -> 3; 3 -> 10; else -> 0 }; poke()
                }
                PillButton(text = if (aiOn.value) "AI 助手" else "AI 关", active = aiOn.value) {
                    aiOn.value = !aiOn.value
                    if (!aiOn.value) coach.clear()
                    poke()
                }
            }
        }

        // AI 教练卡：评分 + 一句话引导 + 参数建议（构图到位亮金）
        if (aiOn.value) {
            AiCoachCard(
                advice = advice,
                currentZoom = activeZoom,
                torchOn = flashUi == FlashUi.TORCH,
                onApplyZoom = { z ->
                    val range = zoomRange
                    val target = if (range != null) z.coerceIn(range.first, range.second) else z
                    if (controller.setZoomRatio(target)) {
                        activeZoom = target
                        if (BuildConfig.DEBUG) {
                            Log.i(TAG_ZOOM_SUGGEST, "apply suggested zoom ${fmtZoom(z)}x -> ${fmtZoom(target)}x")
                        }
                    }
                    poke()
                },
                onToggleTorch = {
                    val on = flashUi != FlashUi.TORCH
                    flashUi = if (on) FlashUi.TORCH else FlashUi.OFF
                    if (on) {
                        controller.setFlashMode(ImageCapture.FLASH_MODE_OFF)
                        controller.setTorch(true)
                    } else {
                        controller.setTorch(false)
                    }
                    if (BuildConfig.DEBUG) Log.i(TAG_NIGHT_LIGHT, "torch suggestion toggled: on=$on")
                    poke()
                },
                modifier = Modifier.align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 滤镜面板：横向预设列表 + 强度滑杆（所见即所得，预览实时生效）
            AnimatedVisibility(visible = filterPanelOpen) {
                FilterPanel(
                    presets = FilterPresets.ALL,
                    selectedId = settings.selectedPresetId,
                    intensity = intensity,
                    onSelect = { id ->
                        scope.launch { settingsRepo.setSelectedPreset(id) }
                        poke()
                    },
                    onIntensity = { intensity = it },
                    onIntensityFinished = {
                        scope.launch { settingsRepo.setFilterIntensity(intensity) }
                    },
                )
            }
            // AI 滤镜推荐角标：仅原片预设下出现，点击直接应用
            val rec = advice.filterRec
            if (!filterPanelOpen && settings.aiFilterRecommendEnabled &&
                settings.selectedPresetId == "none" && rec != null
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(Color(0xFFF2C14E).copy(alpha = 0.92f))
                        .clickable {
                            scope.launch { settingsRepo.setSelectedPreset(rec.preset.id) }
                            filterPanelOpen = true
                            poke()
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        "✦ AI 推荐：试试 ${rec.preset.name}",
                        color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            AnimatedVisibility(visible = drawerOpen) {
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("手电常亮", color = Color.White, fontSize = 13.sp)
                        Spacer(Modifier.size(12.dp))
                        Switch(
                            checked = flashUi == FlashUi.TORCH,
                            onCheckedChange = { on ->
                                flashUi = if (on) FlashUi.TORCH else FlashUi.OFF
                                if (on) { controller.setFlashMode(ImageCapture.FLASH_MODE_OFF); controller.setTorch(true) }
                                else controller.setTorch(false)
                                poke()
                            }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpenSettings() }
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置", tint = Color.White)
                        Spacer(Modifier.size(8.dp))
                        Text("设置与画质", color = Color.White, fontSize = 13.sp)
                    }
                    Text(
                        "保存位置: 系统相册 / ${MediaRepository.RELATIVE_PATH}",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(tween(200)), exit = fadeOut(tween(350))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 档位按设备 ZoomRange 过滤：不支持的档不显示，超广角不足 0.6x 时映射最小档
                        val presets = listOf(0.6f, 1f, 2f, 5f)
                        val range = zoomRange
                        val levels = if (range == null) presets else {
                            val (min, max) = range
                            val l = presets.filter { it >= min - 0.01f && it <= max + 0.01f }.toMutableList()
                            if (min < 0.99f && l.none { it < 1f }) l.add(0, min)
                            l.toList()
                        }
                        levels.forEach { z ->
                            val selected = abs(activeZoom - z) < 0.05f
                            Box(
                                Modifier.clip(CircleShape)
                                    .background(if (selected) Color.White.copy(alpha = 0.95f) else Color.Transparent)
                                    .clickable {
                                        val clamped = if (range != null) z.coerceIn(range.first, range.second) else z
                                        activeZoom = clamped; controller.setZoomRatio(clamped); poke()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    if (z == z.toInt().toFloat()) "${z.toInt()}x" else "${(z * 10).toInt() / 10f}x",
                                    color = if (selected) Color.Black else Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "≈${ShotCoach.equivMm(activeZoom)}mm",
                        color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                            .clickable { drawerOpen = !drawerOpen; poke() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "更多控制", tint = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                        .clickable { onOpenGallery() },
                    contentAlignment = Alignment.Center
                ) {
                    thumb?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "最近照片",
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                }
                // 滤镜入口：选中非原片预设或面板打开时反白提示
                val filterActive = filterPanelOpen || settings.selectedPresetId != "none"
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (filterActive) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.35f))
                        .clickable { filterPanelOpen = !filterPanelOpen; poke() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "滤镜",
                        color = if (filterActive) Color.Black else Color.White,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    )
                }
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val shutterScale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "shutter")
                val readyToShoot = aiOn.value && advice.ready
                Box(
                    Modifier.size(74.dp).scale(shutterScale).clip(CircleShape)
                        .background(Color.White.copy(alpha = if (controlsVisible) 1f else 0.55f))
                        .then(
                            if (readyToShoot) Modifier.border(3.dp, Color(0xFFF2C14E), CircleShape)
                            else Modifier
                        )
                        .clickable(interactionSource = interaction, indication = null) { startCapture() }
                )
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                        .clickable { controller.switchCamera(); activeZoom = 1f; bindTick++; poke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "翻转相机", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PillButton(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.35f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            color = if (active) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 构图网格：类型来自设置页（三分法/黄金分割/对角线/中心十字/方形网格） */
@Composable
private fun GridOverlay(type: GridType) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val color = Color.White.copy(alpha = 0.35f)
        val sw = 1.dp.toPx()
        fun vLine(f: Float) = drawLine(color, Offset(w * f, 0f), Offset(w * f, h), strokeWidth = sw)
        fun hLine(f: Float) = drawLine(color, Offset(0f, h * f), Offset(w, h * f), strokeWidth = sw)
        when (type) {
            GridType.THIRDS -> { vLine(1f / 3); vLine(2f / 3); hLine(1f / 3); hLine(2f / 3) }
            GridType.GOLDEN -> { vLine(0.382f); vLine(0.618f); hLine(0.382f); hLine(0.618f) }
            GridType.DIAGONAL -> {
                drawLine(color, Offset(0f, 0f), Offset(w, h), strokeWidth = sw)
                drawLine(color, Offset(w, 0f), Offset(0f, h), strokeWidth = sw)
            }
            GridType.CENTER -> { vLine(0.5f); hLine(0.5f) }
            GridType.SQUARE -> { for (i in 1..3) { vLine(i / 4f); hLine(i / 4f) } }
            GridType.OFF -> {}
        }
    }
}

/**
 * 水平仪叠加层：中央固定参考线 + 随设备横滚角旋转的真地平线。
 * |roll|≤1.5° 吸附归 0 并变绿（已水平），>3° 变黄提示倾斜明显。
 * [rollProvider] 在绘制域内读取，roll 变化只触发本层重绘，不重组整屏。
 */
@Composable
private fun LevelerOverlay(rollProvider: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val half = size.width * 0.16f
        val roll = rollProvider()
        val absRoll = abs(roll)
        val color = when {
            absRoll <= 1.5f -> Color(0xFF3DDC84) // 已水平：绿
            absRoll > 3f -> Color(0xFFF2C14E)    // 倾斜明显：黄
            else -> Color.White
        }
        // 固定水平参考线
        drawLine(
            Color.White.copy(alpha = 0.35f),
            Offset(cx - half, cy), Offset(cx + half, cy),
            strokeWidth = 1.dp.toPx(),
        )
        // 水平仪线：随 roll 旋转；进入水平阈值后吸附到 0，与参考线重合
        val shown = if (absRoll <= 1.5f) 0f else roll
        rotate(shown, pivot = Offset(cx, cy)) {
            drawLine(
                color,
                Offset(cx - half, cy), Offset(cx + half, cy),
                strokeWidth = 2.dp.toPx(),
            )
        }
        drawCircle(color, radius = 3.dp.toPx(), center = Offset(cx, cy))
    }
}

/**
 * AI 主体框：金框标出人脸/物体，让小白一眼看清「拍的是什么」。
 * 坐标映射直接用 [CameraGlView.mapAnalysisPointToView]——与渲染器同源
 * （旋转/cropRect/FILL_CENTER/前摄镜像一套几何），框不会因全面屏裁剪而漂移。
 */
@Composable
private fun SubjectOverlay(
    subjects: List<RectF>,
    faces: List<RectF>,
    glView: CameraGlView,
) {
    if (subjects.isEmpty() && faces.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun box(r: RectF, color: Color, stroke: Float) {
            val (lx, ty) = glView.mapAnalysisPointToView(r.left, r.top)
            val (rx, by) = glView.mapAnalysisPointToView(r.right, r.bottom)
            drawRoundRect(
                color = color,
                topLeft = Offset(lx * w, ty * h),
                size = Size(rx * w - lx * w, by * h - ty * h),
                cornerRadius = CornerRadius(5.dp.toPx()),
                style = Stroke(width = stroke),
            )
        }
        subjects.forEach { box(it, Color(0xFFF2C14E).copy(alpha = 0.5f), 1.5.dp.toPx()) }
        faces.forEach { box(it, Color(0xFFF2C14E).copy(alpha = 0.95f), 2.dp.toPx()) }
    }
}

/**
 * AI 教练卡：构图评分 + 一句话引导 + 焦距/曝光参数建议。
 * 构图到位时亮金 —— 小白看到金边就可以直接按快门。
 * 焦距建议与暗光手电建议为可点 chip：一键变焦到位 / 一键开关手电。
 */
@Composable
private fun AiCoachCard(
    advice: ShotAdvice,
    currentZoom: Float,
    torchOn: Boolean,
    onApplyZoom: (Float) -> Unit,
    onToggleTorch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (advice.score == 0 && advice.advice.isEmpty()) return

    val gold = Color(0xFFF2C14E)
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = if (advice.ready) 0.72f else 0.42f))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✦", color = gold, fontSize = 13.sp)
            Spacer(Modifier.size(5.dp))
            Text("${advice.score}", color = gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (advice.directions.isNotEmpty()) {
                Spacer(Modifier.size(7.dp))
                Text(
                    advice.directions.joinToString(" ") { glyphFor(it) },
                    color = gold, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                if (advice.ready) "✓ 构图到位，可以拍了" else advice.advice,
                color = Color.White, fontSize = 13.sp, maxLines = 1,
            )
        }
        Spacer(Modifier.size(5.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            advice.sceneLabel?.let { InfoChip(it) }
            advice.suggestedZoom?.let { z ->
                ActionChip("建议 ${fmtZoom(z)}x · ${ShotCoach.equivMm(z)}mm") { onApplyZoom(z) }
            }
            if (advice.lowLight) {
                // 暗光：不自动开灯，只给一键建议（可再点关闭）
                ActionChip(if (torchOn) "关闭手电" else "开启手电补光", active = torchOn) {
                    onToggleTorch()
                }
            } else {
                advice.exposureTip?.let { InfoChip(it) }
            }
            if (advice.suggestedZoom == null) {
                InfoChip("≈${ShotCoach.equivMm(currentZoom)}mm")
            }
        }
    }
}

/** 纯信息 chip（不可点），与原单行建议文本同风格 */
@Composable
private fun InfoChip(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, maxLines = 1)
}

/** 可执行建议 chip：金底提示可点，active 时反白表示已生效 */
@Composable
private fun ActionChip(text: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(
                if (active) Color.White.copy(alpha = 0.92f)
                else Color(0xFFF2C14E).copy(alpha = 0.25f)
            )
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            color = if (active) Color.Black else Color(0xFFF2C14E),
            fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        )
    }
}

/**
 * 滤镜面板：横向滑动预设列表（名称 + 分组标签，选中金边）+ 强度滑杆。
 * 强度用 [FilterParams.lerpParams] 在中性与预设间插值，松手时才写回 DataStore。
 */
@Composable
private fun FilterPanel(
    presets: List<FilterPreset>,
    selectedId: String,
    intensity: Float,
    onSelect: (String) -> Unit,
    onIntensity: (Float) -> Unit,
    onIntensityFinished: () -> Unit,
) {
    val gold = Color(0xFFF2C14E)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(vertical = 10.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            items(presets, key = { it.id }) { p ->
                val selected = p.id == selectedId
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                        .then(
                            if (selected) Modifier.border(1.5.dp, gold, RoundedCornerShape(10.dp))
                            else Modifier
                        )
                        .clickable { onSelect(p.id) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        p.name,
                        color = if (selected) gold else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                    Text(
                        p.group.label + (p.cameraName?.let { " · $it" } ?: ""),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        if (selectedId != FilterPreset.NONE.id) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Text("强度", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                Slider(
                    value = intensity,
                    onValueChange = onIntensity,
                    onValueChangeFinished = onIntensityFinished,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("${(intensity * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

private fun glyphFor(d: GuideDirection): String = when (d) {
    GuideDirection.MOVE_LEFT -> "←"
    GuideDirection.MOVE_RIGHT -> "→"
    GuideDirection.MOVE_UP -> "↑"
    GuideDirection.MOVE_DOWN -> "↓"
    GuideDirection.ZOOM_IN -> "⊕"
    GuideDirection.ZOOM_OUT -> "⊖"
    GuideDirection.TILT_LEVEL -> "⌣"
    GuideDirection.HOLD_STEADY -> "●"
    GuideDirection.NONE -> ""
}

private fun fmtZoom(z: Float): String =
    if (z == z.toInt().toFloat()) "${z.toInt()}" else "$z"

// 从 MediaStore Uri 解码缩略图（成片已按像素级转正，无需再处理 EXIF）
private suspend fun decodeThumb(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { ins ->
            BitmapFactory.decodeStream(ins, null, BitmapFactory.Options().apply { inSampleSize = 8 })
        }
    }.getOrNull()
}
