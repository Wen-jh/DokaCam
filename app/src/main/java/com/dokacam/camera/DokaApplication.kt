package com.dokacam.camera

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class DokaApplication : Application(), ImageLoaderFactory {

    /**
     * Coil 全局图床：相册缩略图专用。
     * 限制内存缓存 15%，避免与相机 GL 管线抢内存（相机 App OOM 重灾区）。
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
