package com.isygold.procinsight.monitor

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ShizukuManager(private val context: Context) {

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _statusMessage = MutableStateFlow("Shizuku not connected")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

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
                    _isAvailable.value = true
                    _statusMessage.value = "Shizuku connected"
                    return true
                }
            } catch (_: Exception) { }
        }
        _isAvailable.value = false
        _statusMessage.value = "Shizuku not installed or API unavailable"
        return false
    }

    companion object {
        fun getShellCommand(vararg cmd: String): Process? {
            for (className in listOf("rikka.shizuku.Shizuku", "moe.shizuku.api.Shizuku")) {
                try {
                    val clazz = Class.forName(className)
                    val method = clazz.getMethod("newProcess", Array<String>::class.java)
                    return method.invoke(null, arrayOf<Any?>(cmd)) as? Process
                } catch (_: Exception) { }
            }
            return null
        }

        fun execute(cmd: String): String? {
            return try {
                val proc = getShellCommand("sh", "-c", cmd) ?: return null
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                output
            } catch (_: Exception) {
                null
            }
        }
    }
}
