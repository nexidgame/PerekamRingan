package com.perekam.ringan

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

const val SRC_MIC = 0
const val SRC_RAW = 1
const val SRC_PERF = 2
const val SRC_INTERNAL = 3
const val SRC_INTERNAL_MIC = 4

/** shortSide = sisi pendek video dalam piksel (0 = resolusi asli layar). */
data class RecSettings(
    val shortSide: Int = 720,
    val fps: Int = 30,
    val bitrateMbps: Int = 8,
    val codec: Int = 264,        // 264 = H.264, 265 = H.265
    val audioOn: Boolean = false,
    val audioSource: Int = SRC_MIC,
    val sampleRate: Int = 44100, // 44100 / 48000
    val channels: Int = 1,       // 1 = mono, 2 = stereo
    val audioCodec: Int = 0,     // 0 = AAC, 1 = AAC_ELD, 2 = HE-AAC
    val gameVolume: Int = 100,   // persen, khusus Internal + MIC
    val micVolume: Int = 100,    // persen, khusus Internal + MIC
)

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(c: Context): RecSettings {
        val s = sp(c)
        return RecSettings(
            shortSide = s.getInt("side", 720),
            fps = s.getInt("fps", 30),
            bitrateMbps = s.getInt("mbps", 8),
            codec = s.getInt("codec", 264),
            audioOn = s.getBoolean("audio_on", false),
            audioSource = s.getInt("audio_src", SRC_MIC),
            sampleRate = s.getInt("audio_rate", 44100),
            channels = s.getInt("audio_ch", 1),
            audioCodec = s.getInt("audio_codec", 0),
            gameVolume = s.getInt("vol_game", 100),
            micVolume = s.getInt("vol_mic", 100),
        )
    }

    fun floating(c: Context): Boolean = sp(c).getBoolean("floating", false)

    fun setFloating(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("floating", on).apply()
    }

    fun save(c: Context, r: RecSettings) {
        sp(c).edit()
            .putInt("side", r.shortSide)
            .putInt("fps", r.fps)
            .putInt("mbps", r.bitrateMbps)
            .putInt("codec", r.codec)
            .putBoolean("audio_on", r.audioOn)
            .putInt("audio_src", r.audioSource)
            .putInt("audio_rate", r.sampleRate)
            .putInt("audio_ch", r.channels)
            .putInt("audio_codec", r.audioCodec)
            .putInt("vol_game", r.gameVolume)
            .putInt("vol_mic", r.micVolume)
            .apply()
    }
}

/** Status perekaman yang dibaca UI. 0 = tidak merekam, selain itu = waktu mulai (elapsedRealtime). */
object RecorderState {
    val startedAt = MutableStateFlow(0L)
}
