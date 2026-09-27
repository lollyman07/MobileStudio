package com.mobilestudio.app.rendering

import android.graphics.*
import com.mobilestudio.app.model.SceneModel
import com.mobilestudio.app.model.SourceModel
import com.mobilestudio.app.model.SourceType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

/**
 * Composites every visible source of the current scene into one Bitmap, honoring z-order,
 * position, size, rotation, opacity and crop. This single composed frame is the app's one
 * source of truth for: the live preview, the video recorder's input, and the virtual-camera
 * output stream — so "what you see is exactly what gets recorded / streamed".
 *
 * Runs its own tick loop at the configured output FPS so preview and recording/streaming always
 * see current content without doing redundant compositing work per consumer.
 */
class CompositorEngine {

    private val _composedFrame = MutableStateFlow<Bitmap?>(null)
    val composedFrame: StateFlow<Bitmap?> = _composedFrame

    private val _actualFps = MutableStateFlow(0f)
    val actualFps: StateFlow<Float> = _actualFps

    // sourceId -> live frame provider (camera / screen / video)
    private val frameSources = mutableMapOf<String, FrameSource>()

    private var outputWidth = 1280
    private var outputHeight = 720
    private var targetFps = 30

    private var scope: CoroutineScope? = null
    private var currentScene: SceneModel? = null

    /** Each frame we publish to listeners (recorder, virtual camera) beyond the StateFlow. */
    private val frameListeners = mutableListOf<(Bitmap) -> Unit>()

    fun setOutputSize(width: Int, height: Int) {
        outputWidth = width
        outputHeight = height
    }

    fun setTargetFps(fps: Int) { targetFps = fps.coerceIn(1, 60) }

    fun registerFrameSource(sourceId: String, source: FrameSource) {
        frameSources[sourceId]?.release()
        frameSources[sourceId] = source
        source.start()
    }

    fun unregisterFrameSource(sourceId: String) {
        frameSources.remove(sourceId)?.let { it.stop(); it.release() }
    }

    fun addFrameListener(listener: (Bitmap) -> Unit) { frameListeners.add(listener) }
    fun removeFrameListener(listener: (Bitmap) -> Unit) { frameListeners.remove(listener) }

    fun setScene(scene: SceneModel) {
        currentScene = scene
    }

    fun start() {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default).also { sc ->
            sc.launch {
                var frames = 0
                var windowStart = System.currentTimeMillis()
                while (isActive) {
                    val frameStart = System.currentTimeMillis()
                    val scene = currentScene
                    if (scene != null) {
                        val bmp = renderFrame(scene)
                        _composedFrame.value = bmp
                        frameListeners.forEach { it(bmp) }
                    }
                    frames++
                    val now = System.currentTimeMillis()
                    if (now - windowStart >= 1000) {
                        _actualFps.value = frames * 1000f / (now - windowStart)
                        frames = 0
                        windowStart = now
                    }
                    val elapsed = System.currentTimeMillis() - frameStart
                    val targetFrameMs = 1000L / targetFps
                    delay(max(0L, targetFrameMs - elapsed))
                }
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    fun releaseAll() {
        stop()
        frameSources.values.forEach { it.stop(); it.release() }
        frameSources.clear()
        frameListeners.clear()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun renderFrame(scene: SceneModel): Bitmap {
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        scene.sources
            .filter { it.visible }
            .sortedBy { it.zOrder }
            .forEach { source -> drawSource(canvas, source) }

        return output
    }

    private fun drawSource(canvas: Canvas, source: SourceModel) {
        val t = source.transform
        val left = t.xNorm * outputWidth
        val top = t.yNorm * outputHeight
        val w = t.widthNorm * outputWidth
        val h = t.heightNorm * outputHeight
        paint.alpha = (t.opacity.coerceIn(0f, 1f) * 255).toInt()

        canvas.save()
        // rotate around the source's own center
        canvas.rotate(t.rotationDeg, left + w / 2f, top + h / 2f)

        when (source.type) {
            SourceType.COLOR -> {
                paint.color = source.colorArgb.toInt()
                canvas.drawRect(left, top, left + w, top + h, paint)
                paint.color = Color.WHITE
            }
            SourceType.TEXT -> {
                if (source.textBackgroundArgb.toInt() != 0) {
                    val bgPaint = Paint(paint).apply { color = source.textBackgroundArgb.toInt() }
                    canvas.drawRect(left, top, left + w, top + h, bgPaint)
                }
                textPaint.color = source.textColorArgb.toInt()
                textPaint.textSize = source.textSizeSp * (outputWidth / 1280f) * 1.6f
                textPaint.isFakeBoldText = source.textBold
                textPaint.alpha = paint.alpha
                textPaint.textAlign = when (source.textAlign) {
                    "center" -> Paint.Align.CENTER
                    "right" -> Paint.Align.RIGHT
                    else -> Paint.Align.LEFT
                }
                val tx = when (source.textAlign) {
                    "center" -> left + w / 2f
                    "right" -> left + w
                    else -> left
                }
                canvas.drawText(source.textContent, tx, top + textPaint.textSize, textPaint)
            }
            SourceType.CAMERA, SourceType.SCREEN_CAPTURE, SourceType.IMAGE, SourceType.VIDEO -> {
                val frame = frameSources[source.id]?.latestFrame()
                if (frame != null) {
                    val cropped = applyCrop(frame, t)
                    val dst = RectF(left, top, left + w, top + h)
                    canvas.drawBitmap(cropped, null, dst, paint)
                } else {
                    // Source not ready yet (e.g. camera still opening) — show a placeholder
                    // rectangle instead of nothing, so layout stays predictable.
                    val ph = Paint(paint).apply { color = Color.DKGRAY }
                    canvas.drawRect(left, top, left + w, top + h, ph)
                }
            }
            SourceType.WEB -> {
                // WebView content is composited live on-screen by the Compose overlay (see
                // PreviewCanvas), not into this offscreen bitmap — see VirtualCameraManager /
                // README for why standard Android can't capture an off-screen WebView without
                // the SYSTEM_ALERT_WINDOW permission. We draw a labelled placeholder here so
                // recordings/streams clearly show the limitation instead of silently omitting it.
                val ph = Paint(paint).apply { color = Color.rgb(30, 30, 40) }
                canvas.drawRect(left, top, left + w, top + h, ph)
                textPaint.color = Color.LTGRAY
                textPaint.textSize = 28f
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText("Web source (live-preview only)", left + 12, top + 36, textPaint)
            }
        }
        canvas.restore()
    }

    private fun applyCrop(src: Bitmap, t: com.mobilestudio.app.model.Transform): Bitmap {
        if (t.cropLeft == 0f && t.cropTop == 0f && t.cropRight == 0f && t.cropBottom == 0f) return src
        val cl = (t.cropLeft * src.width).toInt().coerceIn(0, src.width - 1)
        val ct = (t.cropTop * src.height).toInt().coerceIn(0, src.height - 1)
        val cr = (t.cropRight * src.width).toInt().coerceIn(0, src.width - cl - 1)
        val cb = (t.cropBottom * src.height).toInt().coerceIn(0, src.height - ct - 1)
        val w = max(1, src.width - cl - cr)
        val h = max(1, src.height - ct - cb)
        return Bitmap.createBitmap(src, cl, ct, w, h)
    }
}
