package com.dokacam.camera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 相机 App 常驻暗色：取景器周边一切 UI 都应「退后」，
// 让画面本身成为唯一的亮部（Doka 的「纯粹」哲学）。
private val DokaColors = darkColorScheme(
    primary = Color(0xFFF2C14E),
    onPrimary = Color(0xFF1A1A1A),
    secondary = Color(0xFF9A9A9A),
    onSecondary = Color(0xFFEEEEEE),
    tertiary = Color(0xFF7EC8C0),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF111111),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFB8B8B8),
    outline = Color(0xFF444444),
    error = Color(0xFFE5484D),
)

@Composable
fun DokaCamTheme(content: @Composable () -> Unit) {
    // 相机永远用暗色，忽略系统浅色，避免取景时突兀
    MaterialTheme(
        colorScheme = DokaColors,
        content = content,
    )
}
