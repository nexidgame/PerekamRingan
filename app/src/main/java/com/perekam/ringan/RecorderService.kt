package com.perekam.ringan

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val ACTION_START = "com.perekam.ringan.START"
const val ACTION_STOP = "com.perekam.ringan.STOP"
const val EXTRA_CODE = "code"
const val EXTRA_DATA = "data"
const val EXTRA_W = "w"
const val EXTRA_H = "h"
const val EXTRA_DPI = "dpi"
const val EXTRA_SIDE = "side"
const val EXTRA_FPS = "fps"
const val EXTRA_MBPS = "mbps"
const val EXTRA_MIC = "mic"

class RecorderService : Service() {

    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_STOP -> stopRecording()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        val micOk = intent.getBooleanExtra(EXTRA_MIC, false) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

        // Android 14+: foreground service bertipe mediaProjection harus aktif
        // sebelum getMediaProjection() dipanggil.
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (micOk) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        createChannel()
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)

        if (recorder != null) return

        try {
            val code = intent.getIntExtra(EXTRA_CODE, 0)
            val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java)
                ?: error("Tidak ada data izin")

            val mpm = getSystemService(MediaProjectionManager::class.java)
            val proj = mpm.getMediaProjection(code, data)
            projection = proj
            val cb = object : MediaProjection.Callback() {
                override fun onStop() {
                    main.post { stopRecording() }
                }
            }
            projectionCallback = cb
            proj.registerCallback(cb, main) // wajib sebelum createVirtualDisplay (Android 14+)

            val (vw, vh) = scaled(
                intent.getIntExtra(EXTRA_W, 720),
                intent.getIntExtra(EXTRA_H, 1280),
                intent.getIntExtra(EXTRA_SIDE, 720),
            )
            val fps = intent.getIntExtra(EXTRA_FPS, 30)
            val mbps = intent.getIntExtra(EXTRA_MBPS, 8)
            val dpi = intent.getIntExtra(EXTRA_DPI, 320)

            val name = "Rec_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Perekam")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val u = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Gagal membuat file")
            uri = u
            val fd = contentResolver.openFileDescriptor(u, "rw") ?: error("Gagal membuka file")
            pfd = fd

            val mr = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this)
            else @Suppress("DEPRECATION") MediaRecorder()
            recorder = mr
            if (micOk) mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264) // encoder hardware
            if (micOk) {
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mr.setAudioEncodingBitRate(128_000)
                mr.setAudioSamplingRate(44_100)
            }
            mr.setVideoSize(vw, vh)
            mr.setVideoFrameRate(fps)
            mr.setVideoEncodingBitRate(mbps * 1_000_000)
            mr.setOutputFile(fd.fileDescriptor)
            mr.prepare()

            display = proj.createVirtualDisplay(
                "perekam", vw, vh, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mr.surface, null, null,
            )
            mr.start()
            RecorderState.startedAt.value = SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            stopRecording()
        }
    }

    private fun stopRecording() {
        var ok = false
        recorder?.let { mr ->
            try { mr.stop(); ok = true } catch (_: RuntimeException) { }
            try { mr.reset() } catch (_: Exception) { }
            mr.release()
        }
        recorder = null

        display?.release()
        display = null

        projection?.let { p ->
            projectionCallback?.let { p.unregisterCallback(it) }
            p.stop()
        }
        projection = null
        projectionCallback = null

        try { pfd?.close() } catch (_: Exception) { }
        pfd = null

        uri?.let { u ->
            if (ok) {
                val v = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(u, v, null, null)
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            } else {
                contentResolver.delete(u, null, null)
                Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show()
            }
        }
        uri = null

        RecorderState.startedAt.value = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (recorder != null) stopRecording()
        super.onDestroy()
    }

    /** Skala ke sisi pendek [side], dibulatkan ke kelipatan 16 agar aman bagi encoder. */
    private fun scaled(w: Int, h: Int, side: Int): Pair<Int, Int> {
        val shortSide = min(w, h)
        val f = if (side in 1 until shortSide) side.toFloat() / shortSide else 1f
        fun align(v: Int) = max(16, (v * f / 16f).roundToInt() * 16)
        return align(w) to align(h)
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rec)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setContentIntent(openPi)
            .addAction(0, getString(R.string.stop), stopPi)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "rec"
        private const val NOTIF_ID = 1
    }
}
