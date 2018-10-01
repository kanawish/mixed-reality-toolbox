package com.kanawish.kotlin

import android.app.Activity
import android.content.Context
import android.os.Environment
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader

fun Activity.loadAssetString(filename: String) = loadStringFromAsset(this, filename)

/**
 * Always good to check your basics:
 * http://stackoverflow.com/questions/326390/how-to-create-a-java-string-from-the-contents-of-a-file
 * <p>
 * but since we're on android...
 * http://stackoverflow.com/questions/9095610/android-fileinputstream-read-txt-file-to-string
 */
@Throws(IOException::class)
fun loadStringFromAsset(context: Context, assetName: String): String {
    val input = context.assets.open(assetName)

    return readFile(InputStreamReader(input))
}

/**
 * Will wrap the provided reader in a BufferedReader, and return the contents
 * of this string as a single String.
 *
 * @param inputReader
 * @return file contents
 * @throws IOException in case of issues.
 */
@Throws(IOException::class)
fun readFile(inputReader: Reader): String {
    val bufferedReader = BufferedReader(inputReader)
    val stringBuilder = StringBuilder()
    val ls = System.getProperty("line.separator")

    do {
        val line: String? = bufferedReader.readLine()?.also {
            stringBuilder.append(it)
            stringBuilder.append(ls)
        }
    } while (line != null)

    return stringBuilder.toString()
}

fun isExternalStorageReadable(): Boolean {
    val state = Environment.getExternalStorageState()
    return state == Environment.MEDIA_MOUNTED ||
            state == Environment.MEDIA_MOUNTED_READ_ONLY
}
