package com.kanawish.kotlin

import android.app.Activity
import com.kanawish.gl.utils.FileUtils

fun Activity.loadAssetString(filename:String) = FileUtils.loadStringFromAsset(this, filename)