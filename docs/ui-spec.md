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
- **Kolom pencarian hanya fokus biasa** (baris yang bisa difokus D-pad, tanpa keyboard) sampai **OK** ditekan: baru saat itu kolom teks dibuat, mengambil fokus, dan membuka keyboard virtual. Atas/bawah/kanan dari kolom yang hanya terfokus bergerak normal (kanan tidak punya tujuan, jadi diam). Sebabnya: kolom teks yang sekadar terfokus membuka keyboard sendiri dan keyboard menelan D-pad sehingga fokus terjebak.
- **Mengakhiri pengetikan:** tombol cari di keyboard atau **Kembali** yang sampai ke app menutup keyboard dan memindahkan fokus ke daftar (baris terakhir yang difokus bila masih tersaring, kalau tidak baris pertama). Keyboard TV ini (Gboard TV) **mengonsumsi Kembali pertama untuk menutup dirinya** tanpa memberi tahu app (diukur: jendelanya tidak melaporkan insets maupun tinggi terlihat, dan muncul dengan jeda beberapa detik), jadi setelah itu kolom masih terfokus tanpa keyboard. Dalam keadaan itu atas/bawah/kiri/kanan memindahkan fokus keluar (kolom teks menelan tombol itu, jadi dipindahkan manual dengan `FocusManager`), pindahnya fokus mengakhiri pengetikan, dan **OK** membuka keyboard lagi. Selama keyboard tampil, D-pad bergerak di keyboard.
- Diuji di TV: OK, ketik "ABC", Kembali, bawah, bawah menuju "ABC News Live".

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
- **Tanpa preview (`antennaPreview` false)**: fokus ke channel antena hanya menampilkan "Tekan OK untuk menonton siaran antena" di slot preview; tuner belum disetel. **OK** = `TvView` fullscreen dengan suara; **Kembali** = tuner dilepas dan kembali ke dua kolom. Alasan: video tuner (MediaTek) digambar di lapisan video TV sendiri, bukan lewat Surface `TvView`, dan dugaan kuat posisinya tidak mengikuti `TvView` kecil (tampak sebagai jendela kecil di pojok kiri atas, sisanya hitam). Dugaan ini belum terbukti secara visual karena video tuner tidak ikut `screencap`; fullscreen aman karena ukuran = layar.
- **Preview (flag `antennaPreview`, SEKARANG NYALA di `data/antenna.json`)**: `"antennaPreview": true` di `antenna.json`. Fokus ke channel antena: slot menampilkan "Memuat…", lalu setelah **debounce 700 ms** `TvView` dipasang di slot (ukuran persis kotak preview) dan tuner disetel, **bisu**. Fokus pindah melepas `TvView` seketika (dan tuner bila tujuannya bukan channel antena). OK = fullscreen dengan `TvView` yang sama, Kembali = kembali ke slot.
  - **Hasil uji di TV (2026-11-03):** mekanismenya benar (`dumpsys tv_input`: 1 sesi saat preview, fullscreen, dan sesudah Kembali; 0 saat fokus pindah ke tile; tanpa crash). **Tampilan belum terverifikasi dari VPS**: `screencap` kosong (0 byte) selama `TvView` terpasang, jadi tidak bisa dipastikan apakah daftar kanan, pencarian, dan info channel tetap tergambar. Setelah dicek dengan mata di TV oleh pengguna (2026-11-03): **aman**, daftar kanan, pencarian, dan info channel tetap tergambar. Flag dinyalakan di `data/antenna.json`; bila di TV lain layar app hilang (hanya kotak preview yang tergambar), set `false`.
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
| Atas / Bawah | Pindah baris (melewati header negara), daftar ikut menggulir | **Zapping**: atas = channel berikutnya, bawah = sebelumnya (lihat di bawah) |
| Channel Up / Down | Tidak ada efek | Sama dengan atas / bawah |
| OK | Channel normal: fullscreen. Channel error: muat ulang di preview, tidak fullscreen | Menampilkan banner lagi; pada stream yang gagal juga memuat ulang |
| Kiri | Tidak ada efek | Menutup menu subtitle bila terbuka |
| Kanan | Tidak ada efek | Membuka menu subtitle (stream internet saja; atas/bawah/OK/kiri dipakai menu selama terbuka) |
| Kembali | Keluar dari app | Kembali ke dua kolom, fokus kembali ke channel terakhir yang ditonton |

- Atas dari channel teratas menuju baris tab (chip yang paling dekat secara horizontal), atas lagi menuju kolom pencarian. Bawah kembali ke daftar.
- Saat fullscreen, pencarian, tab, dan baris daftar dinonaktifkan agar D-pad tidak memindahkan fokus di bawah video.
- Fokus awal: channel pertama pada urutan tampil. Setelah fullscreen, fokus kembali ke channel terakhir jika masih lolos filter, jika tidak ke channel pertama yang tampil.

## Zapping di fullscreen

- **Atas / Channel Up** = channel berikutnya, **Bawah / Channel Down** = sebelumnya, di dalam **bagian daftar tempat channel dibuka** (urutan sama dengan daftar; tile Live TV dilewati). Di ujung daftar kembali ke sisi seberang (wrap-around). Bagian dengan satu channel tidak berpindah. Channel yang dibuka dari baris "Lanjutkan" memakai bagian asalnya terakhir ditonton.
- **Banner** di pojok kiri atas, tampil ~3 detik setiap pindah channel dan setiap OK: nomor urut di bagian itu, nama channel, "Negara (KODE) · bagian" (antena: "Antena · bagian · Live TV no. N") dan ikon jenis (antena kaki dua atau globe internet, digambar dengan Canvas).
- **Satu pemutar aktif**: pindah ke channel antena melepas ExoPlayer (`controller.stop()`), pindah ke stream melepas tuner (`antenna.stop()`) dan melepas `TvView` dari hierarki; sesi tuner dan koneksi stream tidak menumpuk (diuji: `dumpsys tv_input` 1 sesi selama antena, 0 sesudah Kembali).
- **Debounce 300 ms** pada tekan cepat berulang: hanya channel terakhir yang di-tune / dimuat; yang sebelumnya dilepas seketika dan layar menampilkan "Memuat…".
- **Error** (mati, geo-block, tuner): pesan yang sama dengan preview (warna kuning/merah) plus petunjuk "OK: coba lagi · atas/bawah: ganti channel · Kembali: daftar". Tanpa tombol, jadi atas/bawah tetap memindahkan channel dan Kembali tetap keluar. Reconnect otomatis 1× di fullscreen tetap berlaku untuk stream.
- Fokus saat Kembali ke dua kolom: channel terakhir yang ditonton; baris digulirkan ke layar jika sudah keluar dari daftar yang dirender.

### Lanjutkan

- Channel yang terakhir ditonton di fullscreen disimpan di **DataStore** (`LastWatched.kt`: id, nama, kunci bagian), setelah zapping tenang (300 ms). Yang disimpan hanya channel, bukan lokasi atau riwayat.
- Bagian **"Lanjutkan"** (satu baris) tampil **paling atas**, di atas "Untuk Anda"; OK membukanya fullscreen. Disembunyikan saat tab kategori atau pencarian aktif. Dibaca bersama channels.json sebelum frame pertama (supaya tidak menggeser daftar yang sedang tampil) dan diperbarui saat keluar fullscreen.
- Channel dicari dengan id, lalu dengan nama (id antena berubah setelah scan ulang); tile Live TV tidak pernah disimpan. Channel yang sudah tidak ada di daftar membuat "Lanjutkan" tidak tampil.

## Subtitle (terjemahan otomatis ke Indonesia)

Hanya untuk **channel stream internet di fullscreen**. Tidak jalan di channel antena (tuner), tile, preview, atau daftar: `SubtitleController` hanya aktif antara masuk dan keluar fullscreen di stream, dan menghentikan semuanya begitu pindah ke antena (kode: `SubtitleLogic.kt` keputusan murni, `SubtitleController.kt`, `LineTranslator.kt` ML Kit, `SubtitleUi.kt`).

**Keputusan saat fullscreen dibuka** (mode Otomatis; `planSubtitles`, dites di `SubtitleLogicTest`):

| | Kondisi | Hasil |
|---|---|---|
| a | `languages` channel mengandung `ind` | Tidak aktif: tanpa overlay, tanpa unduh model (berlaku walau ada track) |
| b | Ada text track berbahasa Indonesia (`id`/`in`/`ind`) | Track itu ditampilkan apa adanya |
| c | Ada text track bahasa lain (CEA-608/708, WebVTT) | Diterjemahkan ke Indonesia dengan ML Kit Translation on-device. Bahasa sumber dari tag track; kosong/`und`/tidak dikenal: dideteksi dengan ML Kit Language ID |
| d | Tidak ada text track | Menu menampilkan "Subtitle tidak tersedia" |

- **Ikon "CC"** (kotak putih kecil di samping "Negara · kategori" di info preview) hanya muncul untuk channel yang `caption`-nya `subtitle` atau `cc` (hasil audit, `docs/sources.md`). Channel `none` atau belum diaudit tidak memilikinya. Channel `cc` diputar dengan `setAllowChunklessPreparation(false)` dan `DefaultHlsExtractorFactory(0, true)` supaya CEA-608 yang tidak dideklarasikan terbaca.
- **Per baris** (channel campuran): bahasa dideteksi (ML Kit Language ID, ambang keyakinan 0,6) pada **3 baris terakhir**; baris terbaru yang panjang (≥ 30 huruf) dideteksi sendiri supaya satu kalimat asing tidak ikut tertarik ke bahasa baris sebelumnya. Hasil Indonesia **tidak diterjemahkan**; `und` atau keyakinan rendah dianggap tidak terdeteksi: **teks asli ditampilkan**, tidak diterjemahkan. **Baris yang sangat pendek** (kurang dari 3 kata) hanya diterjemahkan bila bahasa sumbernya terdeteksi dengan keyakinan **≥ 0,8**; selain itu teks aslinya ditampilkan. Bahasa sumber terjemahan = tag track bila ada, kalau tidak hasil deteksi. Kegagalan terjemahan (offline, model gagal) menampilkan teks asli; unduh model diulang paling cepat tiap 30 detik.
- **Caption siaran langsung** (ditemukan saat menguji NBC/Bloomberg di TV): (1) CEA-608 hampir selalu **HURUF KAPITAL SEMUA**, dan deteksi bahasa ML Kit tidak yakin pada teks seperti itu sehingga barisnya tidak diterjemahkan; baris yang ≥ 90% kapital diubah ke huruf kalimat (`sentenceCaseIfShouting`) untuk deteksi dan terjemahan, sedangkan teks asli yang ditampilkan tidak diubah. (2) Caption sering dilukis **per karakter** (cue tiap beberapa puluh ms), dan tiap cue membatalkan terjemahan sebelumnya sebelum selesai; baris baru diproses setelah **200 ms tanpa cue baru**, dan hanya baris yang sudah stabil masuk ke riwayat deteksi. Selama menunggu, teks sebelumnya tetap tampil.
- **Model bahasa** diunduh saat pertama dibutuhkan (satu per bahasa sumber, ±30 MB, dan model Indonesia): overlay menampilkan "Mengunduh paket bahasa…" (kuning). Yang diunduh hanya file model; teks subtitle tidak pernah dikirim ke mana pun. **Cache terjemahan per baris** dan translator dilepas (`close`) saat pindah channel, keluar fullscreen, dan saat app ke belakang.
- **Overlay**: teks putih 28 sp di pelat hitam 65% bertepi bulat, di tengah bawah dengan jarak 64 dp kiri/kanan dan 48 dp bawah (aman dari overscan). Tidak fokus dan tidak memakai tombol, jadi banner channel (pojok kiri atas) dan zapping atas/bawah tidak terganggu. Subtitle bawaan `PlayerView` disembunyikan; semua subtitle digambar oleh overlay ini.
- **Menu subtitle**: **kanan** di fullscreen (hanya di stream internet) membuka panel di sisi kanan: **Otomatis** (bawaan, aturan di atas) / **Mati** / **Asli** (track Indonesia bila ada, kalau tidak track pertama, tanpa terjemahan) / **Selalu terjemahkan** (track pertama non-Indonesia, mengabaikan `languages`). Di dalam menu: atas/bawah memilih, OK menerapkan, kiri atau Kembali menutup (Kembali pertama hanya menutup menu); Channel Up/Down tetap pindah channel dan menutup menu. Baris bawah menu menjelaskan keadaan ("Subtitle tidak tersedia", "Channel berbahasa Indonesia: tanpa terjemahan", "Diterjemahkan ke Indonesia dari Inggris", ...). Pilihan disimpan di DataStore (`settings`, kunci `subtitle_mode`) dan berlaku untuk semua channel.
- **Memori** (TV 953 MB RAM, ±190 MB tersedia): app yang memutar stream HLS fullscreen dengan penerjemah aktif PSS ±125–133 MB (`dumpsys meminfo`), tanpa selisih yang terukur dibanding channel tanpa terjemahan; model ML Kit dipakai lewat file pemetaan dan dilepas bersama translator. ABI `x86` dibuang dari APK (`abiFilters` armeabi-v7a dan arm64-v8a), APK debug ±47 MB.
- **Cara menguji** (tidak ada channel di daftar yang pasti punya subtitle): `SubtitleProbeActivity` (debug saja) memutar stream uji Apple (WebVTT banyak bahasa) lewat `SubtitleController`: `adb shell am start -n com.projectyaya.tvchannel/.SubtitleProbeActivity --es lang eng [--es mode Original] [--ez menu true] [--es sample '"kalimat 1|kalimat 2"']`; `--es sample` menjalankan deteksi dan terjemahan per baris dan mencatatnya di logcat tag `SubtitleProbe`.
- **Batasan**: HLS hanya menampilkan CEA-608/708 bila manifest mendeklarasikan `CLOSED-CAPTIONS`; yang tidak dideklarasikan tidak terlihat sebagai text track. Subtitle bergambar (bitmap) tidak didukung. Terjemahan mesin kata per kalimat bisa janggal (mis. "Ya" pendek yang terdeteksi Prancis menjadi "Adalah").

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
3. Jika gagal: pesan error (kuning/merah) di tengah layar tanpa tombol (lihat "Zapping di fullscreen"). **OK** memuat ulang dari tepi live dan mereset jatah reconnect, atas/bawah pindah channel, **Kembali** ke dua kolom (preview menampilkan pesan error channel itu).

App tidak boleh crash karena error stream: semua kegagalan pemutaran masuk ke status di atas.

## Belum dicakup / batasan

- Diuji di TV SWTV_24AE_FHD (Android 14, RAM ±1 GB): tampilan, preview, tab kategori, pencarian dengan keyboard virtual, bagian grup dan label catatan. Perilaku di TV lain belum diketahui.
- Tidak ada favorit atau EPG. Yang diingat di luar sesi hanya channel terakhir ditonton ("Lanjutkan"), bukan posisi daftar.
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
