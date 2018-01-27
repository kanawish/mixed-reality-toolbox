package com.kanawish.thing.cube

import android.annotation.SuppressLint
import android.app.Activity
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import com.kanawish.gl.Program
import com.kanawish.gl.Shader
import com.kanawish.gl.utils.FpsCounter
import com.kanawish.gl.utils.ModelUtils
import com.kanawish.librx.firebase.FirebaseAuthManager
import com.kanawish.librx.firebase.FirebaseDbManager
import timber.log.Timber
import javax.inject.Inject
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class MainActivity : Activity() {

    @Inject lateinit var firebaseDbManager: FirebaseDbManager

    companion object {

        private val U_MV_MATRIX = "u_mvMatrix"
        private val U_MVP_MATRIX = "u_mvpMatrix"
        private val U_LIGHT_POSITION = "u_lightPosition"

        private val A_POSITION = "a_Position"
        private val A_NORMAL = "a_Normal"

        private val LIGHT_POS_IN_WORLD_SPACE = floatArrayOf(0.0f, 1.0f, 1.0f, 1.0f);
    }

    private lateinit var glSurfaceView: GLSurfaceView
    private var fpsTextView: TextView? = null
    private var msTextView: TextView? = null

    private val fpsCounter = FpsCounter(this::refreshFps)
    private var rootLayout: RelativeLayout? = null

    private lateinit var renderer: Ep02Renderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_episodes_01)

        rootLayout = findViewById<View>(R.id.rootLayout) as RelativeLayout

        // Manifest feature request makes sure we have the right level of OpenGL support.
        glSurfaceView = findViewById<View>(R.id.glSurfaceView) as GLSurfaceView

        glSurfaceView.setEGLContextClientVersion(2)
        renderer = Ep02Renderer()
        glSurfaceView.setRenderer(renderer)

        fpsTextView = findViewById<View>(R.id.fpsTextView) as TextView
        msTextView = findViewById<View>(R.id.msTextView) as TextView
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        firebaseDbManager.orientationMatrix().subscribe { glSurfaceView.queueEvent{ renderer.orientationMatrix = it } }
    }

    override fun onPause() {
        glSurfaceView.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            rootLayout!!.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun refreshFps(msAverage: Double?) {
        fpsTextView!!.text = String.format("%4.1f fps", 1000.0 / msAverage!!)
        msTextView!!.text = String.format("%4.2f ms", msAverage)
    }

    private inner class Ep02Renderer internal constructor() : GLSurfaceView.Renderer {

        private var cube: ModelUtils.Ep02Model? = null
        private var uLightPosition = FloatArray(4)

        private val modelMatrix = FloatArray(16)
        private val viewMatrix = FloatArray(16)
        private val projectionMatrix = FloatArray(16)

        private val uMvMatrix = FloatArray(16)
        private val uMvpMatrix = FloatArray(16)

        private var programHandle: Int = 0

        private var uMvMatrixHandle: Int = 0
        private var uMvpMatrixHandle: Int = 0
        private var uLightPositionHandle: Int = 0

        private var aPositionHandle: Int = 0
        private var aNormalHandle: Int = 0

        private var started: Long = 0

        var orientationMatrix = FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)
        }

        internal fun cameraAdjust() {
            Matrix.translateM(viewMatrix,0,0f,1f,1f)
        }

        internal fun orientModel(orientationMatrix: FloatArray) {
            Matrix.setIdentityM(modelMatrix, 0)                // Initialize
            Matrix.translateM(modelMatrix, 0, 0f, 0f, -0.5f)     // Move model in front of camera (-Z is in front of us)
            Matrix.multiplyMM(modelMatrix, 0, modelMatrix, 0, orientationMatrix, 0)
        }

        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
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
            val shaderHandles = Shader.compileShadersEp02(this@MainActivity)

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
            uLightPosition = FloatArray(4)

            // VIEW MATRIX INIT - This call sets up the viewMatrix (our camera).
            Matrix.setLookAtM(
                    viewMatrix, 0, // result array, offset
                    0f, 0f, 1.5f, // coordinates for our 'eye'
                    0f, 0f, -5f, // center of view
                    0f, 1.0f, 0.0f  // 'up' vector
            )

        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            Timber.i("Ep00Renderer.onSurfaceChanged(%d, %d)", width, height)

            // We want the viewport to match our screen's geometry.
            GLES20.glViewport(0, 0, width, height)

            val ratio = width.toFloat() / height

            // PROJECTION MATRIX - This call sets up the projectionMatrix.
            Matrix.frustumM(
                    projectionMatrix, 0, // target matrix, offset
                    -ratio, ratio, // left, right
                    -1.0f, 1.0f, // bottom, top
                    1f, 100f         // near, far
            )

            started = System.currentTimeMillis()
        }

        override fun onDrawFrame(gl: GL10) {
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
            orientModel(orientationMatrix)

            // MODEL-VIEW-PROJECTION
            // Multiply view by model matrix. uMvMatrix holds the result.
            Matrix.multiplyMM(uMvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            // Assign matrix to uniform handle.
            GLES20.glUniformMatrix4fv(uMvMatrixHandle, 1, false, uMvMatrix, 0)

            // Multiply model-view matrix by projection matrix, uMvpMatrix holds the result.
            Matrix.multiplyMM(uMvpMatrix, 0, projectionMatrix, 0, uMvMatrix, 0)
            // Assign matrix to uniform handle.
            GLES20.glUniformMatrix4fv(uMvpMatrixHandle, 1, false, uMvpMatrix, 0)

            // Set the position of the light
            Matrix.multiplyMV(uLightPosition, 0, viewMatrix, 0, LIGHT_POS_IN_WORLD_SPACE, 0)
            // Assign light position to uniform handle.
            GLES20.glUniform3f(uLightPositionHandle, uLightPosition[0], uLightPosition[1], uLightPosition[2])

            // Draw call
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_INT, cube!!.indices)
        }

        internal fun animateModel(elapsed: Long) {
            val msCycle = 14000
            val angle = elapsed % msCycle / msCycle.toFloat() * 360f
            Matrix.setIdentityM(modelMatrix, 0)                // Initialize
            Matrix.translateM(modelMatrix, 0, 0f, 0f, -2f)     // Move model in front of camera (-Z is in front of us)
            Matrix.rotateM(modelMatrix, 0, angle, 1f, 1f, 0f)    // Rotate model on the X axis.
        }

    }
}