package com.perekam.ringan

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** shortSide = sisi pendek video dalam piksel (0 = resolusi asli layar). */
data class RecSettings(
    val shortSide: Int = 720,
    val fps: Int = 30,
    val bitrateMbps: Int = 8,
    val mic: Boolean = false,
)

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(c: Context): RecSettings {
        val s = sp(c)
        return RecSettings(
            shortSide = s.getInt("side", 720),
            fps = s.getInt("fps", 30),
            bitrateMbps = s.getInt("mbps", 8),
            mic = s.getBoolean("mic", false),
        )
    }

    fun save(c: Context, r: RecSettings) {
        sp(c).edit()
            .putInt("side", r.shortSide)
            .putInt("fps", r.fps)
            .putInt("mbps", r.bitrateMbps)
            .putBoolean("mic", r.mic)
            .apply()
    }
}

/** Status perekaman yang dibaca UI. 0 = tidak merekam, selain itu = waktu mulai (elapsedRealtime). */
object RecorderState {
    val startedAt = MutableStateFlow(0L)
}
