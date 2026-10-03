package androidx.biometric

import androidx.fragment.app.FragmentActivity
import com.lagradost.desktop.WindowsHello
import java.util.concurrent.Executor
import kotlin.concurrent.thread

/** androidx BiometricPrompt backed by Windows Hello */
class BiometricPrompt(
    private val activity: FragmentActivity,
    private val executor: Executor,
    private val callback: AuthenticationCallback,
) {
    companion object {
        const val ERROR_HW_UNAVAILABLE = 1
        const val ERROR_CANCELED = 5
        const val ERROR_USER_CANCELED = 10
        const val ERROR_NO_BIOMETRICS = 11
        const val ERROR_NEGATIVE_BUTTON = 13
        const val AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL = 1
    }

    abstract class AuthenticationCallback {
        open fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
        open fun onAuthenticationSucceeded(result: AuthenticationResult) {}
        open fun onAuthenticationFailed() {}
    }

    class AuthenticationResult internal constructor(val authenticationType: Int)

    class PromptInfo private constructor(
        val title: CharSequence,
        val subtitle: CharSequence?,
        val description: CharSequence?,
    ) {
        class Builder {
            private var title: CharSequence = ""
            private var subtitle: CharSequence? = null
            private var description: CharSequence? = null
            fun setTitle(title: CharSequence) = apply { this.title = title }
            fun setSubtitle(subtitle: CharSequence?) = apply { this.subtitle = subtitle }
            fun setDescription(description: CharSequence?) = apply { this.description = description }
            fun setNegativeButtonText(text: CharSequence) = this
            fun setAllowedAuthenticators(authenticators: Int) = this
            fun setConfirmationRequired(required: Boolean) = this
            @Deprecated("Use setAllowedAuthenticators")
            fun setDeviceCredentialAllowed(allowed: Boolean) = this
            fun build() = PromptInfo(title, subtitle, description)
        }
    }

    fun authenticate(info: PromptInfo) {
        thread(name = "windows-hello", isDaemon = true) {
            val message = listOfNotNull(info.title, info.description).joinToString("\n")
            when (WindowsHello.verify(message)) {
                WindowsHello.Result.Verified -> executor.execute {
                    callback.onAuthenticationSucceeded(AuthenticationResult(AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL))
                }
                WindowsHello.Result.Canceled -> executor.execute {
                    callback.onAuthenticationError(ERROR_USER_CANCELED, "Canceled")
                }
                WindowsHello.Result.NotAvailable -> executor.execute {
                    callback.onAuthenticationError(ERROR_HW_UNAVAILABLE, "Windows Hello is not available")
                }
                WindowsHello.Result.Failed -> executor.execute { callback.onAuthenticationFailed() }
            }
        }
    }

    fun cancelAuthentication() {}
}

class BiometricManager private constructor() {
    companion object {
        const val BIOMETRIC_SUCCESS = 0
        const val BIOMETRIC_STATUS_UNKNOWN = -1
        const val BIOMETRIC_ERROR_UNSUPPORTED = -2
        const val BIOMETRIC_ERROR_HW_UNAVAILABLE = 1
        const val BIOMETRIC_ERROR_NONE_ENROLLED = 11
        const val BIOMETRIC_ERROR_NO_HARDWARE = 12
        const val BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED = 15
        const val BIOMETRIC_ERROR_NOT_ENABLED_FOR_APPS = 21

        @JvmStatic
        fun from(context: android.content.Context) = BiometricManager()
    }

    @Deprecated("Use canAuthenticate(int)")
    fun canAuthenticate(): Int = canAuthenticate(Authenticators.BIOMETRIC_WEAK)

    object Authenticators {
        const val BIOMETRIC_STRONG = 0x000F
        const val BIOMETRIC_WEAK = 0x00FF
        const val DEVICE_CREDENTIAL = 0x8000
    }

    fun canAuthenticate(authenticators: Int): Int =
        if (WindowsHello.isAvailable()) BIOMETRIC_SUCCESS else BIOMETRIC_ERROR_NONE_ENROLLED
}
