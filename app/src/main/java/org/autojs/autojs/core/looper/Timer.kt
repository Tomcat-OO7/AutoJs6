package org.autojs.autojs.core.looper

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.autojs.autojs.runtime.ScriptRuntime
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Created by Stardust on 2017/12/27.
 */
class Timer(
    runtime: ScriptRuntime,
    looper: Looper
) {
    private val mHandlerCallbacks = ConcurrentHashMap<Int, Runnable?>()
    private val mRuntime: ScriptRuntime = runtime
    private val mHandler: Handler = Handler(looper)
    private val isUiLoop: Boolean = looper == Looper.getMainLooper()

    constructor(runtime: ScriptRuntime, ) : this(runtime, Looper.myLooper()!!)

    fun setTimeout(callback: Any, delay: Long, vararg args: Any): Int {
        val id = createTimerId()
        val r = Runnable {
            callFunction(callback, null, args as Array<Any>)
            mHandlerCallbacks.remove(id)
        }
        mHandlerCallbacks[id] = r
        postDelayed(r, delay)
        return id
    }

    private fun callFunction(callback: Any, thiz: Any?, args: Array<Any>) {
        try {
            mRuntime.bridges.callFunction(callback, thiz, args)
        } catch (e: Exception) {
            if (isUiLoop) {
                mRuntime.exit(e)
            } else throw e
        }
    }

    @Synchronized
    private fun createTimerId(): Int {
        var id: Int
        do {
            id = Random.nextInt()
        } while (mHandlerCallbacks.containsKey(id))
        mHandlerCallbacks[id] = EMPTY_RUNNABLE
        return id
    }

    fun setInterval(listener: Any, interval: Long, vararg args: Any): Int {
        val id = createTimerId()
        val r: Runnable = object : Runnable {
            override fun run() {
                if (mHandlerCallbacks[id] == null) return
                callFunction(listener, null, args as Array<Any>)
                postDelayed(this, interval)
            }
        }
        mHandlerCallbacks[id] = r
        postDelayed(r, interval)
        return id
    }

    fun postDelayed(r: Runnable, interval: Long) {
        val uptime = SystemClock.uptimeMillis() + interval
        mHandler.postAtTime(r, uptime)
    }

    fun post(r: Runnable) {
        mHandler.post(r)
    }

    fun clearInterval(id: Int): Boolean = clearCallback(id)
    fun clearImmediate(id: Int): Boolean = clearCallback(id)
    fun clearTimeout(id: Int): Boolean = clearCallback(id)

    fun setImmediate(listener: Any, vararg args: Any): Int {
        val id = createTimerId()
        val r = Runnable {
            callFunction(listener, null, args as Array<Any>)
            mHandlerCallbacks.remove(id)
        }
        mHandlerCallbacks[id] = r
        post(r)
        return id
    }


    private fun clearCallback(id: Int): Boolean {
        val callback = mHandlerCallbacks[id]
        if (callback != null) {
            mHandler.removeCallbacks(callback)
            mHandlerCallbacks.remove(id)
            if (mHandlerCallbacks.isEmpty()) mHandler.post(EMPTY_RUNNABLE)
            return true
        }
        return false
    }

    fun hasPendingCallbacks(): Boolean {
        return mHandlerCallbacks.size > 0
    }

    fun removeAllCallbacks() {
        mHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val LOG_TAG = "Timer"
        private val EMPTY_RUNNABLE = Runnable {}
    }
}