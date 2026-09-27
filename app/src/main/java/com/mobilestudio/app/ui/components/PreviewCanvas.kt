package com.mobilestudio.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.mobilestudio.app.model.SourceModel
import com.mobilestudio.app.model.Transform
import com.mobilestudio.app.ui.theme.StudioAccent

/**
 * The interactive scene editor (Section 5): tap to select a source, drag to move it, drag its
 * corner handle to resize. All gestures work in the canvas's own normalized (0f..1f) coordinate
 * space so they translate directly onto the compositor's output resolution.
 */
@Composable
fun PreviewCanvas(
    frame: Bitmap?,
    sources: List<SourceModel>,
    selectedSourceId: String?,
    onSelect: (String?) -> Unit,
    onTransformChange: (String, Transform) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSizePx by remember { mutableStateOf(Offset(1f, 1f)) }
    val handleSizeFraction = 0.035f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .pointerInput(sources.map { it.id to it.locked }) {
                    detectTapGestures { tapOffset ->
                        val nx = tapOffset.x / canvasSizePx.x
                        val ny = tapOffset.y / canvasSizePx.y
                        val hit = sources.filter { it.visible }.sortedByDescending { it.zOrder }
                            .firstOrNull { s ->
                                val t = s.transform
                                nx in t.xNorm..(t.xNorm + t.widthNorm) && ny in t.yNorm..(t.yNorm + t.heightNorm)
                            }
                        onSelect(hit?.id)
                    }
                }
                .pointerInput(selectedSourceId, sources.map { it.id to it.locked }) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val selected = sources.firstOrNull { it.id == selectedSourceId } ?: return@detectDragGestures
                            if (selected.locked) return@detectDragGestures
                            val t = selected.transform
                            val startPos = change.position - dragAmount
                            val nx = startPos.x / canvasSizePx.x
                            val ny = startPos.y / canvasSizePx.y
                            val nearRightEdge = nx > t.xNorm + t.widthNorm - handleSizeFraction * 2
                            val nearBottomEdge = ny > t.yNorm + t.heightNorm - handleSizeFraction * 2
                            val isResizeHandle = nearRightEdge && nearBottomEdge

                            val dxNorm = dragAmount.x / canvasSizePx.x
                            val dyNorm = dragAmount.y / canvasSizePx.y

                            val updated = if (isResizeHandle) {
                                t.copy(
                                    widthNorm = (t.widthNorm + dxNorm).coerceIn(0.05f, 1f - t.xNorm),
                                    heightNorm = (t.heightNorm + dyNorm).coerceIn(0.05f, 1f - t.yNorm)
                                )
                            } else {
                                t.copy(
                                    xNorm = (t.xNorm + dxNorm).coerceIn(0f, 1f - t.widthNorm),
                                    yNorm = (t.yNorm + dyNorm).coerceIn(0f, 1f - t.heightNorm)
                                )
                            }
                            onTransformChange(selected.id, updated)
                        }
                    )
                }
        ) {
            canvasSizePx = Offset(size.width, size.height)
            frame?.let { bmp ->
                drawImage(bmp.asImageBitmap(), dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()))
            }
            val selected = sources.firstOrNull { it.id == selectedSourceId }
            if (selected != null) {
                val t = selected.transform
                val left = t.xNorm * size.width
                val top = t.yNorm * size.height
                val w = t.widthNorm * size.width
                val h = t.heightNorm * size.height
                drawRect(
                    color = StudioAccent,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    style = Stroke(width = 3f)
                )
                // resize handle, bottom-right corner
                drawCircle(
                    color = StudioAccent,
                    radius = 14f,
                    center = Offset(left + w, top + h)
                )
            }
        }
    }
}
