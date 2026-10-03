package android.os

/**
 * Schedule a countdown until a time in the future, with regular notifications on intervals along the way.
 */
abstract class CountDownTimer(
    val millisInFuture: Long,
    val countDownInterval: Long
) {
    private var isCancelled = false
    private val handler = Handler(Looper.getMainLooper())
    private var stopTimeInFuture: Long = 0

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (isCancelled) return
            val millisLeft = stopTimeInFuture - SystemClock.elapsedRealtime()
            if (millisLeft <= 0) {
                onFinish()
            } else {
                val lastTickStart = SystemClock.elapsedRealtime()
                onTick(millisLeft)
                var delay = lastTickStart + countDownInterval - SystemClock.elapsedRealtime()
                while (delay < 0) delay += countDownInterval
                handler.postDelayed(this, delay)
            }
        }
    }

    @Synchronized
    fun cancel() {
        isCancelled = true
        handler.removeCallbacks(tickRunnable)
    }

    @Synchronized
    fun start(): CountDownTimer {
        isCancelled = false
        if (millisInFuture <= 0) {
            onFinish()
            return this
        }
        stopTimeInFuture = SystemClock.elapsedRealtime() + millisInFuture
        handler.post(tickRunnable)
        return this
    }

    abstract fun onTick(millisUntilFinished: Long)
    abstract fun onFinish()
}
