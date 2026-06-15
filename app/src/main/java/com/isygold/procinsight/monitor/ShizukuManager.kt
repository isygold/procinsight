package com.isygold.procinsight.monitor

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ShizukuState(
    val available: Boolean = false,
    val message: String = "Tap to check Shizuku status",
    val detail: String = ""
)

class ShizukuManager(private val context: Context) {

    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    /** Check if Shizuku service is running and pingable. Returns true only when ready. */
    fun checkAvailability(): Boolean {
        for (className in listOf("rikka.shizuku.Shizuku", "moe.shizuku.api.Shizuku")) {
            try {
                val clazz = Class.forName(className)
                val method = clazz.getMethod("ping")
                val result = method.invoke(null)
                val available = when (result) {
                    is Int -> result > 0
                    is Boolean -> result
                    else -> false
                }
                if (available) {
                    _state.value = ShizukuState(true, "Shizuku connected", "Using $className")
                    return true
                }
            } catch (e: ClassNotFoundException) {
                _state.value = ShizukuState(false, "Shizuku library not found in APK", className)
                return false
            } catch (e: NoSuchMethodException) {
                _state.value = ShizukuState(false, "Shizuku API incompatible: ${e.message}", className)
            } catch (e: Exception) {
                _state.value = ShizukuState(false, "Shizuku not running: ${e.message}", className)
            }
        }
        _state.value = ShizukuState(false, "Shizuku not connected", "Install Shizuku from shizuku.rikka.app, start the service, and grant authorization to ProcInsight")
        return false
    }

    companion object {
        /**
         * Run a shell command via Shizuku's newProcess.
         *
         * In Shizuku API v13, newProcess is private and takes 3 params:
         *   newProcess(String[] cmd, String[] envp, String dir)
         * Returns ShizukuRemoteProcess (not java.lang.Process).
         * We use getDeclaredMethod + setAccessible to invoke it.
         */
        fun execute(cmd: String): String? {
            for (className in listOf("rikka.shizuku.Shizuku", "moe.shizuku.api.Shizuku")) {
                try {
                    val clazz = Class.forName(className)
                    // newProcess is private in Shizuku API v13+
                    val method = clazz.getDeclaredMethod(
                        "newProcess",
                        Array<String>::class.java,   // cmd
                        Array<String>::class.java,   // envp
                        String::class.java            // dir
                    )
                    method.isAccessible = true

                    val remoteProcess = method.invoke(null, arrayOf("sh", "-c", cmd), null, null)
                    // ShizukuRemoteProcess is part of dev.rikka.shizuku:api
                    val inputStream = remoteProcess::class.java.getMethod("getInputStream").invoke(remoteProcess) as? java.io.InputStream ?: continue
                    val output = inputStream.bufferedReader().readText()
                    // Destroy the remote process
                    try { remoteProcess::class.java.getMethod("destroy").invoke(remoteProcess) } catch (_: Exception) { }
                    if (output.isNotEmpty()) return output
                } catch (_: Exception) { }
            }
            return null
        }
    }
}
