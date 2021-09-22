package com.fsck.k9.power

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import com.fsck.k9.mail.power.PowerManager
import com.fsck.k9.mail.power.WakeLock
import java.util.concurrent.atomic.AtomicInteger
import timber.log.Timber
import android.os.PowerManager as SystemPowerManager
import android.os.PowerManager.WakeLock as SystemWakeLock

class TracingPowerManager private constructor(context: Context) : PowerManager {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as SystemPowerManager

    override fun newWakeLock(tag: String): WakeLock {
        return TracingWakeLock(SystemPowerManager.PARTIAL_WAKE_LOCK, tag)
    }

    fun newWakeLock(flags: Int, tag: String?): TracingWakeLock {
        return TracingWakeLock(flags, tag)
    }

    inner class TracingWakeLock(flags: Int, val tag: String?) : WakeLock {
        private val wakeLock: SystemWakeLock = powerManager.newWakeLock(flags, tag)
        private val id = wakeLockId.getAndIncrement()

        @Volatile
        private var startTime: Long? = null

        @Volatile
        private var timeout: Long? = null

        init {
            Timber.v("TracingWakeLock for tag %s / id %d: Create", tag, id)
        }

        override fun acquire(timeout: Long) {
            synchronized(wakeLock) {
                wakeLock.acquire(timeout)
            }

            Timber.v("TracingWakeLock for tag %s / id %d for %d ms: acquired", tag, id, timeout)

            if (startTime == null) {
                startTime = SystemClock.elapsedRealtime()
            }

            this.timeout = timeout
        }

        @SuppressLint("WakelockTimeout")
        override fun acquire() {
            synchronized(wakeLock) {
                wakeLock.acquire()
            }

            Timber.v("TracingWakeLock for tag %s / id %d: acquired with no timeout.", tag, id)

            if (startTime == null) {
                startTime = SystemClock.elapsedRealtime()
            }

            timeout = null
        }

        override fun setReferenceCounted(counted: Boolean) {
            synchronized(wakeLock) {
                wakeLock.setReferenceCounted(counted)
            }
        }

        override fun release() {
            val startTime = this.startTime
            if (startTime != null) {
                val endTime = SystemClock.elapsedRealtime()

                Timber.v(
                    "TracingWakeLock for tag %s / id %d: releasing after %d ms, timeout = %d ms",
                    tag, id, endTime - startTime, timeout
                )
            } else {
                Timber.v("TracingWakeLock for tag %s / id %d, timeout = %d ms: releasing", tag, id, timeout)
            }

            synchronized(wakeLock) {
                wakeLock.release()
            }

            this.startTime = null
        }
    }

    companion object {
        private val wakeLockId = AtomicInteger(0)
        private var tracingPowerManager: TracingPowerManager? = null

        @JvmStatic
        @Synchronized
        fun getPowerManager(context: Context): TracingPowerManager {
            return tracingPowerManager ?: createPowerManager(context.applicationContext).also {
                tracingPowerManager = it
            }
        }

        private fun createPowerManager(appContext: Context): TracingPowerManager {
            Timber.v("Creating TracingPowerManager")
            return TracingPowerManager(appContext)
        }
    }
}
