package com.mobilestudio.app.rendering

import android.graphics.Bitmap

/**
 * A live frame producer. Camera, screen-capture and video sources each implement this by
 * publishing their latest decoded frame; the compositor pulls (never blocks on) whatever is
 * newest on every tick so a slow source can't stall the whole scene.
 */
interface FrameSource {
    fun latestFrame(): Bitmap?
    fun start()
    fun stop()
    fun release()
}
