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

## Git & GitHub
- Repo GitHub **private**: `amaliaaw31/tv-channel-app`. Jangan ubah jadi publik.
- Branch lokal `master` ↔ remote `main` (push dengan `git push origin master:main`).
- Remote `origin` memakai alias SSH `github-tvchannel` (key khusus repo ini `~/.ssh/id_ed25519_tvchannel`). Jangan pakai key lain di `~/.ssh`, itu milik proyek lain.
- Email commit wajib `113813839+amaliaaw31@users.noreply.github.com` (sudah di `git config --local`). Email pribadi ditolak GitHub (GH007) dan tidak boleh masuk history.
- Push hanya atas permintaan user.

## Dokumen
- `docs/ui-spec.md`: layout, preview, navigasi remote, penanganan error.
- `docs/channels-schema.md`: skema `channels.json` + contoh.
- Aturan: setiap keputusan desain baru (UI, navigasi, error, skema) wajib diperbarui di docs/ pada commit yang sama dengan perubahan kodenya.
