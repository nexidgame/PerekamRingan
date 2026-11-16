package com.perekam.ringan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val data = r.data
            if (r.resultCode == RESULT_OK && data != null) beginService(r.resultCode, data)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            askProjection() // lanjut walau izin ditolak; audio otomatis dimatikan jika ditolak
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme { MainScreen(onStart = ::onStartClicked, onStop = ::onStopClicked) }
        }
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun onStartClicked() {
        val s = Prefs.load(this)
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            need += Manifest.permission.POST_NOTIFICATIONS
        }
        if (s.audioOn && !granted(Manifest.permission.RECORD_AUDIO)) {
            need += Manifest.permission.RECORD_AUDIO
        }
        if (need.isEmpty()) askProjection() else permissionLauncher.launch(need.toTypedArray())
    }

    private fun askProjection() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun beginService(code: Int, data: Intent) {
        val s = Prefs.load(this)
        val b = windowManager.maximumWindowMetrics.bounds
        val i = Intent(this, RecorderService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_CODE, code)
            .putExtra(EXTRA_DATA, data)
            .putExtra(EXTRA_W, b.width())
            .putExtra(EXTRA_H, b.height())
            .putExtra(EXTRA_DPI, resources.configuration.densityDpi)
            .putExtra(EXTRA_SIDE, s.shortSide)
            .putExtra(EXTRA_FPS, s.fps)
            .putExtra(EXTRA_MBPS, s.bitrateMbps)
            .putExtra(EXTRA_CODEC, s.codec)
            .putExtra(EXTRA_AUDIO, s.audioOn)
            .putExtra(EXTRA_ASRC, s.audioSource)
            .putExtra(EXTRA_ARATE, s.sampleRate)
            .putExtra(EXTRA_ACH, s.channels)
            .putExtra(EXTRA_ACODEC, s.audioCodec)
        ContextCompat.startForegroundService(this, i)
        moveTaskToBack(true) // langsung kembali ke game/aplikasi sebelumnya
    }

    private fun onStopClicked() {
        startService(Intent(this, RecorderService::class.java).setAction(ACTION_STOP))
    }
}
