package com.lagradost.desktop.runtime

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal ActivityManager service lifecycle: one instance per service class, created on the first
 * start, onStartCommand for every start, onDestroy on stopSelf/stopService. Callbacks run on the
 * main thread like on Android.
 */
object Services {
    private const val TAG = "Services"
    private val running = ConcurrentHashMap<String, Service>()
    private val startIds = AtomicInteger()
    private val main by lazy { Handler(Looper.getMainLooper()) }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    fun start(context: Context, intent: Intent): ComponentName? {
        val component = intent.component ?: return null
        val className = component.className
        val cls = try {
            Class.forName(className, true, context.classLoader)
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "Unknown service $className")
            return null
        }
        if (!Service::class.java.isAssignableFrom(cls)) return null
        runOnMain {
            try {
                var service = running[className]
                if (service == null) {
                    service = cls.getDeclaredConstructor().newInstance() as Service
                    service.attach(AndroidRuntime.context)
                    running[className] = service
                    Log.d(TAG, "Creating service $className")
                    service.onCreate()
                }
                // onCreate may already have stopped the service
                if (running[className] === service) {
                    service.onStartCommand(intent, 0, startIds.incrementAndGet())
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Service $className failed: ${Log.getStackTraceString(t)}")
                running.remove(className)
            }
        }
        return component
    }

    fun stop(intent: Intent): Boolean {
        val className = intent.component?.className ?: return false
        return stop(className)
    }

    fun stop(service: Service): Boolean = stop(service.javaClass.name)

    private fun stop(className: String): Boolean {
        val service = running.remove(className) ?: return false
        runOnMain {
            try {
                Log.d(TAG, "Destroying service $className")
                service.onDestroy()
            } catch (t: Throwable) {
                Log.e(TAG, "onDestroy of $className failed: ${Log.getStackTraceString(t)}")
            }
        }
        return true
    }

    fun isRunning(cls: Class<*>): Boolean = running.containsKey(cls.name)
}
