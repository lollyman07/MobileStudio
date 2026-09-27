package com.mobilestudio.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size
import com.mobilestudio.app.model.CameraFacing

/** Queries real Camera2 characteristics so resolution pickers only ever offer sizes the device actually supports. */
object CameraCapabilities {

    fun supportedResolutions(context: Context, facing: CameraFacing): List<Size> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
        return try {
            val wanted = if (facing == CameraFacing.FRONT) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val id = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == wanted
            } ?: return emptyList()
            val chars = manager.getCameraCharacteristics(id)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) as? StreamConfigurationMap
                ?: return emptyList()
            map.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)?.toList()?.sortedByDescending { it.width * it.height }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hasCamera(context: Context, facing: CameraFacing): Boolean =
        supportedResolutions(context, facing).isNotEmpty()
}
