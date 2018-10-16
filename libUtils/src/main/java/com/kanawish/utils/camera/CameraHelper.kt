package com.kanawish.utils.camera

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.CameraDevice.StateCallback
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import com.kanawish.kotlin.safeLet
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @startuml
 * class CameraHelper
 * @enduml
 */
@Singleton class CameraHelper @Inject constructor(
        private val app: Application,
        private val manager: CameraManager
) {
    private val camId: String?

    private var cameraDevice: CameraDevice? = null

    private var captureSession: CameraCaptureSession? = null

    private var imageReader: ImageReader? = null

    // Create the thread/looper/handler for image readers.
    private val cameraHandler: Handler = HandlerThread("CameraBackground")
            .apply { start() }
            .looper.let { Handler(it) }

    init {
        // Get the index of the first available camera.
        val camIds: Array<String> = try {
            manager.cameraIdList
        } catch (e: CameraAccessException) {
            Timber.e(e, "Camera access exception getting cameraIdList")
            emptyArray()
        }
        Timber.d("Found ${camIds.size} camera(s).")
        camId = camIds.getOrNull(0)
        Timber.d("Default camera $camId)}")
    }

    @SuppressLint("MissingPermission") // Permission check happens in user code.
    fun openCamera(receiver: (ByteArray) -> Unit) {
        // NOTE: I'm doing something wrong/naive with width-height, it's not working correctly.
        // Init image processor
        imageReader = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.JPEG, 2)
        imageReader?.setOnImageAvailableListener(buildImageListener(receiver), cameraHandler)

        manager.openCamera(
                camId,
                object : StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        Timber.d("Opened camera.")
                        cameraDevice = camera
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        Timber.d("Camera disconnected, closing.")
                        camera.close()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Timber.d("Camera device error[$error], closing.")
                        camera.close()
                    }

                    override fun onClosed(camera: CameraDevice) {
                        Timber.d("Camera closed, releasing.")
                        cameraDevice = null
                    }
                },
                cameraHandler
        )
    }

    /**
     *
     */
    fun buildImageListener(receiver: (ByteArray) -> Unit) = ImageReader.OnImageAvailableListener { reader ->
        // Bit of Kotlin fun here:
        reader.acquireLatestImage()
                // `let` latest image be converted to a byte array...
                .let { image ->
                    // `let` desired image byte buffer be converted to a byte array...
                    image.planes[0].buffer.let { byteBuffer ->
                        // `let` byte buffer be converted to a byte array...
                        ByteArray(byteBuffer.remaining()).also { byteArray ->
                            // once `get()` call fills in byte array, `also` close image as a side-effect.
                            byteBuffer.get(byteArray).also { _ -> image.close() }
                        }
                    }
                }
                // and finally `let` image byte array be consumed by onPictureTaken()
                .let { imageByteArray -> receiver(imageByteArray) }
    }


    fun takePicture() {
//        Timber.d("takePicture()")
        if (cameraDevice == null) {
            Timber.e("Cannot capture image. Camera not initialized.")
            return
        }

        try {
            cameraDevice?.createCaptureSession(
                    imageReader?.surface?.let { listOf(it) } ?: emptyList(),
                    sessionCallback,
                    null)
        } catch (e: CameraAccessException) {
            Timber.e(e, "Access exception while preparing pic.")
        }
    }

    private val sessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            cameraDevice ?: return

            captureSession = session
            triggerImageCapture()
        }

        override fun onConfigureFailed(session: CameraCaptureSession?) {
            Timber.e("Failed to configure camera")
        }
    }

    private fun triggerImageCapture() {
        try {
            safeLet(cameraDevice, imageReader) { camera, reader ->
                val result = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).let { builder ->
                    builder.addTarget(reader.surface)
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
//                    Timber.d("Session initialized.")
                    captureSession?.capture(builder.build(), captureCallback, null)
                }
//                Timber.d("capture() returned $result")
            }
        } catch (e: Exception) {
            Timber.e(e, "Camera capture exception.")
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult
        ) {
//            Timber.d("Partial result")
        }

        override fun onCaptureCompleted(
                session: CameraCaptureSession?,
                request: CaptureRequest,
                result: TotalCaptureResult
        ) {
//            Timber.d("CaptureSession closed")
            session?.let {
                it.close()
                captureSession = null
//                Timber.d("CaptureSession closed")
            }
        }
    }

    fun closeCamera() {
        cameraDevice?.close()
    }


    companion object {
        private const val WIDTH = 640
        private const val HEIGHT = 480
        private const val MAX_IMAGES = 2
    }
}

/**
 * Helpful debugging method:  Dump all supported camera formats to log.  You don't need to run
 * this for normal operation, but it's very helpful when porting this code to different
 * hardware.
 */
fun Context.dumpFormatInfo() {
    val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    var camIds = arrayOf<String>()
    try {
        camIds = manager.cameraIdList
    } catch (e: CameraAccessException) {
        Timber.d("Cam access exception getting IDs")
    }

    if (camIds.size < 1) {
        Timber.d("No cameras found")
    }
    val id = camIds[0]
    Timber.d("Using camera id $id")
    try {
        val characteristics = manager.getCameraCharacteristics(id)
        val configs = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        for (format in configs!!.outputFormats) {
            Timber.d("Getting sizes for format: $format")
            for (s in configs.getOutputSizes(format)) {
                Timber.d("\t" + s.toString())
            }
        }
        val effects = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)
        for (effect in effects!!) {
            Timber.d("Effect available: $effect")
        }
    } catch (e: CameraAccessException) {
        Timber.d("Cam access exception getting characteristics.")
    }

}
