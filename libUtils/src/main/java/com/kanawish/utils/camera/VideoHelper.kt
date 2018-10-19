package com.kanawish.utils.camera

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import timber.log.Timber
import java.lang.Long.signum
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoHelper @Inject constructor(private val manager: CameraManager) {

    companion object {
        private const val WIDTH = 640
        private const val HEIGHT = 480
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private val imageReader = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.JPEG, 2)

    // Create the thread/looper/handler for image readers.
    private val cameraHandler = HandlerThread("VideoBackground")
            .apply { start() }
            .looper.let { Handler(it) }

    @SuppressLint("MissingPermission")
    fun startVideoCapture(videoFrameHandler: ImageReader.OnImageAvailableListener) {
        imageReader.setOnImageAvailableListener(videoFrameHandler, cameraHandler)

        val cameraId = manager.cameraIdList[0]
        Timber.d("Camera[0] $cameraId)}")
        // ...

        manager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?:
            throw RuntimeException("Cannot get available preview/video sizes")

        manager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        Timber.d("Opened camera.")
                        cameraDevice = camera
                        camera.startCaptureSession()
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        Timber.d("Camera device disconnected, closing.")
                        camera.close()
                        cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Timber.d("Camera device error[$error], closing.")
                        camera.close()
                        cameraDevice = null
                    }

                    override fun onClosed(camera: CameraDevice) {
                        Timber.d("Camera onClosed called.")
                    }
                },
                cameraHandler
        )
    }

    fun CameraDevice.startCaptureSession() {
        val builder = createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                //.addTarget(imageReader.surface)

        createCaptureSession(
                listOf(imageReader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        builder.addTarget(imageReader.surface)
                        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        session.setRepeatingRequest(builder.build(), null, null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Timber.e("Failed to configure capture session.")
                    }
                },
                cameraHandler
        )
    }

    fun closeCamera() {
        cameraDevice?.close()
    }

}

/**
 * Extension function that from a ByteArray consumer builds an instance of ImageReader.OnImageAvailableListener.
 *
 * You can use this to transform your function references.
 *
 */
fun ((ByteArray) -> Unit).toImageAvailableListener() = ImageReader.OnImageAvailableListener { reader ->
    // Bit of Kotlin fun here:
    reader.acquireLatestImage()
            // `let` latest image be converted to a byte array...
            ?.let { image ->
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
            ?.let { imageByteArray -> this(imageByteArray) }
}

/**
 * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
 * This method should not to be called until the camera preview size is determined in
 * startVideoCapture, or until the size of `textureView` is fixed.
 *
 * @param rotation  ...
 * @param viewSize The width and height of target `textureView`
 * @param previewSize ...
 */
private fun configureTransform(viewSize: Size, rotation: Int, previewSize: Size): Matrix {
    val viewWidth = viewSize.width
    val viewHeight =  viewSize.height

    val matrix = Matrix()
    val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
    val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
    val centerX = viewRect.centerX()
    val centerY = viewRect.centerY()

    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
        val scale = Math.max(
                viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width
        )
        with(matrix) {
            postScale(scale, scale, centerX, centerY)
            postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        }
    }
    return matrix
}

/**
 * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
 * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
 *
 * @param choices The list of available sizes
 * @return The video size
 */
fun chooseVideoSize(choices: Array<Size>) = choices.firstOrNull {
    it.width == it.height * 4 / 3 && it.width <= 1080 } ?: choices[choices.size - 1]

/**
 * Given [choices] of [Size]s supported by a camera, chooses the smallest one whose
 * width and height are at least as large as the respective requested values, and whose aspect
 * ratio matches with the specified value.
 *
 * @param choices     The list of sizes that the camera supports for the intended output class
 * @param width       The minimum desired width
 * @param height      The minimum desired height
 * @param aspectRatio The aspect ratio
 * @return The optimal [Size], or an arbitrary one if none were big enough
 */
private fun chooseOptimalSize(
    choices: Array<Size>,
    width: Int,
    height: Int,
    aspectRatio: Size
): Size {

    // Collect the supported resolutions that are at least as big as the preview Surface
    val w = aspectRatio.width
    val h = aspectRatio.height
    val bigEnough = choices.filter {
        it.height == it.width * h / w && it.width >= width && it.height >= height }

    // Pick the smallest of those, assuming we found any
    return if (bigEnough.isNotEmpty()) {
        Collections.min(bigEnough, CompareSizesByArea())
    } else {
        choices[0]
    }
}

/**
 * Compare two [Size]s based on their areas.
 */
class CompareSizesByArea : Comparator<Size> {

    // We cast here to ensure the multiplications won't overflow
    override fun compare(lhs: Size, rhs: Size) =
        signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
}

/*
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
        val videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
        val previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java), width, height, videoSize)
        if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) {
            TODO("set aspect ratio on texture view")
        } else {
            TODO("set aspect ratio on texture view")
        }
        val mediaRecorder = MediaRecorder()
*/

