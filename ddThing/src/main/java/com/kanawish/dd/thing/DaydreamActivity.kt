package com.kanawish.dd.thing

import android.annotation.SuppressLint
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Bundle
import com.google.vr.sdk.base.*
import com.google.vr.sdk.controller.Controller
import com.google.vr.sdk.controller.ControllerManager
import com.kanawish.gl.Program
import com.kanawish.gl.Shader
import com.kanawish.gl.utils.FpsCounter
import com.kanawish.gl.utils.ModelUtils
import com.kanawish.librx.firebase.FirebaseDbManager
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.common_ui.*
import timber.log.Timber
import javax.inject.Inject
import javax.microedition.khronos.egl.EGLConfig

fun Controller.bindController(eventQueue: (Runnable) -> Unit, consumer: (Controller) -> Unit) =
        this.setEventListener(ControllerEventListener(this, eventQueue, consumer))

class ControllerEventListener(controller: Controller, eventQueue: (Runnable) -> Unit, consumer: (Controller) -> Unit)
    : Controller.EventListener() {

    private val update: () -> Unit = { eventQueue(Runnable { consumer(controller) }) }

    override fun onConnectionStateChanged(state: Int) {
        Timber.i("ConnectionState changed: ${Controller.ConnectionStates.toString(state)}")
        update()
    }

    override fun onUpdate() = update()
}

class ManagerEventListener() : ControllerManager.EventListener {
    override fun onApiStatusChanged(state: Int) {
        Timber.i("ApiStatus changed ${ControllerManager.ApiStatus.toString(state)}")
    }

    override fun onRecentered() {
        // TODO: Implement
        Timber.i("onRecentered()")
    }
}


class DaydreamActivity() : GvrActivity() {

    @Inject lateinit var firebaseDbManager: FirebaseDbManager

    private val fpsCounter = FpsCounter(this::refreshFps)

    private lateinit var controllerManager: ControllerManager
    private lateinit var controller: Controller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.common_ui)

        // Assing gvrView from kotlin's synthetic accessor.
        gvrView = gvr_view
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8)

        controllerManager = ControllerManager(this, ManagerEventListener())

        gvrView.setRenderer(Renderer(controllerManager.controller))
        gvrView.setTransitionViewEnabled(true)

        if (gvr_view.setAsyncReprojectionEnabled(true)) {
            // Async reprojection decouples the app framerate from the display framerate,
            // allowing immersive interaction even at the throttled clockrates set by
            // sustained performance mode.
            AndroidCompat.setSustainedPerformanceMode(this, true)
        }

    }

    val disposables = CompositeDisposable()

    override fun onResume() {
        super.onResume()

        controllerManager.start()
    }


    override fun onPause() {
        disposables.dispose()

        controllerManager.stop()

        super.onPause()
    }

    @SuppressLint("DefaultLocale")
    private fun refreshFps(msAverage: Double?) {
//        fpsTextView!!.text = String.format("%4.1f fps", 1000.0 / msAverage!!)
//        msTextView!!.text = String.format("%4.2f ms", msAverage)
    }

    companion object {
        private val U_MV_MATRIX = "u_mvMatrix"
        private val U_MVP_MATRIX = "u_mvpMatrix"
        private val U_LIGHT_POSITION = "u_lightPosition"

        private val A_POSITION = "a_Position"
        private val A_NORMAL = "a_Normal"

        // We keep the light always position just above the user.
        private val LIGHT_POS_IN_WORLD_SPACE = floatArrayOf(0.0f, 2.0f, 0.0f, 1.0f);

    }

    private inner class Renderer internal constructor(val controller: Controller) : GvrView.StereoRenderer {

        private var cube: ModelUtils.Ep02Model? = null

        //        private var uLightPosition = FloatArray(3)
        private var uLightPositionHandle: Int = 0

        private val cameraMatrix = FloatArray(16)

        private val modelMatrix = FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)                    // Initialize
            Matrix.translateM(this, 0, 0f, 0f, -2f)  // Move model in front of camera (-Z is in front of us)
        }

        private val viewMatrix = FloatArray(16)
//        private val projectionMatrix = FloatArray(16)

        private val uMvMatrix = FloatArray(16)
        private var uMvMatrixHandle: Int = 0

        private val uMvpMatrix = FloatArray(16)
        private var uMvpMatrixHandle: Int = 0

        private var programHandle: Int = 0

        private var aPositionHandle: Int = 0

        private var aNormalHandle: Int = 0

        private var started: Long = 0


        // private val headRotation = FloatArray(4) // TODO: Use for sound engine.
        private val headViewMatrix = FloatArray(16)
        private val lightPosEyeSpaceMatrix = FloatArray(4)

        private val controllerOrientationMatrix = FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)
        }

        private val armModel = GvrArmModel()

        fun controllerUpdate() {
            controller.update()
            firebaseDbManager.consume(controllerOrientationMatrix)
            controller.orientation.toRotationMatrix(controllerOrientationMatrix)
        }

        private val tempMatrix = FloatArray(16)
        internal fun orientModel(orientationMatrix: FloatArray) {
            Matrix.setIdentityM(modelMatrix, 0)                // Initialize
            Matrix.translateM(modelMatrix, 0, 0f, 0f, -2f)     // Move model in front of camera (-Z is in front of us)
            Matrix.multiplyMM(modelMatrix, 0, modelMatrix, 0, orientationMatrix, 0)
        }

        internal fun animateModel(elapsed: Long) {
            val msCycle = 14000
            val angle = elapsed % msCycle / msCycle.toFloat() * 360f
            Matrix.setIdentityM(modelMatrix, 0)                // Initialize
            Matrix.translateM(modelMatrix, 0, 0f, 0f, -2f)     // Move model in front of camera (-Z is in front of us)
            Matrix.rotateM(modelMatrix, 0, angle, 1f, 1f, 0f)    // Rotate model on the X+Y axis.
        }

        override fun onSurfaceCreated(config: EGLConfig) {
            Timber.i("Ep00Renderer.onSurfaceCreated()")

            // OPENGL CONFIGURATION
            // Set the background clear color of your choice.
            GLES20.glClearColor(0.0f, 0.0f, 1.0f, 1.0f)

            // Use culling to remove back faces.
            GLES20.glEnable(GLES20.GL_CULL_FACE)

            // Enable depth testing
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)


            // OPENGL PROGRAM INIT
            // Load episode 02 shaders from "assets/", compile them, returns shader handlers.
            val shaderHandles = Shader.compileShadersEp02(this@DaydreamActivity)

            // Link the shaders to form a program, binding attributes
            programHandle = Program.linkProgram(shaderHandles, A_POSITION, A_NORMAL)

            uMvMatrixHandle = GLES20.glGetUniformLocation(programHandle, U_MV_MATRIX)
            uMvpMatrixHandle = GLES20.glGetUniformLocation(programHandle, U_MVP_MATRIX)
            uLightPositionHandle = GLES20.glGetUniformLocation(programHandle, U_LIGHT_POSITION)

            aPositionHandle = GLES20.glGetAttribLocation(programHandle, A_POSITION)
            aNormalHandle = GLES20.glGetAttribLocation(programHandle, A_NORMAL)

            GLES20.glUseProgram(programHandle)


            // MODEL INIT - Set up model(s)
            // Our cube model.
            cube = ModelUtils.buildCube(1f)

            // LIGHTING INIT
//            uLightPosition = floatArrayOf(0f, 2f, -2f)

        }

        override fun onSurfaceChanged(width: Int, height: Int) {
            Timber.i("Ep00Renderer.onSurfaceChanged(%d, %d)", width, height)

            // We want the viewport to match our screen's geometry.
            GLES20.glViewport(0, 0, width, height)

            val ratio = width.toFloat() / height

            // PROJECTION MATRIX - This call sets up the projectionMatrix.
/*
            Matrix.frustumM(
                    projectionMatrix, 0, // target matrix, offset
                    -ratio, ratio, // left, right
                    -1.0f, 1.0f, // bottom, top
                    1f, 100f         // near, far
            )
*/

            started = System.currentTimeMillis()
        }

        override fun onNewFrame(headTransform: HeadTransform) {

            // VIEW MATRIX INIT - This call sets up the cameraMatrix (used to be straight to viewMatrix).
            Matrix.setLookAtM(
                    cameraMatrix, 0, // result array, offset
                    0f, 0f, 0.01f, // coordinates for our 'eye' [Z adjusted for vr]
                    0f, 0f, 0f, // center of view
                    0f, 1.0f, 0.0f  // 'up' vector
            )

            headTransform.getHeadView(headViewMatrix, 0)

/*
            setCubeRotation()

            // Build the camera matrix and apply it to the ModelView.
            Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f)

            headTransform.getHeadView(headView, 0)

            // Update the 3d audio engine with the most recent head rotation.
            headTransform.getQuaternion(headRotation, 0)
            gvrAudioEngine.setHeadRotation(
                    headRotation[0], headRotation[1], headRotation[2], headRotation[3])
            // Regular update call to GVR audio engine.
            gvrAudioEngine.update()

            checkGLError("onReadyToDraw")
*/
        }

        override fun onDrawEye(eye: Eye) {
            // Refresh our fps counter.
            fpsCounter.log()

            // We clear the screen.
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

            // MODEL - Pass the vertex information (coordinates, normals) to the Vertex Shader
            GLES20.glVertexAttribPointer(
                    aPositionHandle,
                    ModelUtils.VALUES_PER_COORD,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    cube!!.coordinates)
            GLES20.glEnableVertexAttribArray(aPositionHandle)

            GLES20.glVertexAttribPointer(
                    aNormalHandle,
                    ModelUtils.VALUES_PER_NORMAL,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    cube!!.normals)
            GLES20.glEnableVertexAttribArray(aNormalHandle)


            // MODEL - Prepares the Model transformation Matrix, for the given elapsed time.
//            animateModel(System.currentTimeMillis() - started)
            controllerUpdate()
            orientModel(controllerOrientationMatrix)

            // Apply the eye transformation to the camera.
            Matrix.multiplyMM(viewMatrix, 0, eye.eyeView, 0, cameraMatrix, 0)
            // Set the position of the light
            Matrix.multiplyMV(lightPosEyeSpaceMatrix, 0, viewMatrix, 0, LIGHT_POS_IN_WORLD_SPACE, 0)

            // Multiply view by model matrix. uMvMatrix holds the result.
            Matrix.multiplyMM(uMvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            // Assign matrix to uniform handle.
            GLES20.glUniformMatrix4fv(uMvMatrixHandle, 1, false, uMvMatrix, 0)

            // Multiply model-view matrix by projection matrix, uMvpMatrix holds the result.
            Matrix.multiplyMM(uMvpMatrix, 0, eye.getPerspective(0.1f, 100.0f), 0, uMvMatrix, 0)
            // Assign matrix to uniform handle.
            GLES20.glUniformMatrix4fv(uMvpMatrixHandle, 1, false, uMvpMatrix, 0)

            // Assign light position to uniform handle.
            GLES20.glUniform3f(uLightPositionHandle, lightPosEyeSpaceMatrix[0], lightPosEyeSpaceMatrix[1], lightPosEyeSpaceMatrix[2])

            // Draw call
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_INT, cube!!.indices)
        }

        override fun onFinishFrame(p0: Viewport?) {
        }

        override fun onRendererShutdown() {
            Timber.i("onRendererShutdown()")
        }

    }
}