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

## Antena (grup "TV Nasional (Antena)")
- Channel antena (RCTI, SCTV, dll.) dari tuner TV diputar lewat **`TvView`** (preview kecil di slot dinyalakan flag `antennaPreview` di `antenna.json`, sudah dicek dengan mata di TV; `TvView` hanya terpasang saat dipakai, lihat `docs/ui-spec.md`), bukan stream. Nama yang dicari ada di `data/antenna.json`; **id channel dibaca saat berjalan** dari TvProvider (berubah setelah scan ulang), jangan disimpan atau di-hardcode. Tile "TV Antena" membuka Live TV bawaan; package/activity-nya dideteksi di TV dengan `pm`/`dumpsys` (cara di `docs/sources.md`), jangan ditebak.
- App meminta izin `READ_TV_LISTINGS` (runtime, sekali) hanya untuk membaca hasil scan. Tuner harus **dilepas saat app ke belakang** dan saat item lain mengambil preview (sudah diuji): jangan menahan sesi `TvView` di latar.
- Video tuner tidak ikut `screencap` (hasilnya kosong/hitam); jangan menyimpulkan "layar rusak" dari itu. Periksa dengan `dumpsys tv_input`, log tag `Antenna`, dan `uiautomator dump`.

## Channel (kurasi)
- **Hanya Tier A dan B yang masuk app** (`data/channels.json`). Tier C (IP mentah, agregator, tanpa stream, mati, tidak dipilih) hanya ada di `data/excluded.json` dan tidak boleh masuk app.
- **Channel baru wajib cek domain sumber** (kolom Domain di `python3 tools/build-channels/curate.py --report`): IP mentah atau agregator berarti Tier C; domain milik stasiun, grup medianya, atau mitra distribusi resmi boleh Tier A; ragu berarti Tier B (label "perlu dicek di TV"). Catat domain barunya di `docs/sources.md`.
- Jangan edit `data/channels.json` / `data/excluded.json` manual. Ubah `tools/build-channels/curated_wanted.json` atau `tiers.json`, lalu jalankan `curate.py` dan `finalize.py` (urutannya di `docs/sources.md`).

## Versi HP
- APK yang sama jalan di HP: `isTelevision()` memilih `MainScreen` (D-pad, TV) atau `PhoneScreen` (sentuh). Jangan menambah kode khusus HP di `MainScreen`, dan jangan membawa antena ke HP. Aturan lengkap di `docs/ui-spec.md` ("Versi HP").

## Fullscreen
- Zapping fullscreen (atas/bawah, Channel Up/Down) berputar di bagian daftar tempat channel dibuka; hanya satu pemutar aktif (ExoPlayer **atau** tuner), dan `TvView` hanya ada di hierarki saat dipakai. Channel terakhir ditonton disimpan di DataStore untuk bagian "Lanjutkan" (hanya id/nama/bagian; tidak ada lokasi). Detail di `docs/ui-spec.md`.

## Subtitle terjemahan
- Hanya channel **stream internet** di fullscreen, tidak pernah antena/tile/preview. Aturan keputusan (`languages` `ind` → mati; track Indonesia → apa adanya; track lain → terjemahkan; tanpa track → "Subtitle tidak tersedia"), filter per baris, dan menu (tombol kanan) ada di `docs/ui-spec.md`; logikanya murni di `SubtitleLogic.kt` dan harus tetap dites.
- ML Kit (translate + language-id) **on-device**: yang diunduh hanya model; teks subtitle **tidak dikirim** ke mana pun dan tidak dicatat di log. Translator dan cache baris dilepas saat pindah channel/keluar fullscreen. Jaga memori (TV 1 GB RAM) dan tetap batasi ABI (`abiFilters`).
- `caption` (none/subtitle/cc) di `channels.json` datang dari `tools/audit-captions/audit.py --segments --samples 8 --write` lalu `finalize.py` (jangan diedit manual); ikon "CC" hanya untuk `subtitle`/`cc`. Stream `cc` diputar tanpa chunkless preparation supaya CEA-608 yang tidak dideklarasikan terbaca (alasan dan hasil uji di `docs/sources.md`). Baris < 3 kata diterjemahkan hanya bila deteksi bahasa ≥ 0,8.
- `languages` di `channels.json` diisi `curate.py` dari iptv-org `feeds.json` (jangan edit manual; `curate.py --languages` lalu `finalize.py`).

## Git & GitHub
- Repo GitHub **publik** (keputusan pengguna 2026-11-04): `amaliaaw31/tv-channel-app`. Karena itu **jangan pernah commit rahasia** (keystore, `local.properties`, kata sandi, token, key SSH) dan jangan menaruh data pribadi di repo; `.gitignore` sudah menutup `*.jks`, `*.keystore`, `local.properties`, `keystore.properties`, `release/`. App membaca daftar channel dari `raw.githubusercontent.com` tanpa token, jadi `channels.token` tidak dipakai.
- Rilis: `tools/release.sh` (keystore di `~/.tvchannel-release`, bukan di repo; jangan di-commit). Kesehatan channel: `tools/health-check/check.py`. Detail di `docs/sources.md`.
- Branch utama `main` (lokal dan remote sama namanya); push dengan `git push`.
- Remote `origin` memakai alias SSH `github-tvchannel` (key khusus repo ini `~/.ssh/id_ed25519_tvchannel`). Jangan pakai key lain di `~/.ssh`, itu milik proyek lain.
- Email commit wajib `113813839+amaliaaw31@users.noreply.github.com` (sudah di `git config --local`). Email pribadi ditolak GitHub (GH007) dan tidak boleh masuk history.
- Push hanya atas permintaan user.

## Dokumen
- `docs/ui-spec.md`: layout, daftar berbagian (grup/negara), "Untuk Anda" (lokasi), "TV Antena" (Live TV), tab kategori, pencarian, preview, navigasi remote, penanganan error, kinerja start.
- `docs/channels-schema.md`: skema `channels.json` (termasuk `groups`, `note`, `tier`), pemuatan remote/cache/bundled, contoh.
- `docs/sources.md`: alur kurasi (iptv-org), definisi Tier A/B/C, domain yang dianggap resmi, cara menambah channel, cara menjalankan `tools/build-channels`, batasan legal.
- Aturan: setiap keputusan desain baru (UI, navigasi, error, skema) wajib diperbarui di docs/ pada commit yang sama dengan perubahan kodenya.
