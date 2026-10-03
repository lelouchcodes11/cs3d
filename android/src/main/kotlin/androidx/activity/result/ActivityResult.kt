package androidx.activity.result

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.UiHost
import java.util.concurrent.atomic.AtomicInteger

class ActivityResult(val resultCode: Int, val data: Intent?) {
    override fun toString(): String = "ActivityResult{resultCode=$resultCode, data=$data}"
}

fun interface ActivityResultCallback<O> {
    fun onActivityResult(result: O)
}

abstract class ActivityResultLauncher<I> {
    fun launch(input: I) = launch(input, null)
    abstract fun launch(input: I, options: ActivityOptionsCompat?)
    abstract fun unregister()
    abstract fun getContract(): ActivityResultContract<I, *>
}

interface ActivityResultCaller {
    fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        callback: ActivityResultCallback<O>
    ): ActivityResultLauncher<I>

    fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        registry: ActivityResultRegistry,
        callback: ActivityResultCallback<O>
    ): ActivityResultLauncher<I> = registry.register("caller_" + ActivityResultRegistry.keys.incrementAndGet(), contract, callback)
}

/**
 * Desktop registry: document contracts open native file dialogs through the UI host, other
 * contracts start the intent and report RESULT_OK/RESULT_CANCELED.
 */
open class ActivityResultRegistry(private val context: () -> Context?) {
    companion object {
        internal val keys = AtomicInteger()
    }

    open fun <I, O> register(
        key: String,
        contract: ActivityResultContract<I, O>,
        callback: ActivityResultCallback<O>
    ): ActivityResultLauncher<I> {
        return object : ActivityResultLauncher<I>() {
            private var registered = true

            override fun launch(input: I, options: ActivityOptionsCompat?) {
                if (!registered) return
                val ctx = context() ?: AndroidRuntime.context
                dispatch(ctx, contract, input) { result -> callback.onActivityResult(result) }
            }

            override fun unregister() {
                registered = false
            }

            override fun getContract(): ActivityResultContract<I, *> = contract
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <I, O> dispatch(context: Context, contract: ActivityResultContract<I, O>, input: I, done: (O) -> Unit) {
        val host = AndroidRuntime.host
        fun deliver(uri: Uri?) {
            android.os.Handler(context.mainLooper).post {
                done(contract.parseResult(if (uri != null) Activity.RESULT_OK else Activity.RESULT_CANCELED, uri?.let { Intent().setData(it) }))
            }
        }
        when (contract) {
            is ActivityResultContracts.OpenDocument -> host.chooseFile(UiHost.FileChooser.OPEN, null, input as Array<String>?) { deliver(it?.let(Uri::fromFile)) }
            is ActivityResultContracts.GetContent -> host.chooseFile(UiHost.FileChooser.OPEN, null, arrayOf(input as String)) { deliver(it?.let(Uri::fromFile)) }
            is ActivityResultContracts.CreateDocument -> host.chooseFile(UiHost.FileChooser.SAVE, input as String?, arrayOf(contract.mimeType)) { deliver(it?.let(Uri::fromFile)) }
            is ActivityResultContracts.OpenDocumentTree -> host.chooseFile(UiHost.FileChooser.DIRECTORY, null, null) { deliver(it?.let(Uri::fromFile)) }
            is ActivityResultContracts.OpenMultipleDocuments -> host.chooseFile(UiHost.FileChooser.OPEN, null, input as Array<String>?) { file ->
                android.os.Handler(context.mainLooper).post {
                    done((if (file != null) listOf(Uri.fromFile(file)) else emptyList<Uri>()) as O)
                }
            }
            is ActivityResultContracts.RequestPermission -> android.os.Handler(context.mainLooper).post { done(true as O) }
            is ActivityResultContracts.RequestMultiplePermissions -> android.os.Handler(context.mainLooper).post {
                done((input as Array<String>).associateWith { true } as O)
            }
            else -> {
                val intent = contract.createIntent(context, input)
                val handled = try {
                    host.startActivity(intent)
                } catch (_: Throwable) {
                    false
                }
                android.os.Handler(context.mainLooper).post {
                    done(contract.parseResult(if (handled) Activity.RESULT_OK else Activity.RESULT_CANCELED, null))
                }
            }
        }
    }
}

interface ActivityResultRegistryOwner {
    val activityResultRegistry: ActivityResultRegistry
}
