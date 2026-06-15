package com.isygold.procinsight.monitor

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ShizukuState(
    val available: Boolean = false,
    val authorized: Boolean = false,
    val message: String = "Tap to check Shizuku status",
    val detail: String = ""
)

class ShizukuManager(private val context: Context) {

    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private var checkedOnce = false

    /** 
     * Full availability check: ping (service running) + getUid (authorized).  
     * Returns true only when BOTH service is running AND app is authorized.
     */
    fun checkAvailability(): Boolean {
        for (className in listOf("rikka.shizuku.Shizuku", "moe.shizuku.api.Shizuku")) {
            try {
                val clazz = Class.forName(className)

                // 1) Ping — is the Shizuku service alive?
                val pingMethod = clazz.getMethod("ping")
                val pingResult = pingMethod.invoke(null)
                val serviceAlive = when (pingResult) {
                    is Int -> pingResult > 0
                    is Boolean -> pingResult
                    else -> false
                }
                if (!serviceAlive) {
                    _state.value = ShizukuState(false, false, "Shizuku service not running", "Start the service in the Shizuku app")
                    return false
                }

                // 2) getUid — is this app authorized?
                val uidMethod = try { clazz.getMethod("getUid") } catch (_: NoSuchMethodException) { null }
                val authorized = if (uidMethod != null) {
                    val uid = uidMethod.invoke(null)
                    when (uid) {
                        is Int -> uid >= 0  // -1 = not authorized
                        else -> false
                    }
                } else {
                    // Can't check authorization, assume yes
                    true
                }

                val status = if (authorized) "Shizuku connected & authorized" else "Shizuku connected — NOT authorized"
                val detail = if (authorized) "Using $className"
                    else "Open Shizuku app and grant authorization to ProcInsight"
                _state.value = ShizukuState(true, authorized, status, detail)

                if (!authorized) checkedOnce = true
                return authorized
            } catch (e: ClassNotFoundException) {
                _state.value = ShizukuState(false, false, "Shizuku library not found in APK", className)
                return false
            } catch (e: NoSuchMethodException) {
                _state.value = ShizukuState(false, false, "Shizuku API incompatible: ${e.message}", className)
            } catch (e: Exception) {
                _state.value = ShizukuState(false, false, "Shizuku check failed: ${e.message}", className)
            }
        }
        _state.value = ShizukuState(false, false, "Shizuku not connected", "Install Shizuku from shizuku.rikka.app")
        return false
    }

    /** Run a test command via Shizuku to verify execution works. Returns output or error. */
    fun testExecution(): String {
        return try {
            val result = execute("echo 'shizuku_ok' && id -u 2>/dev/null && ps 2>/dev/null | head -3")
            if (result != null) "OK. uid=$(id output). ps header: ${result.lines().getOrNull(0)?.take(50)}"
            else "FAIL: execute returned null"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    fun openShizukuApp() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.manager")
            if (intent != null) {
                context.startActivity(intent)
            } else {
                val intent2 = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app"))
                intent2.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent2)
            }
        } catch (_: Exception) { }
    }

    companion object {
        /**
         * Run a shell command via Shizuku's newProcess.
         * newProcess is private in v13+: uses getDeclaredMethod + setAccessible.
         * Signature: newProcess(String[] cmd, String[] envp, String dir)
         */
        fun execute(cmd: String): String? {
            for (className in listOf("rikka.shizuku.Shizuku", "moe.shizuku.api.Shizuku")) {
                try {
                    val clazz = Class.forName(className)
                    val method = clazz.getDeclaredMethod(
                        "newProcess",
                        Array<String>::class.java,
                        Array<String>::class.java,
                        String::class.java
                    )
                    method.isAccessible = true

                    val remoteProcess = method.invoke(null, arrayOf("sh", "-c", cmd), null, null)
                    val inputStream = remoteProcess::class.java.getMethod("getInputStream").invoke(remoteProcess) as? java.io.InputStream ?: continue
                    val output = inputStream.bufferedReader().readText()
                    try { remoteProcess::class.java.getMethod("destroy").invoke(remoteProcess) } catch (_: Exception) { }
                    if (output.isNotEmpty()) return output
                } catch (_: Exception) { }
            }
            return null
        }
    }
}
