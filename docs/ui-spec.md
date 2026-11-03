# UI Spec

Spesifikasi layar utama aplikasi. Kode acuan: `MainScreen.kt`, `PlayerController.kt`, `StreamErrors.kt`.
Satuan dp mengikuti Compose (TV 1080p kira-kira 960×540 dp).

## Layout

Satu layar dua kolom, tanpa navigasi antar-layar. Margin aman TV: 48 dp kiri/kanan, 27 dp atas/bawah.

| Area | Lebar | Isi |
|---|---|---|
| Kiri | 58% | Preview 16:9, di bawahnya nama channel, lalu "Negara (KODE) · kategori" |
| Kanan | 42% | Dari atas: kolom pencarian, baris tab kategori, daftar teks channel berbagian (grup, lalu negara) |

- Daftar terdiri dari **bagian** (kode: `buildSections`), masing-masing dengan header:
  1. **Bagian grup** dulu, dari field `groups` di data (mis. Nasional, MNC, Berita, Publik, TVRI Daerah, Daerah). Urutan tetap: Nasional, MNC, Berita, Publik, TVRI Daerah, Daerah; grup lain menyusul urut abjad. Channel di dalam grup diurutkan menurut nama.
  2. **Bagian negara** setelahnya, untuk channel tanpa `groups`: header nama Indonesia + kode (mis. "Jerman (DE)"), diurutkan abjad menurut nama negara, channel mengikuti urutan di `channels.json`.
- Channel dengan beberapa grup muncul di **setiap** grupnya (mis. channel dengan `groups: ["MNC", "Nasional"]` ada di Nasional dan MNC; saat ini belum ada channel seperti itu di data), dan tidak diulang di bagian negaranya. Baris diberi kunci `<bagian>/<id>`; preview mengikuti id channel, jadi pindah antar dua baris channel yang sama tidak memuat ulang stream.
- Channel yang punya `note` (Tier B: "perlu dicek di TV") menampilkannya sebagai teks kecil kuning di samping nama di daftar, dan di bawah info channel di sisi kiri.
- Header bagian tidak bisa difokus. Hanya baris channel yang bisa.
- Tidak ada logo di daftar. Field `logo` di skema belum dipakai.
- Baris yang difokus: isi warna aksen transparan 25% + border 3 dp warna aksen. Warna aksen = biru tema ikon `#3CA6FB` (normal, `MaterialTheme.colorScheme.primary`), kuning atau merah (channel gagal, lihat Error).

## Pencarian dan filter kategori

Keduanya memfilter daftar kanan; pengelompokan per negara tetap. Kode: `filterChannels`, `categoriesOf`, `SearchField`, `CategoryTabs`.

- **Tab kategori**: "Semua" lalu kategori yang ada di data, urutan tetap: Olahraga, Dokumenter, Sains, Berita, Wisata, Anak, Film, Umum (`general`), Hiburan (`entertainment`), Musik (kategori lain menyusul urut abjad, label = id dengan huruf besar di depan). Tab hanya menampilkan kategori yang ada di data. Baris tab bisa digeser kiri/kanan.
- **Pilihan mengikuti fokus**: menggeser fokus ke sebuah tab langsung memfilter daftar (seperti TabRow Android TV), tanpa menekan OK. Tab terpilih diberi isi warna aksen 35%; tab yang difokus 55% + border 3 dp.
- **Pencarian**: mencocokkan **nama channel** (mengandung teks, tanpa membedakan huruf besar/kecil, spasi di tepi diabaikan). Digabung dengan tab terpilih (keduanya harus cocok).
- Jika tidak ada yang cocok: teks "Tidak ada channel yang cocok." menggantikan daftar.
- Preview tidak berhenti ketika channel yang sedang diputar tersaring keluar dari daftar; info di kiri tetap channel itu sampai fokus pindah ke channel lain.
- Keyboard virtual terbuka sendiri saat kolom pencarian difokus. OK membukanya lagi jika sudah ditutup. Selama keyboard terbuka, D-pad bergerak di keyboard: tekan **Kembali** untuk menutupnya, baru turun ke tab/daftar.

## Untuk Anda (lokasi)

Bagian pertama di daftar kanan, dengan header "Untuk Anda · <provinsi>", berisi **TVRI regional provinsi tempat TV berada**. Kode: `Regions.kt` (tabel provinsi, pencarian terdekat), `LocationFinder.kt` (ambil lokasi), `buildSections(..., forYou)`.

- **Bukan GPS sungguhan.** TV tidak punya chip GPS (`android.hardware.location.gps` tidak ada). App memakai lokasi **jaringan/Wi-Fi** (`NETWORK_PROVIDER`) dengan izin **`ACCESS_COARSE_LOCATION`** saja. Cukup untuk tingkat provinsi.
- **Izin ditanyakan sekali**, saat start pertama (dialog sistem, dijawab dengan remote). Jika ditolak, app tetap normal tanpa bagian ini dan **tidak bertanya lagi** pada start berikutnya (penanda `location_prompted` di preferensi). Mengaktifkan lagi: Pengaturan TV → Apps → TV Channel → Permissions → Location, atau hapus data app (juga me-reset penanda).
- **Privasi:** posisi hanya dipakai di TV untuk memilih provinsi. Tidak disimpan, tidak dicatat di log (hanya nama provinsi atau alasan gagal, tag `Region`), dan tidak dikirim ke mana pun.
- **Cara menentukan provinsi:** titik pusat 38 provinsi tertanam di app (`PROVINCES`), diambil yang **terdekat**. Akurat di tengah provinsi, tetapi bisa salah ke provinsi tetangga di dekat perbatasan; anggap ini saran. Jika posisi lebih dari 800 km dari semua titik (TV tidak di Indonesia), bagian ini tidak tampil.
- **TVRI yang ditampilkan:** feed `TVRI.id#<Feed>` milik provinsi itu. Provinsi tanpa feed sendiri memakai feed tetangga terdekat (Banten → Jakarta, Kepulauan Riau → Riau, Maluku Utara → Maluku, Kalimantan Utara → Kalimantan Timur, Papua Barat Daya → Papua Barat, empat provinsi Papua baru → Papua). Nama feed di `Regions.kt` harus ada di `data/channels.json`; `RegionsTest` gagal jika ada yang salah ketik atau feed dihapus dari data.
- **Perilaku daftar:** channel yang sama tetap ada juga di grup normalnya (TVRI Daerah). Bagian ini ikut tersaring oleh tab kategori dan pencarian, dan tidak tampil bila tidak ada isinya. Lokasi biasanya didapat dalam sekejap (lokasi terakhir dari sistem), tetapi bila datang setelah daftar tampil, daftar digulir ke atas agar bagian ini terlihat (kecuali pengguna sudah menggulir ke bawah).
- Belum ada tombol untuk memilih provinsi manual atau mematikan fitur dari dalam app.

## TV Antena (grup "TV Nasional (Antena)")

Grup ini selalu yang pertama setelah "Untuk Anda" dan berisi dua jenis item. Konfigurasi: `data/antenna.json` (lihat `channels-schema.md`).

**Channel antena** (RCTI, GTV, MNCTV, iNews, Indosiar, SCTV, ANTV, tvOne, Metro TV, Trans TV, Trans 7): siaran dari tuner TV sendiri, diputar **fullscreen di app lewat `TvView`** (kode: `AntennaController`, `Antenna.kt`). Hanya yang ada di hasil scan TV yang tampil, dengan nama bawaan TV (`display_name`, mis. "RCTI HD") dan nomor Live TV di sampingnya ("no. 28").
- **Izin `READ_TV_LISTINGS`** (runtime, dialog sistem) dibutuhkan untuk membaca hasil scan. Ditanyakan **sekali** (penanda `tv_listings_prompted`); jika ditolak, hanya tile "TV Antena" yang ada. Di start pertama dengan dua izin baru (lokasi dan TV listings), dialognya muncul berurutan.
- **ID channel dibaca saat berjalan** dari scan TV (berubah setelah scan ulang), jadi tidak disimpan di config; config hanya berisi daftar nama yang dicari (toleran: huruf besar/kecil, spasi, akhiran "HD").
- **Tanpa preview (bawaan)**: fokus ke channel antena hanya menampilkan "Tekan OK untuk menonton siaran antena" di slot preview; tuner belum disetel. **OK** = `TvView` fullscreen dengan suara; **Kembali** = tuner dilepas dan kembali ke dua kolom. Alasan: video tuner (MediaTek) digambar di lapisan video TV sendiri, bukan lewat Surface `TvView`, dan dugaan kuat posisinya tidak mengikuti `TvView` kecil (tampak sebagai jendela kecil di pojok kiri atas, sisanya hitam). Dugaan ini belum terbukti secara visual karena video tuner tidak ikut `screencap`; fullscreen aman karena ukuran = layar.
- **Preview (opsional)**: `"preview": true` di `antenna.json` menyetel tuner di slot kecil setelah **debounce 700 ms**, **bisu**; fokus pindah ke channel stream atau tile melepas tuner. `TvView` ditumpuk di atas `PlayerView` dan berukuran nol bila tidak dipakai. Bawaan **mati** (lihat atas).
- **`TvView` hanya ada di hierarki saat dipakai** (fullscreen, atau slot bila preview aktif). Jika terus terpasang walau berukuran 0, di TV ini seluruh layar selain slot preview kosong (daftar, pencarian, dan info tidak tergambar; terlihat di foto TV dan `screencap`). Setelah dilepas dari hierarki, layar kembali utuh.
- **Tuner dilepas saat app ke belakang** (Home, atau membuka Live TV dari tile) supaya aplikasi Live TV bisa memakainya, dan disetel ulang saat app kembali (diuji: 1 sesi saat fokus di antena, 0 saat Home, 1 saat kembali).
- **Pesan di preview** (tanpa tombol): "Sinyal antena lemah" dan "Siaran tidak tersedia" (kuning), "Tuner TV tidak bisa dipakai" dan "Tuner TV terputus" (merah), "Siaran diblokir kontrol orang tua" (kuning); "Memuat…" saat menyetel.
- **Batasan:** video tuner tidak ikut `screencap` (lapisan terlindungi; layar tampak hitam di tangkapan layar begitu `TvView` ada di jendela), jadi tampilannya hanya bisa diperiksa dengan mata. Tidak ada tombol coba lagi; pindah channel lalu kembali untuk menyetel ulang.

**Tile "TV Antena"** membuka aplikasi Live TV bawaan TV lewat Intent (`ACTION_MAIN` + `LEANBACK_LAUNCHER`, package/activity dari konfigurasi) sebagai jalan lain, mis. untuk channel di luar daftar.
- Preview dikosongkan (tanpa spinner) dan panel info menjelaskan fungsinya.
- Diselidiki lewat adb saat Live TV tampak kecil di pojok kiri atas: task `com.cltv.hybrid` selalu `fullscreen` dengan bounds (0,0)-(1280,720), baik dibuka dari tile, dari `adb shell am start`, maupun setelah preview `TvView`. Intent sistem (tombol TV) memakai `ACTION_VIEW` + package + flag `0x20`; milik app `ACTION_MAIN` + `LEANBACK_LAUNCHER` + `NEW_TASK`; hasil jendelanya sama, jadi intent tidak diubah.
- **Kembali** di Live TV mengembalikan ke app ini, fokus tetap di tile (diuji di TV). Saat app ke belakang, pemutaran dijeda dan tuner dilepas.
- **Live TV tidak ada** di TV: muncul pesan "Aplikasi Live TV tidak ditemukan di TV ini." (diuji dengan menonaktifkan aplikasinya sementara).
- Tile selalu di urutan pertama dalam grup, channel antena menyusul urut abjad menurut nama bawaan TV (ANTV HD, GTV HD, INDOSIAR HD, ...), sama dengan grup lain.

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

- Atas dari channel teratas menuju baris tab (chip yang paling dekat secara horizontal), atas lagi menuju kolom pencarian. Bawah kembali ke daftar.
- Saat fullscreen, pencarian, tab, dan baris daftar dinonaktifkan agar D-pad tidak memindahkan fokus di bawah video.
- Fokus awal: channel pertama pada urutan tampil. Setelah fullscreen, fokus kembali ke channel terakhir jika masih lolos filter, jika tidak ke channel pertama yang tampil.

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

- Diuji di TV SWTV_24AE_FHD (Android 14, RAM ±1 GB): tampilan, preview, tab kategori, pencarian dengan keyboard virtual, bagian grup dan label catatan. Perilaku di TV lain belum diketahui.
- Tidak ada favorit, EPG, atau pengingat posisi daftar di luar sesi.
- Rebuffering saat fullscreen menampilkan spinner "Memuat…" di atas video.
- Pemetaan error teruji lewat tes unit (`DescribeErrorTest`). Alur reconnect dan fokus belum punya tes otomatis.

## Ikon dan splash

- Ikon launcher `mipmap/ic_launcher` (PNG), banner TV `drawable/banner` (320×180). Master ada di `design/` (`icon_512.png`, `banner_1280x720.png`); turunkan ukuran lain dari sana.
- Splash memakai `androidx.core:core-splashscreen`: latar `#060B24`, ikon `drawable-xxhdpi/splash_icon.png` (kanvas 288 dp, ikon 52% kanvas agar muat di lingkaran splash = 2/3 kanvas).
- Splash ditahan sampai `channels.json` selesai dibaca (berhasil atau gagal), jadi layar "Memuat channel…" tidak pernah tampil.

## Kinerja start (diukur di TV, build debug)

**Daftar sekarang ±54 channel (data kurasi): start ±8 sampai 9 detik, pemuatan data ±1 detik.** Pengukuran lama di bawah dengan 1.945 channel (dump massal, sudah dihentikan) disimpan sebagai catatan: start ±11 sampai 15 detik. Rincian pengukuran lama:

| Tahap | Lama |
|---|---|
| Proses mulai sampai `onCreate` | ±2 dtk |
| Memuat channel: cek remote (404 untuk repo private tanpa token) ±0,7 dtk, baca asset ±1,3 dtk, parse `org.json` ±3,6 dtk | ±5,6 dtk |
| Frame Compose pertama | ±3,7 dtk |

Penyebab utama: TV hanya punya ±950 MB RAM dan hampir penuh (swap ±400 MB terpakai), sehingga baca file dan GC lambat. Sudah dicoba dan **tidak membantu**: kompilasi AOT (`cmd package compile -m speed`) dan parser streaming Gson (hemat ±0,5 dtk, tidak sepadan dengan dependensi baru). Opsi yang belum dikerjakan: build release dengan R8 dan baseline profile, menghilangkan field `logo` dari data (tidak dipakai UI, ±24% ukuran), membatasi jumlah channel di config, atau menampilkan UI lebih awal (mengubah keputusan splash di atas).
