package com.kanawish.ar.robotremote.util

import android.app.Activity
import android.support.annotation.LayoutRes
import android.support.annotation.RawRes
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import io.reactivex.Single


fun Activity.viewRenderable(@LayoutRes layout:Int): Single<ViewRenderable> {
    return Single.fromFuture(ViewRenderable.builder().setView(this,layout).build())
}

fun Activity.modelRenderable(@RawRes model:Int): Single<ModelRenderable> {
    return Single.fromFuture(ModelRenderable.builder().setSource(this,model).build())
}
