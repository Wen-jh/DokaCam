package com.dokacam.camera.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** 相册条目（懒加载缩略图） */
data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val size: Long,
)

/**
 * 相册仓库：照片统一写入系统相册 Pictures/AICam。
 * 查询按 RELATIVE_PATH（API 29+）/ DATA（API 26-28）目录前缀过滤，
 * 不用 OWNER_PACKAGE_NAME —— 重装、debug/release 签名切换后旧照片仍然可见。
 */
class MediaRepository(private val context: Context) {

    companion object {
        /** 系统相册里的保存目录（Pictures/AICam） */
        const val RELATIVE_PATH = "Pictures/AICam"
    }

    private val collection: Uri =
        if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    /** API 26-28 传统外部存储的实际目录 */
    @Suppress("DEPRECATION")
    private fun legacyDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AICam")

    /** 查询本应用拍摄的照片（按保存目录过滤，最新在前） */
    suspend fun queryMyPhotos(): List<PhotoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PhotoItem>()
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= 29) {
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            args = arrayOf("$RELATIVE_PATH/%")
        } else {
            selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            args = arrayOf("${legacyDir().absolutePath}/%")
        }
        runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT,
                    MediaStore.Images.Media.SIZE,
                ),
                selection,
                args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    list += PhotoItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = c.getString(iName) ?: "",
                        dateTaken = c.getLong(iDate) * 1000,
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        size = c.getLong(iSize),
                    )
                }
            }
        }
        list
    }

    /** 最新一张照片（取景器缩略图用） */
    suspend fun queryLatest(): PhotoItem? = queryMyPhotos().firstOrNull()

    /**
     * 保存 JPEG 字节到系统相册。
     * API 29+ 走 RELATIVE_PATH + IS_PENDING 流程；API 26-28 直接写传统外部存储目录再登记。
     * @return 保存后的 Uri，失败为 null
     */
    suspend fun saveBytes(
        bytes: ByteArray,
        displayName: String,
        mime: String = "image/jpeg",
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "$RELATIVE_PATH/")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(collection, values) ?: return@runCatching null
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IllegalStateException("openOutputStream 失败")
                } catch (e: Exception) {
                    // 写入失败时清掉半成品条目，避免相册里留 0 字节占位
                    context.contentResolver.delete(uri, null, null)
                    throw e
                }
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
                uri
            } else {
                // legacy：先落盘再登记 DATA，MediaStore 直接索引已有文件
                val dir = legacyDir().apply { mkdirs() }
                val file = File(dir, displayName)
                file.writeBytes(bytes)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    @Suppress("DEPRECATION")
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                }
                context.contentResolver.insert(collection, values)
            }
        }.getOrNull()
    }

    /** 保存 Bitmap（按 quality 压缩为 JPEG）到系统相册 */
    suspend fun saveBitmap(
        bitmap: Bitmap,
        displayName: String,
        mime: String = "image/jpeg",
        quality: Int = 95,
    ): Uri? {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(60, 100), baos)
        return saveBytes(baos.toByteArray(), displayName, mime)
    }

    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }
}
