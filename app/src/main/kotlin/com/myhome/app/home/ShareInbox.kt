package com.myhome.app.home

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Content another app has shared into Ahshan's Home ("Send" from Google Keep, or an
 * image from the gallery). The activity drops it here and the printer's Text
 * screen picks it up, so the hand-over works whether that screen is already
 * open or about to be.
 */
object ShareInbox {
    data class Item(val id: Long, val text: String?, val image: Uri?)

    private val _pending = MutableStateFlow<Item?>(null)
    val pending: StateFlow<Item?> = _pending.asStateFlow()

    fun offer(text: String?, image: Uri?) {
        _pending.value = Item(System.nanoTime(), text, image)
    }

    fun clear() {
        _pending.value = null
    }
}
