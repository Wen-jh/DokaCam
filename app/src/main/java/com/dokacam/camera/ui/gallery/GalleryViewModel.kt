package com.dokacam.camera.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dokacam.camera.data.media.MediaRepository
import com.dokacam.camera.data.media.PhotoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepository(app)

    private val _photos = MutableStateFlow<List<PhotoItem>>(emptyList())
    val photos: StateFlow<List<PhotoItem>> = _photos.asStateFlow()

    fun refresh() = viewModelScope.launch {
        _photos.value = repo.queryMyPhotos()
    }

    fun delete(item: PhotoItem) = viewModelScope.launch {
        repo.delete(item.uri)
        refresh()
    }
}
