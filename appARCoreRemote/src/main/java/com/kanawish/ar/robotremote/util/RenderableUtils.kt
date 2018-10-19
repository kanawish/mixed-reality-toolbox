package com.kanawish.ar.robotremote.util

import android.app.Activity
import android.support.annotation.LayoutRes
import android.support.annotation.RawRes
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.CompletableFuture


private fun <T> singleFuture(completableFuture: CompletableFuture<T>?) =
        Single.fromFuture(completableFuture)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())

fun Activity.viewRenderable(@LayoutRes layout: Int): Single<ViewRenderable> {
    return singleFuture(ViewRenderable.builder().setView(this, layout).build())
}

fun Activity.modelRenderable(@RawRes model: Int): Single<ModelRenderable> {
    return singleFuture(ModelRenderable.builder().setSource(this, model).build())
}
