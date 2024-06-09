package app.vitune.android.utils

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.GlyphManager
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

const val TAG = "GlyphInterface"

enum class NothingDevice(val tag: String, val progressChannel: Int) {
    Phone1(tag = Common.DEVICE_20111, progressChannel = Glyph.Code_20111.D1_1),
    Phone2(tag = Common.DEVICE_22111, progressChannel = Glyph.Code_22111.C1_1),
    Phone2a(tag = Common.DEVICE_23111, progressChannel = Glyph.Code_23111.C_1)
}

val nothingDevice
    get() = when {
        Common.is20111() -> NothingDevice.Phone1
        Common.is22111() -> NothingDevice.Phone2
        Common.is23111() -> NothingDevice.Phone2a
        else -> null
    }

class GlyphInterface(context: Context) : AutoCloseable {
    private val bound = AtomicBoolean()
    val isBound get() = bound.get()

    private val shouldOpenSession = AtomicBoolean()

    private val coroutineScope = CoroutineScope(
        Dispatchers.Main +
                SupervisorJob() +
                CoroutineName(TAG)
    )
    private val mutex = Mutex()

    private val callback: GlyphManager.Callback = object : GlyphManager.Callback {
        override fun onServiceConnected(p0: ComponentName?) {
            bound.set(true)
            manager?.register(nothingDevice?.tag ?: return)
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            bound.set(false)
        }
    }

    private val manager by lazy {
        GlyphManager.getInstance(context.applicationContext).takeIf {
            runCatching { it.init(callback) }.also {
                it.exceptionOrNull()?.printStackTrace()
            }.isSuccess
        }
    }

    private fun openSession() {
        if (!isBound || manager == null) return shouldOpenSession.set(false)
        if (shouldOpenSession.getAndSet(true)) return

        runCatching { manager?.openSession() }.exceptionOrNull()?.let {
            shouldOpenSession.set(false)
            if (it is GlyphException) Log.e(TAG, it.message.orEmpty())
            it.printStackTrace()
        }
    }

    private fun closeSession() {
        if (!isBound || manager == null || shouldOpenSession.getAndSet(false)) return

        runCatching { manager?.closeSession() }.exceptionOrNull()?.let {
            if (it is GlyphException) Log.e(TAG, it.message.orEmpty())
            it.printStackTrace()
        }
    }

    fun tryInit() = manager != null

    fun glyph(block: suspend GlyphManager.() -> Unit): Job? {
        if (!isBound) return null

        return coroutineScope.launch {
            mutex.withLock {
                openSession()
                runCatching {
                    manager?.block()
                }.exceptionOrNull()?.let {
                    if (it is GlyphException) Log.e(TAG, it.message.orEmpty())
                    it.printStackTrace()
                }
                closeSession()
            }
        }
    }

    override fun close() {
        if (!bound.getAndSet(false)) return

        manager?.let {
            closeSession()
            it.unInit()
        }
    }
}

fun GlyphManager.newProgressFrame(): GlyphFrame? {
    return glyphFrameBuilder?.buildChannel(nothingDevice?.progressChannel ?: return null)?.build()
}

fun GlyphInterface.progress(state: Flow<Int>) = glyph {
    val frame = newProgressFrame()
    state
        .distinctUntilChanged()
        .collectLatest {
            displayProgress(frame, it)
        }
    turnOff()
}
