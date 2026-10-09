package com.dokacam.camera.ui.camera

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import com.dokacam.camera.ai.ShotAdvice
import com.dokacam.camera.ai.ShotCoach
import com.dokacam.camera.camera.CameraController
import com.dokacam.camera.data.model.GuideDirection
import com.dokacam.camera.gl.CameraGlView
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class FlashUi { OFF, ON, TORCH }

@Composable
fun CameraScreen() {
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

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }

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
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
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
    var gridOn by remember { mutableStateOf(false) }
    var timerSec by remember { mutableStateOf(0) }
    var drawerOpen by remember { mutableStateOf(false) }
    var activeZoom by remember { mutableStateOf(1f) }

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

    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    val saveDirPath = remember {
        File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "AICam").absolutePath
    }
    fun loadThumb(path: String?) {
        if (path == null) return
        thumb = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 8 })
    }
    LaunchedEffect(Unit) {
        val latest = File(saveDirPath).listFiles()
            ?.filter { it.extension == "jpg" }
            ?.maxByOrNull { it.lastModified() }
        if (latest != null) loadThumb(latest.absolutePath)
    }

    fun doCapture() {
        flashTick++
        controller.takePhoto(
            onSaved = { path ->
                loadThumb(path)
                Toast.makeText(context, "已保存: AICam/${File(path).name}", Toast.LENGTH_SHORT).show()
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())

        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            poke()
                            if (controller.focusAt(offset.x, offset.y, viewSize.width.toFloat(), viewSize.height.toFloat())) {
                                focusPoint = offset
                                focusTick++
                            }
                        },
                        onDoubleTap = { controller.switchCamera(); activeZoom = 1f; poke() }
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

        if (gridOn) GridOverlay()

        // AI 主体框（金框标出检测到的人脸/物体，帮助小白对准）
        if (aiOn.value) {
            SubjectOverlay(
                subjects = advice.subjects,
                faces = advice.faces,
                mirror = controller.frontFacing,
                frameAspect = 3f / 4f,
                viewAspect = if (viewSize.height != 0)
                    viewSize.width.toFloat() / viewSize.height.toFloat() else 3f / 4f,
            )
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
                    text = when (flashUi) { FlashUi.OFF -> "闪光 关"; FlashUi.ON -> "闪光 开"; FlashUi.TORCH -> "手电" },
                    active = flashUi != FlashUi.OFF
                ) {
                    flashUi = when (flashUi) { FlashUi.OFF -> FlashUi.ON; FlashUi.ON -> FlashUi.TORCH; FlashUi.TORCH -> FlashUi.OFF }
                    when (flashUi) {
                        FlashUi.OFF  -> { controller.setFlashMode(ImageCapture.FLASH_MODE_OFF); controller.setTorch(false) }
                        FlashUi.ON   -> { controller.setFlashMode(ImageCapture.FLASH_MODE_ON);  controller.setTorch(false) }
                        FlashUi.TORCH-> { controller.setFlashMode(ImageCapture.FLASH_MODE_OFF); controller.setTorch(true) }
                    }
                    poke()
                }
                PillButton(text = if (gridOn) "构图 开" else "构图 关", active = gridOn) {
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
                modifier = Modifier.align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                    Text("保存位置: $saveDirPath", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(tween(200)), exit = fadeOut(tween(350))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.6f, 1f, 2f, 5f).forEach { z ->
                            val selected = abs(activeZoom - z) < 0.01f
                            Box(
                                Modifier.clip(CircleShape)
                                    .background(if (selected) Color.White.copy(alpha = 0.95f) else Color.Transparent)
                                    .clickable { activeZoom = z; controller.setZoomRatio(z); poke() }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    if (z == z.toInt().toFloat()) "${z.toInt()}x" else "${z}x",
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
                horizontalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
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
                        .clickable { controller.switchCamera(); activeZoom = 1f; poke() },
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

@Composable
private fun GridOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        for (i in 1..2) {
            val x = w * i / 3
            drawLine(Color.White.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
            val y = h * i / 3
            drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }
    }
}

/**
 * AI 主体框：金框标出人脸/物体，让小白一眼看清「拍的是什么」。
 * 前摄预览镜像，框也要镜像回来才能对上。
 * 预览是 FILL_CENTER 中心裁剪：先把分析帧坐标重映射进可视子矩形再画，
 * 否则在全面屏上框会横向漂移。
 */
@Composable
private fun SubjectOverlay(
    subjects: List<RectF>,
    faces: List<RectF>,
    mirror: Boolean,
    frameAspect: Float,
    viewAspect: Float,
) {
    if (subjects.isEmpty() && faces.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 与 CameraGlView 的 FILL_CENTER 一致：把归一化帧坐标映射到屏幕归一化坐标
        val ca = frameAspect
        val va = viewAspect
        var ax = 0f; var sx = 1f; var ay = 0f; var sy = 1f
        if (ca > va) {
            val visW = va / ca
            ax = (1f - visW) / 2f; sx = 1f / visW
        } else if (va > ca) {
            val visH = ca / va
            ay = (1f - visH) / 2f; sy = 1f / visH
        }
        fun mapX(x: Float) = ((x - ax) * sx).coerceIn(0f, 1f)
        fun mapY(y: Float) = ((y - ay) * sy).coerceIn(0f, 1f)

        fun box(r: RectF, color: Color, stroke: Float) {
            val l = if (mirror) (1f - mapX(r.right)) * w else mapX(r.left) * w
            val rt = if (mirror) (1f - mapX(r.left)) * w else mapX(r.right) * w
            drawRoundRect(
                color = color,
                topLeft = Offset(l, mapY(r.top) * h),
                size = Size(rt - l, (mapY(r.bottom) - mapY(r.top)) * h),
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
 */
@Composable
private fun AiCoachCard(
    advice: ShotAdvice,
    currentZoom: Float,
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
        val chips = buildList {
            advice.sceneLabel?.let { add(it) }
            advice.suggestedZoom?.let { add("建议 ${fmtZoom(it)}x · ${ShotCoach.equivMm(it)}mm") }
            advice.exposureTip?.let { add(it) }
            if (advice.suggestedZoom == null) add("≈${ShotCoach.equivMm(currentZoom)}mm")
        }
        if (chips.isNotEmpty()) {
            Spacer(Modifier.size(4.dp))
            Text(
                chips.joinToString(" · "),
                color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, maxLines = 1,
            )
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
