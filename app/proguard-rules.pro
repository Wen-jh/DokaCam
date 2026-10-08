# ML Kit 模型
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }

# 数据模型（DataStore 反射 / 序列化）
-keep class com.dokacam.camera.data.model.** { *; }

# GL 渲染管线用到的原生方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# Compose
-dontwarn androidx.compose.**

# 保留行号便于排查线上问题
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
