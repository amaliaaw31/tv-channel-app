# Android TV Channel App

Aplikasi Android TV pribadi untuk menonton channel nasional & internasional. Tidak dirilis ke Play Store.

## Lingkungan
- User coding dari HP via Termius ke VPS Ubuntu (tanpa GUI).
- Tidak ada Android Studio/emulator. Build via Gradle CLI.
- Deploy ke TV via adb lewat Tailscale; cadangan: GitHub Release + app Downloader di TV.
- TV: `swtv-24ae-fhd` (model SWTV_24AE_FHD, Android SDK 34), adb di **`100.108.220.15:5555`** (IP Tailscale). VPS: `100.82.45.9`.
- Deploy: `adb connect 100.108.220.15:5555`, lalu `adb -s 100.108.220.15:5555 install -r app/build/outputs/apk/debug/app-debug.apk`, lalu `adb -s 100.108.220.15:5555 shell am start -n com.projectyaya.tvchannel/.MainActivity`. Lihat layar TV: `adb -s 100.108.220.15:5555 exec-out screencap -p > tv.png`.
- Jika IP berubah atau adb `unauthorized`/offline: cek `tailscale status` dan aktifkan lagi Network debugging di TV.

## Stack
- Kotlin + Jetpack Compose for TV (androidx.tv)
- Media3 ExoPlayer (HLS/DASH)
- Daftar channel: `data/channels.json` (URL raw GitHub, lalu cache lokal, lalu salinan yang ikut di APK; lihat docs/channels-schema.md)

## MVP
Daftar channel per grup/negara dengan filter kategori dan pencarian, player, favorit. EPG menyusul.

## Aturan
- Beri perintah shell lengkap & file utuh, output ringkas (layar HP).
- Semua UI harus bisa dinavigasi D-pad dengan focus state jelas.
- Tangani error stream (timeout, 403/geo-block) dengan baik.
- Hanya gunakan stream resmi/legal.
- Jawab dalam Bahasa Indonesia, kode & komentar dalam bahasa Inggris.

## Lokasi (fitur "Untuk Anda")
- Hanya izin lokasi **kasar** (`ACCESS_COARSE_LOCATION`, jaringan/Wi-Fi; TV tidak punya GPS). Jangan menambah izin lokasi presisi atau latar belakang.
- Posisi hanya dipakai di perangkat untuk memilih provinsi: **jangan disimpan, dicatat di log, atau dikirim** (log hanya nama provinsi/alasan gagal).
- Nama feed TVRI di `Regions.kt` harus selaras dengan `data/channels.json` (dijaga `RegionsTest`). Detail di `docs/ui-spec.md`.

## Channel (kurasi)
- **Hanya Tier A dan B yang masuk app** (`data/channels.json`). Tier C (IP mentah, agregator, tanpa stream, mati, tidak dipilih) hanya ada di `data/excluded.json` dan tidak boleh masuk app.
- **Channel baru wajib cek domain sumber** (kolom Domain di `python3 tools/build-channels/curate.py --report`): IP mentah atau agregator berarti Tier C; domain milik stasiun, grup medianya, atau mitra distribusi resmi boleh Tier A; ragu berarti Tier B (label "perlu dicek di TV"). Catat domain barunya di `docs/sources.md`.
- Jangan edit `data/channels.json` / `data/excluded.json` manual. Ubah `tools/build-channels/curated_wanted.json` atau `tiers.json`, lalu jalankan `curate.py` dan `finalize.py` (urutannya di `docs/sources.md`).

## Git & GitHub
- Repo GitHub **private**: `amaliaaw31/tv-channel-app`. Jangan ubah jadi publik.
- Branch utama `main` (lokal dan remote sama namanya); push dengan `git push`.
- Remote `origin` memakai alias SSH `github-tvchannel` (key khusus repo ini `~/.ssh/id_ed25519_tvchannel`). Jangan pakai key lain di `~/.ssh`, itu milik proyek lain.
- Email commit wajib `113813839+amaliaaw31@users.noreply.github.com` (sudah di `git config --local`). Email pribadi ditolak GitHub (GH007) dan tidak boleh masuk history.
- Push hanya atas permintaan user.

## Dokumen
- `docs/ui-spec.md`: layout, daftar berbagian (grup/negara), "Untuk Anda" (lokasi), tab kategori, pencarian, preview, navigasi remote, penanganan error, kinerja start.
- `docs/channels-schema.md`: skema `channels.json` (termasuk `groups`, `note`, `tier`), pemuatan remote/cache/bundled, contoh.
- `docs/sources.md`: alur kurasi (iptv-org), definisi Tier A/B/C, domain yang dianggap resmi, cara menambah channel, cara menjalankan `tools/build-channels`, batasan legal.
- Aturan: setiap keputusan desain baru (UI, navigasi, error, skema) wajib diperbarui di docs/ pada commit yang sama dengan perubahan kodenya.
