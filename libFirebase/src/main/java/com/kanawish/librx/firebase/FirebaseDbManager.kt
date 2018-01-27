package com.kanawish.librx.firebase

import com.google.firebase.database.*
import com.jakewharton.rxrelay2.PublishRelay
import com.kanawish.librx.firebase.FirebaseAuthManager.State.Anonymous
import com.kanawish.librx.firebase.FirebaseAuthManager.State.Registered
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * To keep things very simple, we'll be sharing key-value pairs of things like matrices and vectors
 */
private const val DATA = "data"
private const val MATRICES = "matrices"
private const val VECTORS = "vectors"

fun matrixDbRef(): DatabaseReference = FirebaseDatabase.getInstance().getReference(DATA).child(MATRICES)

@Singleton
class FirebaseDbManager @Inject constructor(
        authManager: FirebaseAuthManager
) {
    // The repo's `input channel`
    private val consumer = PublishRelay.create<FloatArray>()

    private val producer: Observable<FloatArray>

    private val disposables = CompositeDisposable()

    init {
        // Only should try to produce events from firebase when Authed, otherwise causes exceptions / spams anonymous user creation due to reboots.
        producer = authManager.state().switchMap { state ->
            when (state) {
                is Anonymous, Registered -> orientationObservable()
                else -> Observable.empty<FloatArray>()
            }
        }

        authManager.state().subscribe { state ->
            when (state) {
                is Anonymous, Registered -> {
                    // Writes to Firebase
                    disposables += consumer
                            .throttleLast(16, TimeUnit.MILLISECONDS, Schedulers.io())
                            .map(FloatArray::asList)
                            .subscribe { floatList ->
                                matrixDbRef().child("dummy").setValue(floatList)
                            }
                }
                else -> {
                    disposables.clear()
                }
            }
        }
    }

    private fun orientationObservable(): Observable<FloatArray>? {
        return Observable.create<FloatArray> { emitter ->
            Timber.i("matrixDbRef().addValueEventListener()")
            matrixDbRef().addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val value = snapshot.child("dummy").value as List<Float>
                        emitter.onNext(FloatArray(value.size, { i -> value[i] }))
                    } else {
                        Timber.i("matrixDbRef doesn't exist in Firebase DB")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Timber.i("onCancelled()")
                    emitter.onError(error.toException())
                }
            })
        }
    }

    fun consume(fa: FloatArray) = consumer.accept(fa)

    fun orientationMatrix(): Observable<FloatArray> = producer
}