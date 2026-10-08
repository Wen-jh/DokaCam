package com.dokacam.camera.data.model

/** 画幅比例 */
enum class AspectRatio(val label: String, val ratio: Float) {
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_1_1("1:1", 1f),
    RATIO_3_4("3:4", 3f / 4f),
    RATIO_9_16("9:16", 9f / 16f);

    companion object {
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: RATIO_4_3
    }
}

/** 网格线类型 —— 从新手辅助到进阶构图 */
enum class GridType(val label: String) {
    OFF("关闭"),
    THIRDS("三分法"),
    GOLDEN("黄金分割"),
    DIAGONAL("对角线"),
    CENTER("中心十字"),
    SQUARE("方形网格");

    companion object {
        fun fromName(name: String) = entries.firstOrNull { it.name == name } ?: OFF
    }
}

/** 闪光灯模式 */
enum class FlashMode(val label: String) {
    OFF("关闭"),
    AUTO("自动"),
    ON("开启"),
    TORCH("常亮"),
}

/** 定时拍摄 */
enum class TimerDelay(val label: String, val seconds: Int) {
    OFF("关", 0),
    S_3("3s", 3),
    S_10("10s", 10);

    companion object {
        fun fromName(name: String) = entries.firstOrNull { it.name == name } ?: OFF
    }
}

/** 照片保存格式 */
enum class PhotoFormat(val label: String, val mime: String, val ext: String) {
    JPEG("JPEG", "image/jpeg", "jpg"),
    HEIF("HEIF", "image/heif", "heic"),
}

/** 水平仪状态 */
enum class LevelState { LEVEL, TILTED_UP, TILTED_DOWN, ROLLED }

/** 构图引导方向提示 */
enum class GuideDirection { NONE, MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN, ZOOM_IN, ZOOM_OUT, TILT_LEVEL, HOLD_STEADY }

/**
 * 完整的拍摄设置快照。UI 层单向从 ViewModel 读取，
 * 底层 CameraController 据此重新绑定用例。
 */
data class CameraSettings(
    val aspectRatio: AspectRatio = AspectRatio.RATIO_4_3,
    val gridType: GridType = GridType.THIRDS,
    val flashMode: FlashMode = FlashMode.OFF,
    val timerDelay: TimerDelay = TimerDelay.OFF,
    val levelEnabled: Boolean = true,
    val tapToCapture: Boolean = false,
    val saveOriginal: Boolean = false,
    val mirrorFrontCamera: Boolean = true,
    val shutterSound: Boolean = true,
    val photoFormat: PhotoFormat = PhotoFormat.JPEG,
    val jpegQuality: Int = 95,
    val aiCompositionEnabled: Boolean = true,
    val aiFilterRecommendEnabled: Boolean = true,
    val beautyEnabled: Boolean = false,
    val beautyIntensity: Float = 0.4f,
    val dateStampEnabled: Boolean = false,
    val dateStampFormat: String = "yy M d",
    val locationStampEnabled: Boolean = false,
    val modelStampEnabled: Boolean = false,
    val ccdModeEnabled: Boolean = false,
    val selectedPresetId: String = "none",
    val filterIntensity: Float = 1f,
)
