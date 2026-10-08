package com.dokacam.camera.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dokacam.camera.data.model.AspectRatio

/**
 * 设置页：拍摄 / 画质 / 水印 / AI 四组开关。
 * 设计原则：只放「会影响拍照行为」的项，不放运行时切换的项（那些在取景器上）。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val s by vm.settings.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Text("←", color = Color.White, fontSize = 22.sp)
            }
            Text("设置", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Section("拍摄") {
            ToggleRow("保存原图", "同时保留未经滤镜处理的原片", s.saveOriginal, vm::setSaveOriginal)
            ToggleRow("快门音效", null, s.shutterSound, vm::setShutterSound)
            ToggleRow("触屏拍摄", "点击取景器任意处即拍照", s.tapToCapture, vm::setTapToCapture)
            ToggleRow("水平仪", "取景时显示水平提示", s.levelEnabled, vm::setLevelEnabled)
            ToggleRow("前置镜像", "自拍预览与成片水平翻转", s.mirrorFrontCamera, vm::setMirrorFront)
            // 画幅
            ChoiceRow(
                "画幅比例",
                AspectRatio.entries.map { it.label },
                s.aspectRatio.label,
            ) { vm.setAspectRatio(AspectRatio.fromLabel(it)) }
        }

        Section("画质") {
            ChoiceRow(
                "照片格式",
                listOf("JPEG", "HEIF"),
                s.photoFormat.name,
            ) { vm.setPhotoFormat(com.dokacam.camera.data.model.PhotoFormat.valueOf(it)) }
            SliderRow(
                "JPEG 画质",
                s.jpegQuality.toFloat(),
                60f..100f,
                display = "${s.jpegQuality}",
            ) { vm.setJpegQuality(it.toInt()) }
            ToggleRow(
                "CCD 低清模式",
                "刻意降低分辨率并增强噪点，还原早期数码相机质感",
                s.ccdModeEnabled, vm::setCcdMode,
            )
        }

        Section("水印") {
            ToggleRow("日期水印", "复古胶片日期戳，如 ’98 7 14", s.dateStampEnabled, vm::setDateStamp)
            if (s.dateStampEnabled) {
                ChoiceRow(
                    "日期样式",
                    listOf("yy M d", "yyyy MM dd", "yy/MM/dd"),
                    s.dateStampFormat,
                ) { vm.setDateStampFormat(it) }
            }
            ToggleRow("机型水印", "在照片角落写入设备型号", s.modelStampEnabled, vm::setModelStamp)
        }

        Section("AI 功能") {
            ToggleRow(
                "AI 构图引导", "取景时实时主体检测与构图建议",
                s.aiCompositionEnabled, vm::setAiComposition,
            )
            ToggleRow(
                "AI 滤镜推荐", "根据场景与光线自动推荐滤镜",
                s.aiFilterRecommendEnabled, vm::setAiRecommend,
            )
            ToggleRow("人像美颜", "磨皮 + 肤色提亮（保留质感）", s.beautyEnabled, vm::setBeauty)
            if (s.beautyEnabled) {
                SliderRow(
                    "美颜强度",
                    s.beautyIntensity,
                    0f..1f,
                    display = "${(s.beautyIntensity * 100).toInt()}%",
                ) { vm.setBeautyIntensity(it) }
            }
        }

        Section("关于") {
            StaticRow("版本", "1.0.0")
            StaticRow("开源许可", "MIT / Apache-2.0 / GPL-3.0 组件声明见 README")
        }

        Spacer(Modifier.height(48.dp))
    }
}

// ------------------------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        color = Color(0xFFF2C14E),
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .background(Color(0xFF1A1A1A), RoundedCornerShape(14.dp)),
    ) { content() }
}

@Composable
private fun ToggleRow(
    title: String,
    desc: String?,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!value) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (desc != null) {
                Spacer(Modifier.height(2.dp))
                Text(desc, color = Color(0xFF888888), fontSize = 12.sp)
            }
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFFF2C14E),
                checkedThumbColor = Color(0xFF1A1A1A),
            ),
        )
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    options: List<String>,
    active: String,
    onPick: (String) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, color = Color.White, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { opt ->
                val isActive = opt == active
                Text(
                    opt,
                    color = if (isActive) Color.Black else Color(0xFFBBBBBB),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .background(
                            if (isActive) Color(0xFFF2C14E) else Color(0xFF2A2A2A),
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onPick(opt) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(display, color = Color(0xFFF2C14E), fontSize = 13.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StaticRow(title: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = Color.White, fontSize = 15.sp)
        Text(value, color = Color(0xFF888888), fontSize = 13.sp)
    }
}
