package com.dokacam.camera.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dokacam.camera.camera.LensOption
import com.dokacam.camera.data.model.FilterGroup
import com.dokacam.camera.data.model.FilterPreset

/**
 * 焦段条：0.5x / 1x / 2x / 3x
 * 选中项放大 + 金色高亮，点击即切。
 */
@Composable
fun ZoomLensBar(
    options: List<LensOption>,
    active: Float,
    onZoom: (Float) -> Unit,
) {
    if (options.size <= 1) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { opt ->
            val isActive = kotlin.math.abs(opt.zoomRatio - active) < 0.05f
            Box(
                Modifier
                    .padding(horizontal = 10.dp)
                    .size(if (isActive) 42.dp else 38.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) Color.White.copy(alpha = 0.22f)
                        else Color.Transparent,
                    )
                    .clickable { onZoom(opt.zoomRatio) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    opt.label,
                    color = if (isActive) Color(0xFFF2C14E) else Color.White,
                    fontSize = if (isActive) 14.sp else 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * 滤镜抽屉：横向滚动 + 分组组头 + 强度滑杆。
 * AI 推荐项带金色角标。
 */
@Composable
fun FilterStrip(
    presets: List<FilterPreset>,
    activeId: String,
    intensity: Float,
    onPick: (FilterPreset) -> Unit,
    onIntensity: (Float) -> Unit,
    recommendedIds: Set<String> = emptySet(),
) {
    Column(Modifier.fillMaxWidth()) {
        LazyRow(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(presetsWithHeaders(presets), key = { it.key }) { item ->
                val preset = item.preset
                if (preset == null) {
                    Text(
                        item.label,
                        color = Color(0xFF777777),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
                    )
                } else {
                    val isActive = preset.id == activeId
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(preset) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Box {
                            Surface(
                                shape = CircleShape,
                                color = if (isActive) MaterialTheme.colorScheme.primary
                                else Color.White.copy(alpha = 0.14f),
                                modifier = Modifier.size(44.dp),
                            ) {}
                            if (preset.id in recommendedIds) {
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .align(Alignment.TopEnd)
                                        .background(Color(0xFFF2C14E), CircleShape),
                                )
                            }
                            Text(
                                preset.name.take(2),
                                color = if (isActive) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            preset.name,
                            color = if (isActive) Color(0xFFF2C14E) else Color(0xFFBBBBBB),
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }

        // 强度滑杆（选中原片时隐藏）
        if (activeId != "none") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("强度", color = Color(0xFF999999), fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = intensity,
                    onValueChange = onIntensity,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFF2C14E),
                        activeTrackColor = Color(0xFFF2C14E),
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    ),
                    modifier = Modifier.weight(1f).height(28.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("${(intensity * 100).toInt()}%", color = Color(0xFF999999), fontSize = 11.sp)
            }
        }
    }
}

/** 平铺列表 →（组头，滤镜）交错结构 */
private data class RowItem(val key: String, val label: String, val preset: FilterPreset?)

private fun presetsWithHeaders(presets: List<FilterPreset>): List<RowItem> {
    val out = mutableListOf<RowItem>()
    var lastGroup: FilterGroup? = null
    presets.forEach { p ->
        if (p.group != lastGroup) {
            out += RowItem("header_${p.group.name}", p.group.label, null)
            lastGroup = p.group
        }
        out += RowItem(p.id, p.name, p)
    }
    return out
}
