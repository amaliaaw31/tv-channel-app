# Android TV Channel App

Aplikasi Android TV pribadi untuk menonton channel nasional & internasional. Tidak dirilis ke Play Store.

## Lingkungan
- User coding dari HP via Termius ke VPS Ubuntu (tanpa GUI).
- Tidak ada Android Studio/emulator. Build via Gradle CLI.
- Deploy ke TV via adb lewat Tailscale; cadangan: GitHub Release + app Downloader di TV.

## Stack
- Kotlin + Jetpack Compose for TV (androidx.tv)
- Media3 ExoPlayer (HLS/DASH)
- Daftar channel: channels.json (dibaca dari URL raw GitHub)

## MVP
Daftar channel per negara/kategori, player, favorit. EPG menyusul.

## Aturan
- Beri perintah shell lengkap & file utuh, output ringkas (layar HP).
- Semua UI harus bisa dinavigasi D-pad dengan focus state jelas.
- Tangani error stream (timeout, 403/geo-block) dengan baik.
- Hanya gunakan stream resmi/legal.
- Jawab dalam Bahasa Indonesia, kode & komentar dalam bahasa Inggris.

## Dokumen
- `docs/ui-spec.md`: layout, preview, navigasi remote, penanganan error.
- `docs/channels-schema.md`: skema `channels.json` + contoh.
- Aturan: setiap keputusan desain baru (UI, navigasi, error, skema) wajib diperbarui di docs/ pada commit yang sama dengan perubahan kodenya.
