package com.kanawish.dd.robotcontroller

import android.app.Activity
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.view.View
import com.kanawish.gl.Program
import com.kanawish.gl.utils.ModelUtils
import com.kanawish.kotlin.buildShaders
import com.kanawish.kotlin.loadAssetString
import kotlinx.android.synthetic.main.standalone_ui.*
import timber.log.Timber
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val U_MV_MATRIX = "u_mvMatrix"
private const val U_MVP_MATRIX = "u_mvpMatrix"
private const val U_LIGHT_POSITION = "u_lightPosition"

private const val A_POSITION = "a_Position"
private const val A_NORMAL = "a_Normal"

private val LIGHT_POS_IN_WORLD_SPACE = floatArrayOf(0.0f, 1.0f, 1.0f, 1.0f);

class StandaloneActivity : Activity() {

    private lateinit var renderer: Renderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.standalone_ui)

        glSurfaceView.setEGLContextClientVersion(3)

        renderer = Renderer(
                loadAssetString("shaders/gles2.ep02.vertshader"),
                loadAssetString("shaders/gles2.ep02.fragshader"))
        glSurfaceView.setRenderer(renderer)
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        glSurfaceView.onPause() // TBH, not sure it's important to put this before onPause(). TODO: Find out, ask someone who would known.
        super.onPause()
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

    /**
     * Generic name until I come up with better one.
     */
    class ModelContext(vertSource: String, fragSource: String) {
        private val programHandle: Int

        private val uMvMatrixHandle: Int
        private val uMvpMatrixHandle: Int
        private val uLightPositionHandle: Int

        private val aPositionHandle: Int
        private val aNormalHandle: Int

        init {
            // OPENGL PROGRAM INIT

            // Load episode 02 shaders from "assets/", compile them, returns shader handlers.
            // Link the shaders to form a program, binding attributes
            programHandle = Program.linkProgram(buildShaders(vertSource, fragSource), A_POSITION, A_NORMAL)

            uMvMatrixHandle = GLES30.glGetUniformLocation(programHandle, U_MV_MATRIX)
            uMvpMatrixHandle = GLES30.glGetUniformLocation(programHandle, U_MVP_MATRIX)
            uLightPositionHandle = GLES30.glGetUniformLocation(programHandle, U_LIGHT_POSITION)

            aPositionHandle = GLES30.glGetAttribLocation(programHandle, A_POSITION)
            aNormalHandle = GLES30.glGetAttribLocation(programHandle, A_NORMAL)
        }

        fun useProgram() {
            GLES30.glUseProgram(programHandle)
        }
    }

    class Renderer constructor(val vertSource: String, val fragSource: String) : GLSurfaceView.Renderer {

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
            Matrix.rotateM(this,0,10f,1f,0f, 0f)
        }

        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            Timber.i("Ep00Renderer.onSurfaceCreated()")

            // OPENGL CONFIGURATION
            // Set the background clear color of your choice.
            GLES30.glClearColor(0.0f, 0.0f, 1.0f, 1.0f)

            // Use culling to remove back faces.
            GLES30.glEnable(GLES30.GL_CULL_FACE)

            // Enable depth testing
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)


            // OPENGL PROGRAM INIT

            // Load episode 02 shaders from "assets/", compile them, returns shader handlers.
            // Link the shaders to form a program, binding attributes
            programHandle = Program.linkProgram(buildShaders(vertSource, fragSource), A_POSITION, A_NORMAL)

            uMvMatrixHandle = GLES30.glGetUniformLocation(programHandle, U_MV_MATRIX)
            uMvpMatrixHandle = GLES30.glGetUniformLocation(programHandle, U_MVP_MATRIX)
            uLightPositionHandle = GLES30.glGetUniformLocation(programHandle, U_LIGHT_POSITION)

            aPositionHandle = GLES30.glGetAttribLocation(programHandle, A_POSITION)
            aNormalHandle = GLES30.glGetAttribLocation(programHandle, A_NORMAL)

            GLES30.glUseProgram(programHandle)


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
            GLES30.glViewport(0, 0, width, height)

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

        internal fun cameraAdjust() {
            Matrix.translateM(viewMatrix, 0, 0f, 1f, 1f)
        }

        fun FloatArray.orientModel(orientationMatrix: FloatArray): FloatArray {
            Matrix.setIdentityM(this, 0)                // Initialize
            Matrix.translateM(this, 0, 0f, 0f, -0.5f)     // Move model in front of camera (-Z is in front of us)
            Matrix.multiplyMM(this, 0, this, 0, orientationMatrix, 0)
            return this
        }

        override fun onDrawFrame(gl: GL10) {
            // We clear the screen.
            GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT or GLES30.GL_COLOR_BUFFER_BIT)

            // MODEL - Pass the vertex information (coordinates, normals) to the Vertex Shader
            aPositionHandle.assignAttributes(ModelUtils.VALUES_PER_COORD, cube!!.coordinates)
            aNormalHandle.assignAttributes(ModelUtils.VALUES_PER_NORMAL, cube!!.normals)


            // MODEL - Prepares the Model transformation Matrix, for the given elapsed time.
//            animateModel(System.currentTimeMillis() - started)


            // MODEL-VIEW-PROJECTION
            // Multiply view by model matrix. uMvMatrix holds the result, then assign matrix to uniform handle.
            uMvMatrixHandle.assign4fv(uMvMatrix.assignMultMM(viewMatrix, modelMatrix.orientModel(orientationMatrix)))
            // lots more cut out here, and the below:
//            GLES30.glUniformMatrix4fv(uMvMatrixHandle, 1, false, uMvMatrix, 0)

            // Multiply model-view matrix by projection matrix, uMvpMatrix holds the result.
            // Assign matrix to uniform handle.
            uMvpMatrixHandle.assign4fv(uMvpMatrix.assignMultMM(projectionMatrix,uMvMatrix))
            // vs
//            Matrix.multiplyMM(uMvpMatrix, 0, projectionMatrix, 0, uMvMatrix, 0)
//            GLES30.glUniformMatrix4fv(uMvpMatrixHandle, 1, false, uMvpMatrix, 0)

            // Set the position of the light
            // Assign light position to uniform handle.
            uLightPositionHandle.assign3f(uLightPosition.assignMultMV(viewMatrix, LIGHT_POS_IN_WORLD_SPACE))
            // vs
            // Matrix.multiplyMV(uLightPosition, 0, viewMatrix, 0, LIGHT_POS_IN_WORLD_SPACE, 0)
//            GLES30.glUniform3f(uLightPositionHandle, uLightPosition[0], uLightPosition[1], uLightPosition[2])

            // Draw call
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, cube!!.count, GLES30.GL_UNSIGNED_INT, cube!!.indices)
        }

        fun FloatArray.assignMultMV(lhsMat: FloatArray, rhsVec: FloatArray): FloatArray {
            Matrix.multiplyMV(this, 0, lhsMat, 0, rhsVec, 0)
            return this
        }

        fun FloatArray.assignMultMM(lhs: FloatArray, rhs: FloatArray): FloatArray {
            Matrix.multiplyMM(this, 0, lhs, 0, rhs, 0)
            return this
        }

        fun Int.assignAttributes(size: Int, buffer: FloatBuffer) {
            GLES30.glVertexAttribPointer(this, size, GLES30.GL_FLOAT, false, 0, buffer)
            GLES30.glEnableVertexAttribArray(this)
        }

        fun Int.assign4fv(matrix: FloatArray) = GLES30.glUniformMatrix4fv(this, 1, false, matrix, 0)

        fun Int.assign3f(vector: FloatArray) = GLES30.glUniform3f(this, vector[0], vector[1], vector[2])

        fun draw(context:ModelContext, model: ModelUtils.Ep02Model) {
            TODO("implement")
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, model.count, GLES30.GL_UNSIGNED_INT, model.indices)
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

