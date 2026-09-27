package com.mobilestudio.app.virtualcamera

import android.graphics.Bitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * ===========================================================================================
 *  WHY THIS ISN'T A SYSTEM-WIDE ANDROID "VIRTUAL WEBCAM" — AND WHAT WE BUILT INSTEAD
 * ===========================================================================================
 * Android has no public API that lets a normal (non-root, non-system, non-OEM-privileged) app
 * register itself as a new Camera2/CameraX device that *other* apps can pick from a camera
 * chooser — unlike Windows (DirectShow/Media Foundation virtual camera drivers) or Linux
 * (v4l2loopback), there is no camera HAL registration surface exposed to app-signature-level
 * processes. Camera providers are registered by the Camera HAL, which is a vendor/OEM component
 * outside any app's reach without root and a custom HAL module (unsafe, unsupported, and
 * explicitly out of scope for this app).
 *
 * So instead of faking a "Start Virtual Camera" button that secretly does nothing, this class
 * implements the closest thing that *is* legitimately possible on stock Android:
 *
 *   An MJPEG-over-HTTP server, running on the device's local network, that streams the
 *   compositor's live composed output. Any software that can consume an IP/network camera —
 *   OBS Studio's own "Browser Source" or "Video Capture Device (network)" plugins, VLC, other
 *   apps with an MJPEG/IP-camera input, or a browser tab on another device on the same Wi-Fi —
 *   can subscribe to it exactly like a network webcam. No PC, cable, or root required; this is a
 *   real, working stream, not a simulation.
 *
 * This is architected as a pluggable "VirtualCameraOutput" so a future OEM or platform API that
 * *does* support true system camera registration (e.g. a manufacturer's proprietary SDK) can be
 * swapped in later without touching the compositor or UI layer.
 */
class VirtualCameraManager {

    enum class Status { STOPPED, STARTING, LIVE, ERROR }

    private val _status = MutableStateFlow(Status.STOPPED)
    val status: StateFlow<Status> = _status

    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl: StateFlow<String?> = _streamUrl

    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients

    var lastError: String? = null
        private set

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val clientSockets = CopyOnWriteArrayList<Socket>()
    private val latestJpeg = AtomicReference<ByteArray?>(null)
    private var jpegQuality = 80
    private var port = 8080

    fun start(scope: CoroutineScope, port: Int = 8080) {
        this.port = port
        if (_status.value == Status.LIVE || _status.value == Status.STARTING) return
        _status.value = Status.STARTING
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val socket = ServerSocket(port)
                serverSocket = socket
                _streamUrl.value = "http://${localIpAddress() ?: "0.0.0.0"}:$port/stream"
                _status.value = Status.LIVE
                while (isActive) {
                    val client = try { socket.accept() } catch (_: Exception) { break }
                    launch(Dispatchers.IO) { serveClient(client) }
                }
            } catch (e: Exception) {
                lastError = e.message
                _status.value = Status.ERROR
            }
        }
    }

    /** Feed the compositor's latest frame in; called on every composed frame while live. */
    fun submitFrame(bitmap: Bitmap) {
        if (_status.value != Status.LIVE) return
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
        latestJpeg.set(out.toByteArray())
    }

    private fun serveClient(socket: Socket) {
        clientSockets.add(socket)
        _connectedClients.value = clientSockets.size
        try {
            socket.getInputStream().bufferedReader().readLine() // consume the request line; single-endpoint server
            val out = BufferedOutputStream(socket.getOutputStream())
            val boundary = "mobilestudioframe"
            out.write("HTTP/1.0 200 OK\r\n".toByteArray())
            out.write("Content-Type: multipart/x-mixed-replace; boundary=$boundary\r\n".toByteArray())
            out.write("Cache-Control: no-cache\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()

            while (_status.value == Status.LIVE && !socket.isClosed) {
                val jpeg = latestJpeg.get()
                if (jpeg != null) {
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Type: image/jpeg\r\n".toByteArray())
                    out.write("Content-Length: ${jpeg.size}\r\n\r\n".toByteArray())
                    out.write(jpeg)
                    out.write("\r\n".toByteArray())
                    out.flush()
                }
                Thread.sleep(66) // ~15fps stream — plenty for a network preview/consume source
            }
        } catch (_: Exception) {
            // client disconnected — normal
        } finally {
            clientSockets.remove(socket)
            _connectedClients.value = clientSockets.size
            try { socket.close() } catch (_: Exception) {}
        }
    }

    fun stop() {
        serverJob?.cancel()
        clientSockets.forEach { try { it.close() } catch (_: Exception) {} }
        clientSockets.clear()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        _status.value = Status.STOPPED
        _streamUrl.value = null
        _connectedClients.value = 0
    }

    private fun localIpAddress(): String? = try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filter { !it.isLoopbackAddress && it is InetAddress && it.hostAddress?.contains(':') == false }
            .map { it.hostAddress }
            .firstOrNull()
    } catch (_: Exception) { null }
}
