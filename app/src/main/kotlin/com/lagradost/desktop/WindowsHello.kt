package com.lagradost.desktop

import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Windows Hello (PIN, fingerprint, face) user verification through the WinRT UserConsentVerifier,
 * invoked with PowerShell so no native code is needed. Used for the app lock ("biometric") setting.
 */
object WindowsHello {
    private const val TAG = "WindowsHello"

    enum class Result { Verified, Canceled, NotAvailable, Failed }

    private const val AWAIT = "\$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { \$_.Name -eq 'AsTask' -and \$_.GetParameters().Count -eq 1 -and \$_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]; " +
            "function Await(\$op, [Type]\$t) { \$task = \$asTask.MakeGenericMethod(\$t).Invoke(\$null, @(\$op)); \$task.Wait(-1) | Out-Null; \$task.Result }; "

    private fun run(script: String, timeoutSeconds: Long): String? {
        if (!DesktopPlatform.isWindows) return null
        return try {
            val full = "Add-Type -AssemblyName System.Runtime.WindowsRuntime; " +
                    "[Windows.Security.Credentials.UI.UserConsentVerifier,Windows.Security.Credentials.UI,ContentType=WindowsRuntime] | Out-Null; " +
                    AWAIT + script
            val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", full)
                .redirectErrorStream(true).start()
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                return null
            }
            p.inputStream.bufferedReader().readText().trim().lines().lastOrNull()?.trim()
        } catch (t: Throwable) {
            Log.w(TAG, "PowerShell failed: $t")
            null
        }
    }

    @Volatile
    private var availability: Boolean? = null

    /** true if Windows Hello (or a PIN) is configured for the user */
    @Synchronized
    fun isAvailable(): Boolean {
        availability?.let { return it }
        val out = run(
            "Await ([Windows.Security.Credentials.UI.UserConsentVerifier]::CheckAvailabilityAsync()) " +
                    "([Windows.Security.Credentials.UI.UserConsentVerifierAvailability])", 20
        )
        val available = out == "Available"
        availability = available
        return available
    }

    /** Blocking: shows the Windows Hello prompt with [message] */
    fun verify(message: String): Result {
        val escaped = message.replace("'", "''")
        val out = run(
            "Await ([Windows.Security.Credentials.UI.UserConsentVerifier]::RequestVerificationAsync('$escaped')) " +
                    "([Windows.Security.Credentials.UI.UserConsentVerificationResult])", 300
        ) ?: return Result.NotAvailable
        return when (out) {
            "Verified" -> Result.Verified
            "Canceled" -> Result.Canceled
            "DeviceNotPresent", "NotConfiguredForUser", "DisabledByPolicy" -> Result.NotAvailable
            else -> Result.Failed
        }
    }
}
