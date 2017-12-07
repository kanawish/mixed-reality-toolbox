package com.kanawish.glia.feature.arcore

import android.Manifest
import android.app.Activity
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.view.View
import android.widget.Toast
import com.google.ar.core.Config
import com.kanawish.gl.Program
import com.kanawish.gl.Shader
import com.kanawish.gl.utils.ModelUtils
import com.kanawish.glia.feature.R

import kotlinx.android.synthetic.main.activity_ar_core.*
import timber.log.Timber
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

import com.google.ar.core.Session
import com.kanawish.glia.feature.arcore.renderer.BackgroundRenderer

/**
 * Created on 2017-11-19.
 */
private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
private const val CAMERA_PERMISSION_CODE = 0
class ArCoreActivity : Activity() {

    lateinit var arCoreSession: Session
    lateinit var defaultConfig: Config

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.i("onCreate")

        if (!hasCameraPermission()) {
            Timber.i("hasCameraPermission reportedly not ok.")
            ActivityCompat.requestPermissions(this, arrayOf(CAMERA_PERMISSION), CAMERA_PERMISSION_CODE)
        }

        Timber.i("hasCameraPermission reportedly ok??")

        setContentView(R.layout.activity_ar_core)

        arCoreSession = Session(this)
        defaultConfig = Config.createDefaultConfig()
        if( !arCoreSession.isSupported(defaultConfig)) {
            Timber.e("Device doesn't support ARCore")
            finish()
            return
        }

        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setRenderer(ArCoreRenderer(this, arCoreSession))
    }

    override fun onResume() {
        super.onResume()
//            showLoadingMessage()
        // Note that order matters - see the note in onPause(), the reverse applies here.
        arCoreSession.resume(defaultConfig)
        glSurfaceView.onResume()

    }

    override fun onPause() {
        super.onPause()
        // Note that the order matters - GLSurfaceView is paused first so that it does not try
        // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
        // still call mSession.update() and get a SessionPausedException.
        glSurfaceView.onPause()
        arCoreSession.pause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        Timber.i("onRequestPermissionsResult $requestCode, $permissions, $results")
        if (!hasCameraPermission()) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            rootLayout.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    class ArCoreRenderer( val context:Context, val arCoreSession:Session ) : GLSurfaceView.Renderer {

        private val U_MV_MATRIX = "u_mvMatrix"
        private val U_MVP_MATRIX = "u_mvpMatrix"
        private val U_LIGHT_POSITION = "u_lightPosition"

        private val A_POSITION = "a_Position"
        private val A_NORMAL = "a_Normal"

        private var cube = ModelUtils.buildCube(1f)
        private var uLightPosition = FloatArray(3)

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

        private val backgroundRenderer = BackgroundRenderer()

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            Timber.i("Ep00Renderer.onSurfaceCreated()")

            // OPENGL CONFIGURATION
            // Set the background clear color of your choice.
            GLES20.glClearColor(0.0f, 0.0f, 1.0f, 1.0f)

            // Use culling to remove back faces.
            GLES20.glEnable(GLES20.GL_CULL_FACE)

            // Enable depth testing
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)

            backgroundRenderer.createOnGlThread(context)
            arCoreSession.setCameraTextureName(backgroundRenderer.textureId)

            // OPENGL PROGRAM INIT
            // Load episode 02 shaders from "assets/", compile them, returns shader handlers.
            val shaderHandles = Shader.compileShadersEp02(context)


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
            uLightPosition = floatArrayOf(0f, 2f, -2f)


            // VIEW MATRIX INIT - This call sets up the viewMatrix (our camera).
            Matrix.setLookAtM(
                    viewMatrix, 0, // result array, offset
                    0f, 0f, 1.5f, // coordinates for our 'eye'
                    0f, 0f, -5f, // center of view
                    0f, 1.0f, 0.0f  // 'up' vector
            )
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
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

            arCoreSession.setDisplayGeometry(width.toFloat(), height.toFloat())
        }

        override fun onDrawFrame(gl: GL10?) {
            // Refresh our fps counter.
//            fpsCounter.log()

            // TODO: Reposition world camera based on ar core session position updates, etc.

            // We clear the screen.
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

            val frame = arCoreSession.update()

            // Draw background.
            backgroundRenderer.draw(frame)

            // MODEL - Pass the vertex information (coordinates, normals) to the Vertex Shader
            GLES20.glVertexAttribPointer(
                    aPositionHandle,
                    ModelUtils.VALUES_PER_COORD,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    cube.getCoordinates())
            GLES20.glEnableVertexAttribArray(aPositionHandle)

            GLES20.glVertexAttribPointer(
                    aNormalHandle,
                    ModelUtils.VALUES_PER_NORMAL,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    cube.getNormals())
            GLES20.glEnableVertexAttribArray(aNormalHandle)


            // MODEL - Prepares the Model transformation Matrix, for the given elapsed time.
            animateModel(System.currentTimeMillis() - started)


            // MODEL-VIEW-PROJECTION
            // Multiply view by model matrix. uMvMatrix holds the result.
            Matrix.multiplyMM(uMvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            // Assign matrix to uniform handle.
            GLES20.glUniformMatrix4fv(uMvMatrixHandle, 1, false, uMvMatrix, 0)

            // Multiply model-view matrix by projection matrix, uMvpMatrix holds the result.
            Matrix.multiplyMM(uMvpMatrix, 0, projectionMatrix, 0, uMvMatrix, 0)
            // Assign matrix to uniform handle.
            GLES20.glUniformMatrix4fv(uMvpMatrixHandle, 1, false, uMvpMatrix, 0)


            // Assign light position to uniform handle.
            GLES20.glUniform3f(uLightPositionHandle, uLightPosition[0], uLightPosition[1], uLightPosition[2])

            // Draw call
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_INT, cube.getIndices())
        }

        private fun animateModel(elapsed: Long) {
            val msCycle = 14000
            val angle = elapsed % msCycle / msCycle.toFloat() * 360f
            Matrix.setIdentityM(modelMatrix, 0)                // Initialize
            Matrix.translateM(modelMatrix, 0, 0f, 0f, -2f)     // Move model in front of camera (-Z is in front of us)
            Matrix.rotateM(modelMatrix, 0, angle, 1f, 1f, 0f)    // Rotate model on the X axis.
        }

    }
}