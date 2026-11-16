package io.github.carrombot

import android.os.Handler
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/** Service-owned Handler dispatcher; never creates another thread or retains an Activity. */
internal class MainDispatcher(
    private val handler: Handler,
) : CoroutineDispatcher() {
    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        check(handler.post(block)) { "Main looper unavailable" }
    }
}
