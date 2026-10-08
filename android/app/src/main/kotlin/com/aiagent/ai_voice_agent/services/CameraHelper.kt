package com.aiagent.ai_voice_agent.services

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * CameraHelper — takes photos using Camera2 API without preview.
 * Supports front/back camera selection and timer countdown.
 * Saves to public gallery (/sdcard/Pictures/) by default.
 */
class CameraHelper(private val context: Context) {

    companion object {
        private const val TAG = "CameraHelper"
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var customSavePath: String? = null

    /**
     * Take a photo.
     * @param useFrontCamera true for selfie (front), false for back camera
     * @param timerSeconds countdown before capture (0 = immediate)
     * @param savePath custom save directory (null = /sdcard/Pictures/ — public gallery)
     * @param onCountdown callback for countdown ticks (5, 4, 3, 2, 1, "Снимаю!")
     * @return File? — path to saved photo, or null on failure
     */
    fun takePhoto(
        useFrontCamera: Boolean = true,
        timerSeconds: Int = 0,
        savePath: String? = null,
        onCountdown: ((String) -> Unit)? = null
    ): File? {
        customSavePath = savePath
        var resultFile: File? = null
        val latch = CountDownLatch(1)

        startBackgroundThread()

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = findCamera(cameraManager, useFrontCamera) ?: run {
                Log.e(TAG, "Camera not found: front=$useFrontCamera")
                latch.countDown()
                return null
            }

            // Create output file
            val outputFile = createPhotoFile()

            // Set up ImageReader
            val reader = setupImageReader(outputFile) {
                resultFile = outputFile
                latch.countDown()
            }
            imageReader = reader

            // Open camera
            val openLatch = CountDownLatch(1)
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    openLatch.countDown()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    openLatch.countDown()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    Log.e(TAG, "Camera error: $error")
                    openLatch.countDown()
                }
            }, backgroundHandler)

            if (!openLatch.await(5, TimeUnit.SECONDS)) {
                Log.e(TAG, "Camera open timeout")
                cleanup()
                latch.countDown()
                return null
            }

            val device = cameraDevice ?: run {
                cleanup()
                latch.countDown()
                return null
            }

            // Timer countdown
            if (timerSeconds > 0) {
                for (i in timerSeconds downTo 1) {
                    onCountdown?.invoke("$i")
                    Thread.sleep(1000)
                }
                onCountdown?.invoke("Снимаю!")
                Thread.sleep(300) // Brief pause after countdown
            }

            // Start capture session
            startCapture(device, reader)

            // Wait for capture to complete
            latch.await(10, TimeUnit.SECONDS)

        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "takePhoto error: ${e.message}", e)
        } finally {
            cleanup()
        }

        return resultFile
    }

    private fun findCamera(cameraManager: CameraManager, useFront: Boolean): String? {
        val facing = if (useFront) CameraCharacteristics.LENS_FACING_FRONT
                     else CameraCharacteristics.LENS_FACING_BACK

        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == facing) {
                return id
            }
        }
        return null
    }

    private fun setupImageReader(outputFile: File, onImageSaved: () -> Unit): ImageReader {
        val maxSize = 1920
        val reader = ImageReader.newInstance(maxSize, maxSize * 3 / 4, ImageFormat.JPEG, 2)
        reader.setOnImageAvailableListener({ imageReader ->
            val image = imageReader.acquireLatestImage()
            if (image != null) {
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    FileOutputStream(outputFile).use { fos ->
                        fos.write(bytes)
                    }
                    Log.d(TAG, "Photo saved: ${outputFile.absolutePath}")
                    
                    // Scan media so photo appears in gallery immediately
                    scanMedia(outputFile)
                    
                    onImageSaved()
                } catch (e: Exception) {
                    Log.e(TAG, "Save error: ${e.message}")
                } finally {
                    image.close()
                }
            }
        }, backgroundHandler)
        return reader
    }

    private fun scanMedia(file: File) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("image/jpeg")
            ) { path, uri ->
                Log.d(TAG, "Media scanned: $uri")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Media scan failed: ${e.message}")
        }
    }

    private fun startCapture(camera: CameraDevice, reader: ImageReader) {
        val surface = reader.surface

        // Create dummy preview surface (required by Camera2)
        val previewTexture = SurfaceTexture(0)
        previewTexture.setDefaultBufferSize(640, 480)
        val previewSurface = Surface(previewTexture)

        val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(previewSurface)
            addTarget(surface)
            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            // Front camera mirror effect
            set(CaptureRequest.JPEG_ORIENTATION, 0)
        }.build()

        val sessionLatch = CountDownLatch(1)
        camera.createCaptureSession(
            listOf(previewSurface, surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        session.capture(captureRequest, object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult
                            ) {
                                Log.d(TAG, "Capture completed")
                                sessionLatch.countDown()
                            }

                            override fun onCaptureFailed(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                failure: CaptureFailure
                            ) {
                                Log.e(TAG, "Capture failed: ${failure.reason}")
                                sessionLatch.countDown()
                            }
                        }, backgroundHandler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Capture error: ${e.message}")
                        sessionLatch.countDown()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Session config failed")
                    sessionLatch.countDown()
                }
            },
            backgroundHandler
        )

        sessionLatch.await(8, TimeUnit.SECONDS)

        // Clean up preview surface
        previewSurface.release()
        previewTexture.release()
    }

    private fun createPhotoFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        
        // Determine save directory
        val dir = when {
            // Custom path provided
            customSavePath != null -> resolveCustomPath(customSavePath!!)
            // Default: public Pictures gallery (обычная галерея)
            else -> File("/sdcard/Pictures")
        }
        
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "IMG_$timestamp.jpg")
    }

    private fun resolveCustomPath(path: String): File {
        return when {
            path.startsWith("/") -> File(path) // Absolute path
            path.contains("document", true) || path.contains("документ", true) -> File("/sdcard/Documents")
            path.contains("download", true) || path.contains("загрузк", true) -> File("/sdcard/Download")
            path.contains("picture", true) || path.contains("фото", true) || path.contains("галере", true) -> File("/sdcard/Pictures")
            else -> File("/sdcard/Pictures/$path") // Subfolder in Pictures
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also {
            it.start()
            backgroundHandler = Handler(it.looper)
        }
    }

    private fun cleanup() {
        try {
            captureSession?.close()
            cameraDevice?.close()
            imageReader?.close()
            backgroundThread?.quitSafely()
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup error: ${e.message}")
        }
        captureSession = null
        cameraDevice = null
        imageReader = null
        backgroundThread = null
        backgroundHandler = null
    }
}
