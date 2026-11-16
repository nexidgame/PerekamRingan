package com.perekam.ringan

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val Red = Color(0xFFE53935)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFFFF8A80), background = Color(0xFF121214))
        else -> lightColorScheme(primary = Red)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun MainScreen(onStart: () -> Unit, onStop: () -> Unit, onFloating: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(Prefs.load(ctx)) }
    val startedAt by RecorderState.startedAt.collectAsState()
    val floating by OverlayState.enabled.collectAsState()
    val recording = startedAt != 0L
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(recording) {
        while (recording) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }

    fun update(n: RecSettings) {
        s = n
        Prefs.save(ctx, n)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Perekam Ringan",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(36.dp))
            RecordButton(recording, if (recording) onStop else onStart)
            Spacer(Modifier.height(16.dp))
            Text(
                if (recording) formatTime(now - startedAt) else "Ketuk untuk mulai merekam",
                style = MaterialTheme.typography.titleLarge,
                color = if (recording) Red else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Option(
                        "Resolusi", listOf("480p" to 480, "720p" to 720, "1080p" to 1080, "Asli" to 0),
                        s.shortSide, !recording,
                    ) { update(s.copy(shortSide = it)) }
                    Option(
                        "Frame rate",
                        listOf("30 fps" to 30, "60 fps" to 60, "90 fps" to 90, "120 fps" to 120),
                        s.fps, !recording,
                    ) { update(s.copy(fps = it)) }
                    Option(
                        "Bitrate",
                        listOf("4 Mbps" to 4, "8 Mbps" to 8, "12 Mbps" to 12, "16 Mbps" to 16),
                        s.bitrateMbps, !recording,
                    ) { update(s.copy(bitrateMbps = it)) }
                    Option(
                        "Codec video", listOf("H.264" to 264, "H.265" to 265),
                        s.codec, !recording,
                    ) { update(s.copy(codec = it)) }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Tombol rekam melayang", style = MaterialTheme.typography.bodyLarge)
                        Switch(floating, onFloating)
                    }
                    Text(
                        "Tombol merah di tengah layar. Ketuk untuk mulai merekam, lalu tombol hilang. " +
                            "Berhenti lewat tombol di notifikasi. Tombol bisa digeser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Rekam audio", style = MaterialTheme.typography.bodyLarge)
                        Switch(s.audioOn, { update(s.copy(audioOn = it)) }, enabled = !recording)
                    }
                    if (s.audioOn) {
                        Option(
                            "Sumber suara",
                            listOf(
                                "MIC" to SRC_MIC, "Raw" to SRC_RAW,
                                "Performa" to SRC_PERF, "Internal" to SRC_INTERNAL,
                            ),
                            s.audioSource, !recording,
                        ) { update(s.copy(audioSource = it)) }
                        Text(
                            when (s.audioSource) {
                                SRC_RAW -> "Raw: mikrofon tanpa terproses (tanpa peredam noise dan penguat otomatis)."
                                SRC_PERF -> "Performa Suara: mikrofon dengan latensi rendah."
                                SRC_INTERNAL -> "Suara Internal: merekam suara dari HP (game/media), bukan mikrofon. " +
                                    "Sebagian aplikasi memblokirnya, dan video disatukan saat berhenti."
                                else -> "MIC: mikrofon standar."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Option(
                            "Tingkat sampel audio",
                            listOf("44100 Hz" to 44100, "48000 Hz" to 48000),
                            s.sampleRate, !recording,
                        ) { update(s.copy(sampleRate = it)) }
                        Option(
                            "Saluran audio", listOf("Mono" to 1, "Stereo" to 2),
                            s.channels, !recording,
                        ) { update(s.copy(channels = it)) }
                        Option(
                            "Enkoder audio",
                            listOf("AAC" to 0, "AAC_ELD" to 1, "HE-AAC" to 2),
                            s.audioCodec, !recording,
                        ) { update(s.copy(audioCodec = it)) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Tanpa overlay dan widget melayang, jadi game tetap lancar. " +
                    "Untuk beban paling ringan pilih 720p, 30 fps. " +
                    "Hasil disimpan di Movies/Perekam.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    val size by animateDpAsState(if (recording) 44.dp else 96.dp, label = "size")
    val corner by animateDpAsState(if (recording) 12.dp else 48.dp, label = "corner")
    Box(
        Modifier
            .size(136.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(Red),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Option(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (label, value) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    enabled = enabled,
                    icon = {},
                    label = {
                        Text(
                            label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val t = ms / 1000
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t % 3600 / 60, t % 60)
    else "%02d:%02d".format(t / 60, t % 60)
}
