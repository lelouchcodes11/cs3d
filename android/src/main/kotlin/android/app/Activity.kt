package android.app

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import com.lagradost.desktop.runtime.AndroidRuntime

open class Activity : ActivityBase(), Window.Callback {
    companion object {
        const val RESULT_CANCELED = 0
        const val RESULT_OK = -1
        const val RESULT_FIRST_USER = 1
        // desktop: Java statics inherited from Context are reachable through Activity on Android
        const val INPUT_METHOD_SERVICE = android.content.Context.INPUT_METHOD_SERVICE
        const val DEFAULT_KEYS_DISABLE = 0
    }

    private var mWindow: Window? = null
    private var mIntent: Intent? = Intent(Intent.ACTION_MAIN)
    private var mApplication: Application? = null
    @Volatile
    private var mFinished = false
    @Volatile
    private var mDestroyed = false
    private var mResultCode = RESULT_CANCELED
    private var mResultData: Intent? = null
    private var mTitle: CharSequence? = null
    private var mRequestedOrientation = -1
    private val mMainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** Desktop: attach the activity to its base context, called once by the host */
    fun attach(base: Context, application: Application?) {
        attachBaseContext(base)
        mApplication = application
        mWindow = Window(this)
        mWindow?.setCallback(this)
    }

    fun getApplication(): Application? = mApplication
    open fun getWindow(): Window = mWindow ?: Window(this).also { mWindow = it }
    open fun getWindowManager(): WindowManager? = null
    open fun getIntent(): Intent? = mIntent
    open fun setIntent(newIntent: Intent?) {
        mIntent = newIntent
    }

    open fun getComponentName(): ComponentName = ComponentName(packageName, javaClass.name)
    open fun getLocalClassName(): String = javaClass.simpleName
    open fun getCallingActivity(): ComponentName? = null
    open fun getCallingPackage(): String? = null
    open fun getParent(): Activity? = null
    open fun isChild(): Boolean = false
    open fun isTaskRoot(): Boolean = true
    open fun getTaskId(): Int = 1

    open fun setContentView(view: View?) = getWindow().setContentView(view)
    open fun setContentView(view: View?, params: ViewGroup.LayoutParams?) = getWindow().setContentView(view, params)
    open fun setContentView(layoutResID: Int) = setContentView(getLayoutInflater().inflate(layoutResID, null))
    open fun addContentView(view: View, params: ViewGroup.LayoutParams?) = getWindow().addContentView(view, params)
    open fun <T : View?> findViewById(id: Int): T = getWindow().findViewById(id) as T
    open fun <T : View> requireViewById(id: Int): T = findViewById<T?>(id) ?: throw IllegalArgumentException("ID does not reference a View inside this Activity")
    open fun getCurrentFocus(): View? = getWindow().getCurrentFocus()
    open fun getLayoutInflater(): LayoutInflater = LayoutInflater.from(this)
    open fun getMenuInflater(): MenuInflater = MenuInflater(this)

    open fun runOnUiThread(action: Runnable) {
        if (Looper.getMainLooper().isCurrentThread) action.run() else mMainHandler.post(action)
    }

    open fun getDisplay(): android.view.Display? = (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.getDefaultDisplay()

    // ------------------------------------------------------------------ lifecycle
    protected open fun onCreate(savedInstanceState: Bundle?) {}
    protected open fun onPostCreate(savedInstanceState: Bundle?) {}
    protected open fun onStart() {}
    protected open fun onRestart() {}
    protected open fun onResume() {}
    protected open fun onPostResume() {}
    protected open fun onPause() {}
    protected open fun onStop() {}
    protected open fun onDestroy() {}
    protected open fun onSaveInstanceState(outState: Bundle) {}
    protected open fun onRestoreInstanceState(savedInstanceState: Bundle) {}
    protected open fun onUserLeaveHint() {}
    open fun onConfigurationChanged(newConfig: Configuration) {}
    open fun onLowMemory() {}
    open fun onTrimMemory(level: Int) {}
    open fun onAttachedToWindow() {}
    open fun onDetachedFromWindow() {}
    open fun onWindowFocusChanged(hasFocus: Boolean) {}
    open fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration?) {}
    open fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {}
    protected open fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {}
    open fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {}

    /** Desktop host lifecycle drivers */
    fun performCreate(savedInstanceState: Bundle?) {
        onCreate(savedInstanceState)
        onPostCreate(savedInstanceState)
    }

    private var mResumed = false
    private var mStopped = true

    fun performStart() {
        mStopped = false
        onStart()
    }

    fun performResume() {
        mResumed = true
        onResume()
        onPostResume()
    }

    fun performPause() {
        if (!mResumed) return
        mResumed = false
        onPause()
    }

    fun performStop() {
        if (mStopped) return
        mStopped = true
        onStop()
    }

    /** Desktop host: bring a paused or stopped activity back to the resumed state */
    fun performRestartIfStopped() {
        if (mStopped) {
            onRestart()
            performStart()
        }
        if (!mResumed) performResume()
    }

    fun resultCodeForHost(): Int = mResultCode
    fun resultDataForHost(): Intent? = mResultData
    fun performDestroy() {
        mDestroyed = true
        onDestroy()
    }

    fun performNewIntent(intent: Intent?) {
        mIntent = intent
        if (intent != null) {
            onNewIntent(intent)
        }
    }

    fun dispatchActivityResult(requestCode: Int, resultCode: Int, data: Intent?) = onActivityResult(requestCode, resultCode, data)

    open fun isFinishing(): Boolean = mFinished
    open fun isDestroyed(): Boolean = mDestroyed
    open fun isChangingConfigurations(): Boolean = false
    open fun finish() {
        mFinished = true
        AndroidRuntime.host.finishActivity(this)
    }

    open fun finishAffinity() = finish()
    open fun finishAndRemoveTask() = finish()
    open fun recreate() {}
    open fun moveTaskToBack(nonRoot: Boolean): Boolean = false

    // AOSP default: an activity with nothing else to handle back finishes
    open fun onBackPressed() = finish()
    open fun onCreateView(name: String, context: Context, attrs: android.util.AttributeSet): View? = null
    open fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = false
    open fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = false
    open fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean = false
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val decor = getWindow().getDecorView()
        if (decor.dispatchKeyEvent(event)) return true
        return if (event.action == KeyEvent.ACTION_DOWN) onKeyDown(event.keyCode, event) else onKeyUp(event.keyCode, event)
    }

    open fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean = false
    open fun onTouchEvent(event: android.view.MotionEvent): Boolean = false

    // ------------------------------------------------------------------ results / activities
    fun setResult(resultCode: Int) {
        mResultCode = resultCode
    }

    fun setResult(resultCode: Int, data: Intent?) {
        mResultCode = resultCode
        mResultData = data
    }

    open fun startActivityForResult(intent: Intent, requestCode: Int) = startActivityForResult(intent, requestCode, null)
    open fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        intent.putExtra("desktop.requestCode", requestCode)
        intent.putExtra("desktop.requestActivity", System.identityHashCode(this))
        startActivity(intent, options)
    }

    open fun requestPermissions(permissions: Array<String>, requestCode: Int) {
        runOnUiThread {
            onRequestPermissionsResult(requestCode, permissions, IntArray(permissions.size) { PackageManager.PERMISSION_GRANTED })
        }
    }

    open fun shouldShowRequestPermissionRationale(permission: String): Boolean = false
    open fun setRequestedOrientation(requestedOrientation: Int) {
        mRequestedOrientation = requestedOrientation
    }

    open fun getRequestedOrientation(): Int = mRequestedOrientation
    open fun setTitle(title: CharSequence?) {
        mTitle = title
    }

    open fun setTitle(titleId: Int) = setTitle(getText(titleId))
    open fun getTitle(): CharSequence? = mTitle
    open fun setTitleColor(textColor: Int) {}
    open fun overridePendingTransition(enterAnim: Int, exitAnim: Int) {}
    open fun invalidateOptionsMenu() {}
    open fun onCreateOptionsMenu(menu: android.view.Menu): Boolean = true
    open fun onOptionsItemSelected(item: android.view.MenuItem): Boolean = false
    open fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean = true
    open fun isInPictureInPictureMode(): Boolean = false
    open fun isInMultiWindowMode(): Boolean = false
    open fun enterPictureInPictureMode() {}
    open fun enterPictureInPictureMode(params: PictureInPictureParams?): Boolean = false
    open fun setPictureInPictureParams(params: Any?) {}
    open fun setShowWhenLocked(showWhenLocked: Boolean) {}
    open fun setTurnScreenOn(turnScreenOn: Boolean) {}
    open fun hasWindowFocus(): Boolean = true
    open fun registerActivityLifecycleCallbacks(callback: Application.ActivityLifecycleCallbacks) {
        mApplication?.registerActivityLifecycleCallbacks(callback)
    }

    open fun unregisterActivityLifecycleCallbacks(callback: Application.ActivityLifecycleCallbacks) {
        mApplication?.unregisterActivityLifecycleCallbacks(callback)
    }

    open fun getFragmentManager(): Any? = null
    open fun getVolumeControlStream(): Int = 3
    open fun setVolumeControlStream(streamType: Int) {}
}

open class Application : ContextWrapper(null) {
    interface ActivityLifecycleCallbacks {
        fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?)
        fun onActivityStarted(activity: Activity)
        fun onActivityResumed(activity: Activity)
        fun onActivityPaused(activity: Activity)
        fun onActivityStopped(activity: Activity)
        fun onActivitySaveInstanceState(activity: Activity, outState: Bundle)
        fun onActivityDestroyed(activity: Activity)
    }

    private val callbacks = ArrayList<ActivityLifecycleCallbacks>()

    /** Desktop: attach the base context */
    fun attach(base: Context) = attachBaseContext(base)

    open fun onCreate() {}
    open fun onTerminate() {}
    open fun onConfigurationChanged(newConfig: Configuration) {}
    open fun onLowMemory() {}
    open fun onTrimMemory(level: Int) {}

    /** Desktop: told when a callback was registered (a plugin loaded while the app is already open) */
    @Volatile
    var onLifecycleCallbackRegistered: ((ActivityLifecycleCallbacks) -> Unit)? = null

    open fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        synchronized(callbacks) { callbacks.add(callback) }
        onLifecycleCallbackRegistered?.invoke(callback)
    }

    open fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        synchronized(callbacks) { callbacks.remove(callback) }
    }

    fun lifecycleCallbacks(): List<ActivityLifecycleCallbacks> = synchronized(callbacks) { callbacks.toList() }

    companion object {
        @JvmStatic
        fun getProcessName(): String = AndroidRuntime.PACKAGE_NAME
    }
}

