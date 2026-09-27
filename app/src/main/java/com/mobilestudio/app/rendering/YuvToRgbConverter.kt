package com.mobilestudio.app.rendering

import android.graphics.*
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Converts a CameraX YUV_420_888 ImageProxy into an ARGB_8888 Bitmap.
 *
 * We go through YuvImage -> JPEG -> Bitmap rather than a hand-rolled RenderScript/GLES pipeline.
 * RenderScript is deprecated on modern Android and a GLES path adds real complexity for marginal
 * gain at the preview/record resolutions this app targets (max 1080p). This conversion is CPU
 * work, so on lower-end devices the "Performance mode" setting (Section 12) downsamples the
 * analysis resolution to keep it cheap.
 */
object YuvToRgbConverter {

    fun toBitmap(image: ImageProxy): Bitmap? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
        val bytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
