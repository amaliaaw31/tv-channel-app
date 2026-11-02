# Sumber data channel

`data/channels.json` dibuat otomatis oleh `tools/build-channels/build_channels.py` dari API komunitas **iptv-org**.
Jangan edit file itu manual; ubah `tools/build-channels/config.json` lalu jalankan ulang skrip.

## Sumber

Semua dari `https://iptv-org.github.io/api/`:

| File | Dipakai untuk |
|---|---|
| `channels.json` | nama, negara, kategori, status tutup, flag NSFW |
| `streams.json` | URL stream, kualitas, `user_agent`, `referrer` |
| `blocklist.json` | channel yang diblokir (alasan: nsfw, dmca, dll.), dibuang |
| `categories.json` | validasi nama kategori di config (typo membuat skrip berhenti) |
| `logos.json` | URL logo (`channels.json` API tidak punya kolom logo) |

iptv-org hanya mengindeks stream yang bisa diakses publik; mereka tidak menyimpan atau menjamin stream-nya.

## Aturan filter (berurutan)

1. Buang channel di blocklist, di `exclude.json` (hasil audit TV), `is_nsfw = true`, dan yang sudah ditutup (`closed`).
2. Simpan hanya kategori di `config.json` → `categories` (sekarang: sports, documentary, science, news, travel, kids, movies). Channel di beberapa kategori memakai yang **pertama di urutan config**.
3. Kode negara harus 2 huruf kapital. `UK` (tulisan iptv-org) dipetakan ke `GB`. Selain itu dibuang.
4. Channel harus punya minimal satu stream `http(s)`.
5. Stream diurutkan: resolusi tertinggi dulu, lalu `https` sebelum `http`. Maksimal `max_streams_tried_per_channel` (3) dicoba per channel.
6. Stream dianggap valid jika menjawab **HTTP 200** dalam `timeout_seconds` (5 detik) dan 16 KB pertamanya berisi playlist HLS (`#EXTM3U` **dan** `#EXT-X-`). Playlist m3u biasa (`#EXTINF` saja) ditolak. Stream valid pertama dipakai; satu stream per channel.
7. Header `User-Agent` dan `Referer` dari data iptv-org disimpan di field `headers`.
8. Hasil diurutkan (negara, nama, id) dan ditulis satu channel per baris agar diff git mudah dibaca.

Pengecekan berjalan paralel (`workers`, 64 thread).

**Pengaman:** skrip menolak menulis jika hasilnya kurang dari `min_channels` (100) atau kurang dari `min_keep_ratio` (50%) hasil sebelumnya, supaya masalah jaringan di VPS tidak menimpa data bagus dengan daftar kosong. Paksa dengan `--force`.

## Menjalankan

```bash
cd ~/projectyaya/tv-channel-app
python3 tools/build-channels/build_channels.py             # tulis data/channels.json
python3 tools/build-channels/build_channels.py --publish   # ... lalu git commit + push
python3 tools/build-channels/build_channels.py --limit 50 --out /tmp/trial.json   # uji cepat
python3 -m unittest discover -s tools/build-channels       # tes offline
```

Hanya butuh Python 3 (stdlib). Satu kali jalan sekitar beberapa menit.

## Hasil terakhir (2026-11-02)

- 2.681 channel kandidat → **1.945 lolos** validasi, 113 negara.
- Per kategori: news 761, movies 462, kids 264, sports 261, documentary 151, travel 35, science 11.
- 381 stream `http://`, 172 butuh header, 1.926 punya logo.

## Batasan dan kebijakan "resmi/legal"

Aturan proyek: hanya stream resmi/legal. **Skrip tidak bisa memastikan itu.**

- Daftar iptv-org bercampur: ada stream yang disiarkan langsung oleh stasiunnya, ada juga siaran ulang tidak resmi. Tidak ada penanda "resmi" di datanya. Blocklist hanya membuang yang sudah dilaporkan.
- Kategori **movies** paling berisiko memuat siaran ulang tidak resmi. Kalau ragu, keluarkan dari `categories` di config.
- Validasi dilakukan dari VPS. Stream yang dibatasi wilayah bisa lolos di VPS tetapi gagal di TV (app menampilkan "Dibatasi wilayah"), atau sebaliknya terbuang padahal bisa diputar di TV.
- Lolos validasi berarti playlist-nya terbaca saat itu, bukan bahwa siarannya hidup atau legal. Stream sering mati; jalankan ulang skrip berkala.
- Channel yang terbukti gagal di TV dikeluarkan lewat daftar pengecualian `tools/build-channels/exclude.json` (lihat bagian audit di bawah). Mengedit `data/channels.json` manual akan tertimpa di jalan berikutnya; tambahkan id ke `exclude.json` agar permanen.

## Audit di TV dan pemangkasan

Validasi HLS dari VPS (Singapura) tidak tahu apakah stream bisa diputar di TV (Indonesia): pembatasan wilayah dan dukungan decoder berbeda per perangkat. Karena itu ada alat audit yang memutar **setiap channel di TV**.

- `AuditActivity` (hanya di build debug, tanpa ikon launcher) memutar tiap channel di `data/channels.json` satu per satu, **bisu**, memakai player dan pemetaan error yang sama dengan app. Batas 25 detik per channel. Satu baris JSON per channel ditulis ke `files/audit.jsonl` di TV: `{"id", "ms", "result": ok|error|timeout, "title", "errorCode", "http"}`.
- Bisa dilanjutkan: channel yang sudah ada di file dilewati bila dijalankan ulang. `files/audit.done` muncul saat selesai.
- Lama: ±4 sampai 8 detik per channel (±2 sampai 4 jam untuk ±1.900 channel). Selama itu TV dipakai audit.

```bash
cd ~/projectyaya/tv-channel-app && export PATH=$HOME/android-sdk/platform-tools:$PATH
T=100.108.220.15:5555; P=com.projectyaya.tvchannel
./gradlew assembleDebug && adb -s $T install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $T shell am force-stop $P
adb -s $T shell run-as $P rm -f files/audit.jsonl files/audit.done      # mulai dari nol (lewati untuk melanjutkan)
adb -s $T shell am start -n $P/.AuditActivity
adb -s $T exec-out run-as $P cat files/audit.jsonl | wc -l              # kemajuan
adb -s $T shell run-as $P test -f files/audit.done && echo selesai
adb -s $T exec-out run-as $P cat files/audit.jsonl > /tmp/audit.jsonl   # ambil hasil
python3 tools/build-channels/prune_audit.py /tmp/audit.jsonl --dry-run  # lihat dulu
python3 tools/build-channels/prune_audit.py /tmp/audit.jsonl            # pangkas + tulis exclude.json
```

`prune_audit.py` membuang channel yang audit terakhirnya **error** dengan judul "Format tidak didukung" atau "Dibatasi wilayah" (ubah dengan `--titles`). Channel dengan hasil lain (timeout, tidak ditemukan, dll.) **tidak** dibuang, karena bisa sementara. Id yang dibuang dicatat di `exclude.json` (id, alasan, tanggal audit) dan dilewati oleh `build_channels.py`.

Hasil audit hanya berlaku untuk TV dan jaringan yang dipakai saat audit. Ulangi setelah mengganti TV atau ISP, atau secara berkala.
