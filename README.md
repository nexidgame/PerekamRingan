# Perekam Ringan

Perekam layar minimalis untuk Android 11 (API 30) sampai Android 16 (API 36).

## Cara build
1. Buka folder ini di Android Studio (Ladybug atau lebih baru, JDK 17), tunggu Gradle sync.
2. Hubungkan HP, lalu Run. Atau Build > Generate Signed App Bundle / APK untuk rilis.
3. Setelah sync pertama, Android Studio membuat file gradle-wrapper.jar sendiri.

## Kenapa ringan
- MediaRecorder + encoder hardware (H.264 atau H.265): frame dari layar langsung ke encoder, tanpa proses di CPU.
- Tidak ada overlay, widget melayang, kamera, atau penggambaran sentuhan.
- UI hanya aktif saat aplikasi dibuka; saat merekam, aplikasi langsung ke latar belakang.

## Fitur
Resolusi (480p/720p/1080p/asli), 30/60/90/120 fps, bitrate 4-16 Mbps,
codec H.264 atau H.265 (otomatis kembali ke H.264 jika HP tidak mendukung H.265),
audio opsional dengan pilihan:
- Sumber suara: MIC, Raw (tanpa terproses), Performa Suara, atau Suara Internal
- Tingkat sampel: 44100 Hz atau 48000 Hz
- Saluran: mono atau stereo
- Enkoder: AAC, AAC_ELD, atau HE-AAC
Jika kombinasi audio tidak didukung HP, aplikasi otomatis memakai AAC (dan MIC untuk sumber mikrofon).
Video tersimpan di Movies/Perekam lewat MediaStore (tanpa izin penyimpanan).

## Catatan Android 14-16
Foreground service bertipe mediaProjection dimulai sebelum getMediaProjection(),
callback didaftarkan sebelum createVirtualDisplay(), dan UI edge-to-edge.
Suara Internal memakai AudioPlaybackCapture: direkam terpisah lalu digabung dengan video
saat berhenti, jadi ada jeda singkat untuk menyimpan. Aplikasi/game yang melarang
perekaman audio akan menghasilkan suara kosong.
