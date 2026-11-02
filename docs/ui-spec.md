# UI Spec

Spesifikasi layar utama aplikasi. Kode acuan: `MainScreen.kt`, `PlayerController.kt`, `StreamErrors.kt`.
Satuan dp mengikuti Compose (TV 1080p kira-kira 960×540 dp).

## Layout

Satu layar dua kolom, tanpa navigasi antar-layar. Margin aman TV: 48 dp kiri/kanan, 27 dp atas/bawah.

| Area | Lebar | Isi |
|---|---|---|
| Kiri | 58% | Preview 16:9, di bawahnya nama channel, lalu "Negara (KODE) · kategori" |
| Kanan | 42% | Daftar teks channel, dikelompokkan per negara |

- Daftar: header negara (nama Indonesia + kode, mis. "Indonesia (ID)"), diurutkan abjad menurut nama negara. Urutan channel dalam satu negara mengikuti urutan di `channels.json`.
- Header negara tidak bisa difokus. Hanya baris channel yang bisa.
- Tidak ada logo di daftar. Field `logo` di skema belum dipakai.
- Baris yang difokus: isi warna aksen transparan 25% + border 3 dp warna aksen. Warna aksen = ungu tema (normal), kuning atau merah (channel gagal, lihat Error).

## Preview dan player

- **Satu ExoPlayer** (`PlayerController`) dan **satu `PlayerView`** untuk preview dan fullscreen. View hanya diubah ukurannya (slot preview ↔ layar penuh), sehingga OK ke fullscreen tidak memuat ulang stream.
- Preview **bisu** (volume 0). Fullscreen **volume penuh**. Kembali ke dua kolom, suara dibisukan lagi dan stream tetap berjalan.
- **Debounce 700 ms**: stream baru dimuat 700 ms setelah fokus berhenti berpindah. Begitu fokus pindah, muatan sebelumnya langsung dihentikan dan preview menampilkan "Memuat…".
- Header channel dikirim lewat `DefaultHttpDataSource.setDefaultRequestProperties`. `User-Agent` juga di-set lewat `setUserAgent` karena properti user agent milik factory bisa menimpa header.
- Timeout koneksi dan baca masing-masing 10 detik. Redirect antar http/https diizinkan.
- Cleartext `http://` diizinkan lewat `res/xml/network_security_config.xml` (`base-config cleartextTrafficPermitted="true"`). Alasan: app pribadi, banyak stream IPTV memakai http.
- Tombol Home menjeda pemutaran. Kembali ke app melanjutkan.
- Layar tetap menyala selama app terbuka (`keepScreenOn`).

## Navigasi remote

| Tombol | Di daftar | Di fullscreen |
|---|---|---|
| Atas / Bawah | Pindah baris (melewati header negara), daftar ikut menggulir | Tidak ada efek |
| OK | Channel normal: fullscreen. Channel error: muat ulang di preview, tidak fullscreen | Pada layar "Siaran terputus": menekan tombol yang difokus |
| Kiri / Kanan | Tidak ada efek | Berpindah antara Coba lagi dan Kembali (hanya saat siaran terputus) |
| Kembali | Keluar dari app | Kembali ke dua kolom, fokus kembali ke channel terakhir |

- Saat fullscreen, baris daftar dinonaktifkan agar D-pad tidak memindahkan fokus di bawah video.
- Fokus awal: channel pertama pada urutan tampil.

## Status preview dan penanganan error

Status ditampilkan di area preview. Daftar tetap bisa dinavigasi selama error.

| Kondisi | Pesan | Warna |
|---|---|---|
| Sedang memuat | spinner + "Memuat…" | putih |
| `IO_NETWORK_CONNECTION_TIMEOUT` | Server tidak merespons | kuning |
| `IO_BAD_HTTP_STATUS` 403 atau 451 | Dibatasi wilayah | kuning |
| `IO_BAD_HTTP_STATUS` 404 atau 410 | Channel tidak ditemukan | merah |
| `IO_NETWORK_CONNECTION_FAILED` (tanpa koneksi) | Tidak ada internet | merah |
| `PARSING_CONTAINER_MALFORMED`, `PARSING_MANIFEST_MALFORMED`, `PARSING_CONTAINER_UNSUPPORTED`, `PARSING_MANIFEST_UNSUPPORTED`, `DECODING_FORMAT_UNSUPPORTED` | Format tidak didukung | merah |
| Lainnya (termasuk HTTP status lain) | Channel tidak bisa diputar + kode kecil: `HTTP 500 · kode 2004` atau `Kode 1000` | merah |

- Di bawah pesan error ada petunjuk kecil "Tekan OK untuk coba lagi".
- Warna kuning = bisa berhasil nanti atau di tempat lain. Merah = perlu channel lain atau perbaikan konfigurasi.
- Baris daftar milik channel yang sedang dimuat dan gagal ikut berwarna kuning/merah (highlight dan border) saat difokus.
- Kode 451 dan 410 ditambahkan sebagai padanan 403 dan 404 (sama-sama diblokir wilayah / sudah tidak ada).
- `BEHIND_LIVE_WINDOW` bukan error bagi pengguna: player otomatis kembali ke tepi live.

### Siaran putus di fullscreen

1. Error muncul saat fullscreen: **reconnect otomatis 1×** (tampil "Memuat…").
2. Jika berhasil memutar lagi, jatah reconnect direset (putus berikutnya dapat 1× lagi).
3. Jika gagal: layar "Siaran terputus", alasan (warna kuning/merah), dan dua tombol **Coba lagi** (fokus awal) dan **Kembali**.
4. Coba lagi memuat ulang dari tepi live dan mereset jatah reconnect. Kembali ke dua kolom; preview menampilkan pesan error channel itu.

App tidak boleh crash karena error stream: semua kegagalan pemutaran masuk ke status di atas.

## Belum dicakup / batasan

- Belum diuji di perangkat TV sungguhan (hanya build dan tes unit).
- Tidak ada favorit, EPG, atau pengingat posisi daftar di luar sesi.
- Rebuffering saat fullscreen menampilkan spinner "Memuat…" di atas video.
- Pemetaan error teruji lewat tes unit (`DescribeErrorTest`). Alur reconnect dan fokus belum punya tes otomatis.
