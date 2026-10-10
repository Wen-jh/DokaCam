package com.dokacam.camera.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

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
 * 相册仓库：只操作本 App 拍摄的照片（按 owner 包名过滤），
 * 不索引用户整机的相册 —— 隐私边界与系统相机一致。
 */
class MediaRepository(private val context: Context) {

    private val collection: Uri =
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    /** 查询本应用拍摄的照片 */
    suspend fun queryMyPhotos(): List<PhotoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PhotoItem>()
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
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    "${MediaStore.Images.Media.OWNER_PACKAGE_NAME}=?"
                } else {
                    // 旧版本无 owner 列：按命名约定过滤，避免把用户整机相册都拉进来
                    "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? ESCAPE '\\'"
                },
                if (android.os.Build.VERSION.SDK_INT >= 29) arrayOf(context.packageName)
                else arrayOf("AICam\\_%"),
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

    /**
     * 保存 Bitmap 到相册。
     * @return 保存后的 Uri
     */
    suspend fun saveBitmap(
        bitmap: Bitmap,
        displayName: String,
        mime: String = "image/jpeg",
        quality: Int = 95,
        onStream: ((OutputStream) -> Unit)? = null,
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AICam")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                } else {
                    @Suppress("DEPRECATION")
                    put(MediaStore.Images.Media.DATA, "")
                }
            }
            val uri = context.contentResolver.insert(collection, values) ?: return@runCatching null
            context.contentResolver.openOutputStream(uri)?.use { os ->
                if (onStream != null) onStream(os)
                else bitmap.compress(Bitmap.CompressFormat.JPEG, quality, os)
            }
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            uri
        }.getOrNull()
    }

    /** 追加保存原图（saveOriginal 开启时的第二份输出） */
    suspend fun saveOriginalBytes(bytes: ByteArray, displayName: String): Uri? =
        saveBitmap(
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            displayName = displayName,
        ) { os -> os.write(bytes) }

    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }
}
