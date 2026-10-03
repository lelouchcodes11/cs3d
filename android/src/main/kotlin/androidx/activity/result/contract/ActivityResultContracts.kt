package androidx.activity.result.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult

abstract class ActivityResultContract<I, O> {
    abstract fun createIntent(context: Context, input: I): Intent
    abstract fun parseResult(resultCode: Int, intent: Intent?): O
    open fun getSynchronousResult(context: Context, input: I): SynchronousResult<O>? = null

    class SynchronousResult<T>(val value: T)
}

class ActivityResultContracts private constructor() {
    open class StartActivityForResult : ActivityResultContract<Intent, ActivityResult>() {
        override fun createIntent(context: Context, input: Intent): Intent = input
        override fun parseResult(resultCode: Int, intent: Intent?): ActivityResult = ActivityResult(resultCode, intent)
    }

    open class OpenDocument : ActivityResultContract<Array<String>, Uri?>() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).putExtra(Intent.EXTRA_MIME_TYPES, input).setType("*/*")

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            intent.takeIf { resultCode == Activity.RESULT_OK }?.data
    }

    open class OpenMultipleDocuments : ActivityResultContract<Array<String>, List<Uri>>() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).putExtra(Intent.EXTRA_MIME_TYPES, input).setType("*/*")

        override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> =
            listOfNotNull(intent.takeIf { resultCode == Activity.RESULT_OK }?.data)
    }

    open class GetContent : ActivityResultContract<String, Uri?>() {
        override fun createIntent(context: Context, input: String): Intent =
            Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType(input)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            intent.takeIf { resultCode == Activity.RESULT_OK }?.data
    }

    open class CreateDocument(val mimeType: String) : ActivityResultContract<String, Uri?>() {
        @Deprecated("Use CreateDocument(String)")
        constructor() : this("*/*")

        override fun createIntent(context: Context, input: String): Intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mimeType).putExtra(Intent.EXTRA_TITLE, input)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            intent.takeIf { resultCode == Activity.RESULT_OK }?.data
    }

    open class OpenDocumentTree : ActivityResultContract<Uri?, Uri?>() {
        override fun createIntent(context: Context, input: Uri?): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            intent.takeIf { resultCode == Activity.RESULT_OK }?.data
    }

    class RequestPermission : ActivityResultContract<String, Boolean>() {
        override fun createIntent(context: Context, input: String): Intent = Intent()
        override fun parseResult(resultCode: Int, intent: Intent?): Boolean = true
    }

    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>() {
        override fun createIntent(context: Context, input: Array<String>): Intent = Intent()
        override fun parseResult(resultCode: Int, intent: Intent?): Map<String, Boolean> = emptyMap()
    }
}
