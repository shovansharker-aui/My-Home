package com.myhome.app.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/** Bridges hardware key presses from the activity into whichever screen wants them. */
object HardwareKeys {
    val shutter = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** True only while the live screen is showing, so volume keys behave normally elsewhere. */
    @Volatile
    var shutterScreenActive = false
}
