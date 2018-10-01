package com.kanawish.kotlin

import android.opengl.GLES20
import timber.log.Timber

fun buildShaders(vertShaderSource: String, fragShaderSource: String): IntArray {
    return intArrayOf(
            Shader(GLES20.GL_VERTEX_SHADER, vertShaderSource).handle,
            Shader(GLES20.GL_FRAGMENT_SHADER, fragShaderSource).handle
    )
}

/**
 * Builds a shader of given type by taking the provided source code,
 * assigning it to a new GL shader instance and compiling it.
 *
 * If this process is successful, the resulting handle is stored in `handle`
 *
 * @param type GLES20.GL_VERTEX_SHADER or GLES20.GL_FRAGMENT_SHADER
 * @param sourceCode a string containing the shader source code
 *
 *
 */
class Shader constructor(val type: Int, val sourceCode: String) {

    /**
     *  Handle to the new compiled shader, or 0 if construction failed.
     */
    var handle: Int

    init {
        // Get a handle to an empty shader of our desired type.
        handle = GLES20.glCreateShader(type)

        if (handle != 0) {
            // Assign source code to the empty shader.
            GLES20.glShaderSource(handle, sourceCode)

            // Attempt compilation of the source code.
            GLES20.glCompileShader(handle)

            // Check if compilation was successful.
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(handle, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

            // If compilation failed, remove the invalid shader from the OpenGL pipeline.
            if (compileStatus[0] == 0) {
                GLES20.glDeleteShader(handle)
                handle = 0
            }
        } else {
            Timber.e("Error creating shader.")
        }

        // Log an error + shader source code if compilation
        if (handle == 0) {
            Timber.e("Error compiling shader. \n%s", sourceCode)
        }
    }

}
