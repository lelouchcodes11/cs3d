package android.app

// desktop: no app ops; picture-in-picture and the other ops are reported as not allowed
open class AppOpsManager {
    open fun checkOpNoThrow(op: String, uid: Int, packageName: String?): Int = MODE_IGNORED
    open fun unsafeCheckOpNoThrow(op: String, uid: Int, packageName: String?): Int = MODE_IGNORED
    open fun unsafeCheckOpRawNoThrow(op: String, uid: Int, packageName: String?): Int = MODE_IGNORED
    open fun noteOpNoThrow(op: String, uid: Int, packageName: String?): Int = MODE_IGNORED

    companion object {
        const val MODE_ALLOWED = 0
        const val MODE_IGNORED = 1
        const val MODE_ERRORED = 2
        const val MODE_DEFAULT = 3
        const val OPSTR_PICTURE_IN_PICTURE = "android:picture_in_picture"
    }
}
