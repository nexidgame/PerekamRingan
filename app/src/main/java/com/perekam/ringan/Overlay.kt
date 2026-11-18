package com.perekam.ringan

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

const val ACTION_OVERLAY_OFF = "com.perekam.ringan.OVERLAY_OFF"

object OverlayState {
    /** Fitur tombol melayang diaktifkan pengguna. */
    val enabled = MutableStateFlow(false)

    /** true = tombol disembunyikan (sedang meminta izin, merekam, atau menyimpan). */
    val busy = MutableStateFlow(false)

    @Volatile var launched = false
}

/** Memulai RecorderService dari activity (dipakai MainActivity dan QuickStartActivity). */
fun Activity.startRecordingService(code: Int, data: Intent) {
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
        .putExtra(EXTRA_ABR, s.audioBitrate)
        .putExtra(EXTRA_GVOL, s.gameVolume)
        .putExtra(EXTRA_MVOL, s.micVolume)
    ContextCompat.startForegroundService(this, i)
}

/** Tombol rekam melayang di tengah layar. Hilang saat merekam supaya tidak ikut terekam. */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var view: View? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        scope.launch {
            OverlayState.busy.collect { busy -> if (busy) hide() else show() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_OVERLAY_OFF) {
            OverlayState.enabled.value = false
            Prefs.setFloating(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        hide()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun show() {
        if (view != null || !Settings.canDrawOverlays(this)) return
        val size = dp(15)
        val v = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(150, 229, 57, 53))
                setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(4).toFloat()
            contentDescription = "Mulai rekam"
        }
        lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        lp.x = startX + dx.roundToInt()
                        lp.y = startY + dy.roundToInt()
                        try { wm.updateViewLayout(v, lp) } catch (ex: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap()
                    true
                }
                else -> false
            }
        }
        try {
            wm.addView(v, lp)
            view = v
        } catch (e: Exception) {
            // izin overlay dicabut atau ditolak sistem
        }
    }

    private fun hide() {
        view?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        view = null
    }

    private fun onTap() {
        OverlayState.launched = false
        try {
            startActivity(
                Intent(this, QuickStartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            OverlayState.busy.value = true
            // Jika activity ternyata tidak terbuka, munculkan lagi tombolnya.
            handler.postDelayed({
                if (!OverlayState.launched) OverlayState.busy.value = false
            }, 2500)
        } catch (e: Exception) {
            OverlayState.busy.value = false
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Tombol melayang", NotificationManager.IMPORTANCE_MIN),
        )
        val off = PendingIntent.getService(
            this, 2,
            Intent(this, OverlayService::class.java).setAction(ACTION_OVERLAY_OFF),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rec)
            .setContentTitle("Tombol rekam melayang aktif")
            .setContentText("Ketuk tombol merah di layar untuk mulai merekam")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .addAction(0, "Matikan", off)
            .build()
    }

    companion object {
        private const val CHANNEL = "overlay"
        private const val NOTIF_ID = 2
    }
}

/** Activity transparan: meminta izin rekam layar, memulai rekaman, lalu menutup diri. */
class QuickStartActivity : ComponentActivity() {

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val data = r.data
            if (r.resultCode == RESULT_OK && data != null) {
                startRecordingService(r.resultCode, data) // tombol tetap tersembunyi sampai rekaman selesai
            } else {
                OverlayState.busy.value = false
            }
            finish()
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            askProjection()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OverlayState.launched = true
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

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun askProjection() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
}
