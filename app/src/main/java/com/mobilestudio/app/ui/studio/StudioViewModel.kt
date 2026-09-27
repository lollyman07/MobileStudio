package com.mobilestudio.app.ui.studio

import android.app.Application
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.mobilestudio.app.audio.AudioLevelMonitor
import com.mobilestudio.app.camera.CameraFrameProvider
import com.mobilestudio.app.data.SettingsRepository
import com.mobilestudio.app.data.StudioRepository
import com.mobilestudio.app.model.*
import com.mobilestudio.app.recording.RecorderManager
import com.mobilestudio.app.recording.StudioForegroundService
import com.mobilestudio.app.rendering.ImageFrameProvider
import com.mobilestudio.app.rendering.VideoFrameProvider
import com.mobilestudio.app.util.MediaStoreHelper
import com.mobilestudio.app.virtualcamera.VirtualCameraManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StudioUiState(
    val scenes: List<SceneModel> = emptyList(),
    val currentSceneId: String? = null,
    val selectedSourceId: String? = null,
    val previewFrame: Bitmap? = null,
    val fps: Float = 0f,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recordingSeconds: Int = 0,
    val isCapturingScreen: Boolean = false,
    val isStreaming: Boolean = false,
    val streamUrl: String? = null,
    val streamClients: Int = 0,
    val micLevelDb: Float = -60f,
    val micMuted: Boolean = false,
    val videoSettings: VideoOutputSettings = VideoOutputSettings(),
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val lastRecordingSavedTo: String? = null,
    val errorMessage: String? = null
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = StudioRepository(application)
    private val settingsRepo = SettingsRepository(application)
    private val audioMonitor = AudioLevelMonitor()

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState

    private var service: StudioForegroundService? = null
    private var bound = false
    private val cameraProviders = mutableMapOf<String, CameraFrameProvider>()

    private var recordingTimerJob: kotlinx.coroutines.Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as StudioForegroundService.LocalBinder
            service = b.getService()
            bound = true
            observeCompositor()
            currentScene()?.let { service?.compositor?.setScene(it) }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    init {
        val app = getApplication<Application>()
        val intent = Intent(app, StudioForegroundService::class.java)
        app.startService(intent)
        app.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        viewModelScope.launch {
            repo.loadFullProject().let { scenes ->
                val withDefault = if (scenes.isEmpty()) listOf(defaultScene()) else scenes
                _uiState.value = _uiState.value.copy(scenes = withDefault, currentSceneId = withDefault.first().id)
                if (scenes.isEmpty()) repo.saveScene(withDefault.first())
                service?.compositor?.setScene(withDefault.first())
            }
        }
        viewModelScope.launch {
            settingsRepo.videoSettings.collect { vs -> _uiState.value = _uiState.value.copy(videoSettings = vs) }
        }
        viewModelScope.launch {
            settingsRepo.themeMode.collect { tm -> _uiState.value = _uiState.value.copy(themeMode = tm) }
        }
        audioMonitor.start(viewModelScope)
        viewModelScope.launch {
            audioMonitor.levelDb.collect { db -> _uiState.value = _uiState.value.copy(micLevelDb = db) }
        }
    }

    private fun defaultScene() = SceneModel(name = "Main", order = 0)

    private fun observeCompositor() {
        val svc = service ?: return
        viewModelScope.launch {
            svc.compositor.composedFrame.collect { frame ->
                _uiState.value = _uiState.value.copy(previewFrame = frame)
            }
        }
        viewModelScope.launch {
            svc.compositor.actualFps.collect { fps -> _uiState.value = _uiState.value.copy(fps = fps) }
        }
        viewModelScope.launch {
            svc.virtualCameraManager.status.collect {
                _uiState.value = _uiState.value.copy(isStreaming = it == VirtualCameraManager.Status.LIVE)
            }
        }
        viewModelScope.launch {
            svc.virtualCameraManager.streamUrl.collect { url -> _uiState.value = _uiState.value.copy(streamUrl = url) }
        }
        viewModelScope.launch {
            svc.virtualCameraManager.connectedClients.collect { c -> _uiState.value = _uiState.value.copy(streamClients = c) }
        }
    }

    fun currentScene(): SceneModel? = _uiState.value.scenes.firstOrNull { it.id == _uiState.value.currentSceneId }

    // ---------- Scenes ----------

    fun selectScene(sceneId: String) {
        _uiState.value = _uiState.value.copy(currentSceneId = sceneId, selectedSourceId = null)
        currentScene()?.let { service?.compositor?.setScene(it) }
    }

    fun createScene(name: String) {
        val scene = SceneModel(name = name, order = _uiState.value.scenes.size)
        val updated = _uiState.value.scenes + scene
        _uiState.value = _uiState.value.copy(scenes = updated, currentSceneId = scene.id)
        viewModelScope.launch { repo.saveScene(scene) }
        service?.compositor?.setScene(scene)
    }

    fun renameScene(sceneId: String, newName: String) {
        updateScenes { scenes ->
            scenes.map { if (it.id == sceneId) it.copy(name = newName) else it }
        }
        _uiState.value.scenes.firstOrNull { it.id == sceneId }?.let { viewModelScope.launch { repo.saveScene(it) } }
    }

    fun duplicateScene(sceneId: String) {
        val original = _uiState.value.scenes.firstOrNull { it.id == sceneId } ?: return
        val newSceneId = java.util.UUID.randomUUID().toString()
        val copy = original.copy(
            id = newSceneId,
            name = original.name + " Copy",
            order = _uiState.value.scenes.size,
            sources = original.sources
                .map { it.copy(id = java.util.UUID.randomUUID().toString(), sceneId = newSceneId) }
                .toMutableList()
        )
        _uiState.value = _uiState.value.copy(scenes = _uiState.value.scenes + copy)
        viewModelScope.launch {
            repo.saveScene(copy)
            repo.saveSources(copy.sources)
        }
    }

    fun deleteScene(sceneId: String) {
        val remaining = _uiState.value.scenes.filterNot { it.id == sceneId }
        val newCurrent = if (_uiState.value.currentSceneId == sceneId) remaining.firstOrNull()?.id else _uiState.value.currentSceneId
        _uiState.value = _uiState.value.copy(scenes = remaining, currentSceneId = newCurrent)
        viewModelScope.launch { repo.deleteScene(sceneId) }
        remaining.firstOrNull { it.id == newCurrent }?.let { service?.compositor?.setScene(it) }
    }

    fun reorderScenes(newOrder: List<String>) {
        updateScenes { scenes ->
            newOrder.mapIndexedNotNull { idx, id -> scenes.firstOrNull { it.id == id }?.copy(order = idx) }
        }
        viewModelScope.launch { repo.saveScenes(_uiState.value.scenes) }
    }

    private fun updateScenes(transform: (List<SceneModel>) -> List<SceneModel>) {
        _uiState.value = _uiState.value.copy(scenes = transform(_uiState.value.scenes))
    }

    // ---------- Sources ----------

    fun addSource(type: SourceType, lifecycleOwner: LifecycleOwner) {
        val scene = currentScene() ?: return
        val source = SourceModel(
            sceneId = scene.id,
            type = type,
            name = defaultNameFor(type, scene.sources.size),
            zOrder = scene.sources.size
        )
        attachFrameSourceIfNeeded(source, lifecycleOwner)
        mutateCurrentScene { it.sources.add(source) }
        viewModelScope.launch { repo.saveSource(source) }
        _uiState.value = _uiState.value.copy(selectedSourceId = source.id)
    }

    private fun defaultNameFor(type: SourceType, index: Int) = when (type) {
        SourceType.CAMERA -> "Camera ${index + 1}"
        SourceType.SCREEN_CAPTURE -> "Screen Capture"
        SourceType.IMAGE -> "Image ${index + 1}"
        SourceType.TEXT -> "Text ${index + 1}"
        SourceType.COLOR -> "Color ${index + 1}"
        SourceType.VIDEO -> "Video ${index + 1}"
        SourceType.WEB -> "Web ${index + 1}"
    }

    private fun attachFrameSourceIfNeeded(source: SourceModel, lifecycleOwner: LifecycleOwner) {
        val svc = service ?: return
        when (source.type) {
            SourceType.CAMERA -> {
                val provider = CameraFrameProvider(getApplication(), lifecycleOwner, source.cameraFacing)
                cameraProviders[source.id] = provider
                svc.compositor.registerFrameSource(source.id, provider)
            }
            else -> {}
        }
    }

    fun setImageForSource(sourceId: String, uri: Uri) {
        val svc = service ?: return
        updateSource(sourceId) { it.copy(imageUri = uri.toString()) }
        svc.compositor.registerFrameSource(sourceId, ImageFrameProvider(getApplication(), uri))
    }

    fun setVideoForSource(sourceId: String, uri: Uri) {
        val svc = service ?: return
        val source = findSource(sourceId) ?: return
        updateSource(sourceId) { it.copy(videoUri = uri.toString()) }
        svc.compositor.registerFrameSource(sourceId, VideoFrameProvider(getApplication(), uri, source.videoLoop))
    }

    fun setWebUrlForSource(sourceId: String, url: String) {
        updateSource(sourceId) { it.copy(webUrl = url) }
    }

    fun switchCameraFacing(sourceId: String, facing: CameraFacing) {
        updateSource(sourceId) { it.copy(cameraFacing = facing) }
        cameraProviders[sourceId]?.switchFacing(facing)
    }

    fun toggleTorch(sourceId: String, enabled: Boolean) {
        cameraProviders[sourceId]?.setTorch(enabled)
    }

    fun selectSource(sourceId: String?) { _uiState.value = _uiState.value.copy(selectedSourceId = sourceId) }

    fun updateTransform(sourceId: String, transform: Transform) {
        updateSource(sourceId) { it.copy(transform = transform) }
    }

    fun toggleVisibility(sourceId: String) {
        updateSource(sourceId) { it.copy(visible = !it.visible) }
    }

    fun toggleLock(sourceId: String) {
        updateSource(sourceId) { it.copy(locked = !it.locked) }
    }

    fun renameSource(sourceId: String, name: String) {
        updateSource(sourceId) { it.copy(name = name) }
    }

    fun updateTextProperties(
        sourceId: String, content: String? = null, sizeSp: Float? = null, bold: Boolean? = null,
        colorArgb: Long? = null, bgArgb: Long? = null, align: String? = null
    ) {
        updateSource(sourceId) {
            it.copy(
                textContent = content ?: it.textContent,
                textSizeSp = sizeSp ?: it.textSizeSp,
                textBold = bold ?: it.textBold,
                textColorArgb = colorArgb ?: it.textColorArgb,
                textBackgroundArgb = bgArgb ?: it.textBackgroundArgb,
                textAlign = align ?: it.textAlign
            )
        }
    }

    fun updateColor(sourceId: String, argb: Long) {
        updateSource(sourceId) { it.copy(colorArgb = argb) }
    }

    fun moveLayer(sourceId: String, up: Boolean) {
        val scene = currentScene() ?: return
        val sorted = scene.sources.sortedBy { it.zOrder }.toMutableList()
        val idx = sorted.indexOfFirst { it.id == sourceId }
        val swapWith = if (up) idx + 1 else idx - 1
        if (idx < 0 || swapWith < 0 || swapWith >= sorted.size) return
        val a = sorted[idx]; val b = sorted[swapWith]
        val az = a.zOrder; a.zOrder = b.zOrder; b.zOrder = az
        mutateCurrentScene { /* z-orders mutated in place above */ }
        viewModelScope.launch { repo.saveSources(listOf(a, b)) }
    }

    fun deleteSource(sourceId: String) {
        val svc = service
        svc?.compositor?.unregisterFrameSource(sourceId)
        cameraProviders.remove(sourceId)
        mutateCurrentScene { it.sources.removeAll { s -> s.id == sourceId } }
        viewModelScope.launch { repo.deleteSource(sourceId) }
        if (_uiState.value.selectedSourceId == sourceId) _uiState.value = _uiState.value.copy(selectedSourceId = null)
    }

    private fun findSource(sourceId: String): SourceModel? =
        currentScene()?.sources?.firstOrNull { it.id == sourceId }

    private fun updateSource(sourceId: String, transform: (SourceModel) -> SourceModel) {
        var updated: SourceModel? = null
        mutateCurrentScene { sources ->
            val idx = sources.indexOfFirst { it.id == sourceId }
            if (idx >= 0) {
                updated = transform(sources[idx])
                sources[idx] = updated!!
            }
        }
        updated?.let { viewModelScope.launch { repo.saveSource(it) } }
    }

    private fun mutateCurrentScene(block: (MutableList<SourceModel>) -> Unit) {
        val sceneId = _uiState.value.currentSceneId ?: return
        val scenes = _uiState.value.scenes.map { scene ->
            if (scene.id == sceneId) {
                block(scene.sources)
                scene
            } else scene
        }
        _uiState.value = _uiState.value.copy(scenes = scenes)
        scenes.firstOrNull { it.id == sceneId }?.let { service?.compositor?.setScene(it) }
    }

    // ---------- Recording ----------

    fun startRecording() {
        val svc = service ?: return
        val vs = _uiState.value.videoSettings
        val outDir = MediaStoreHelper.appOutputDir(getApplication())
        val ok = svc.startRecording(outDir, vs.customWidth, vs.customHeight, vs.fps, vs.bitrateKbps, vs.audioEnabled)
        if (ok) {
            _uiState.value = _uiState.value.copy(isRecording = true, isPaused = false, recordingSeconds = 0)
            recordingTimerJob = viewModelScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    if (!_uiState.value.isPaused) {
                        _uiState.value = _uiState.value.copy(recordingSeconds = _uiState.value.recordingSeconds + 1)
                    }
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(errorMessage = svc.recorder.lastError ?: "Failed to start recording")
        }
    }

    fun pauseRecording() {
        service?.pauseRecording()
        _uiState.value = _uiState.value.copy(isPaused = true)
    }

    fun resumeRecording() {
        service?.resumeRecording()
        _uiState.value = _uiState.value.copy(isPaused = false)
    }

    fun stopRecording() {
        recordingTimerJob?.cancel()
        val file = service?.stopRecording()
        _uiState.value = _uiState.value.copy(isRecording = false, isPaused = false)
        if (file != null) {
            viewModelScope.launch {
                val uri = MediaStoreHelper.publishRecording(getApplication(), file)
                val durationMs = _uiState.value.recordingSeconds * 1000L
                repo.saveRecording(
                    com.mobilestudio.app.data.db.RecordingEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        filePath = file.absolutePath,
                        fileName = file.name,
                        durationMs = durationMs,
                        width = _uiState.value.videoSettings.customWidth,
                        height = _uiState.value.videoSettings.customHeight,
                        fps = _uiState.value.videoSettings.fps,
                        fileSizeBytes = file.length(),
                        createdAtMs = System.currentTimeMillis()
                    )
                )
                _uiState.value = _uiState.value.copy(lastRecordingSavedTo = uri?.toString() ?: file.absolutePath)
            }
        }
    }

    // ---------- Screen capture ----------

    fun beginScreenCapture(resultCode: Int, data: Intent, width: Int, height: Int, density: Int) {
        val scene = currentScene() ?: return
        val source = SourceModel(sceneId = scene.id, type = SourceType.SCREEN_CAPTURE, name = "Screen Capture", zOrder = scene.sources.size)
        mutateCurrentScene { it.add(source) }
        viewModelScope.launch { repo.saveSource(source) }
        service?.beginScreenCapture(resultCode, data, width, height, density, source.id)
        _uiState.value = _uiState.value.copy(isCapturingScreen = true)
    }

    fun stopScreenCapture(sourceId: String) {
        service?.stopScreenCapture(sourceId)
        _uiState.value = _uiState.value.copy(isCapturingScreen = false)
    }

    // ---------- Virtual camera ----------

    fun startVirtualCamera() {
        val vs = _uiState.value.videoSettings
        service?.compositor?.setOutputSize(vs.customWidth, vs.customHeight)
        service?.startVirtualCamera()
    }

    fun stopVirtualCamera() { service?.stopVirtualCamera() }

    // ---------- Audio ----------

    fun setMicMuted(muted: Boolean) {
        audioMonitor.setMuted(muted)
        _uiState.value = _uiState.value.copy(micMuted = muted)
    }

    // ---------- Settings ----------

    fun updateVideoSettings(update: (VideoOutputSettings) -> VideoOutputSettings) {
        viewModelScope.launch {
            val newSettings = update(_uiState.value.videoSettings)
            settingsRepo.setResolution(newSettings.customWidth, newSettings.customHeight)
            settingsRepo.setFps(newSettings.fps)
            settingsRepo.setBitrate(newSettings.bitrateKbps)
            settingsRepo.setCodec(newSettings.codec)
            settingsRepo.setAudioEnabled(newSettings.audioEnabled)
            settingsRepo.setQualityPreset(newSettings.preset)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }

    override fun onCleared() {
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
        audioMonitor.stop()
        super.onCleared()
    }
}
