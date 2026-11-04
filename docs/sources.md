# Sumber data channel

Daftar channel app **dikurasi manual**, bukan lagi dump massal. Alurnya:

```
curated_wanted.json ──curate.py──▶ data/curated.json ──(tiers.json)──finalize.py──▶ data/channels.json   (Tier A + B, masuk app)
   nama yang dicari      cocokkan + tes stream      semua kandidat + status                └──────────────▶ data/excluded.json  (Tier C + tidak ditemukan, dengan alasan)
```

`data/channels.json` dan `data/excluded.json` dihasilkan skrip; jangan diedit manual (ubah `tiers.json` atau `curated_wanted.json`, jalankan ulang).

## Sumber

- **iptv-org** (`https://iptv-org.github.io/api/`): `channels`, `streams`, `blocklist`. Indeks komunitas; mereka tidak menyimpan atau menjamin stream-nya dan **tidak menandai mana yang resmi**.
- **Satu pengecualian di luar iptv-org**: TVRI Sport memakai `https://ott-balancer.tvri.go.id/live/eds/SportHD/hls/SportHD.m3u8`. iptv-org tidak punya feed ini (hanya IP mentah `202.150.172.59` dan `dens.tv`). Jalur ini ditemukan dengan memeriksa server TVRI mengikuti pola feed lain (`.../live/eds/<Nama>/hls/<Nama>.m3u8`); cuplikan gambarnya menampilkan logo TVRI SPORT. Dicatat sebagai `overrides` di `tiers.json`.

## Kurasi (`curate.py`)

Input: `tools/build-channels/curated_wanted.json` (nama yang dicari, varian, negara, grup, preferensi).

1. Buang channel di blocklist, yang ditutup (`closed`), dan NSFW.
2. Cocokkan nama dengan toleransi: huruf besar/kecil, aksen, spasi, tanda baca, "HD/SD/TV/Channel", bagian dalam kurung. Huruf non-Latin tidak dihapus (`MBC忠北` bukan `MBC`).
3. Urutan kecocokan: nama channel yang sama (≥90) → judul stream yang sama (mis. "Al Jazeera English" adalah feed di channel "Al Jazeera"; TVRI daerah adalah feed di channel "TVRI") → kecocokan sebagian.
4. Bila ada beberapa versi: utamakan yang cocok `prefer` (negara/nama), lalu yang punya stream, lalu yang hidup. Versi lain dicatat di `alternatives`.
5. Tiap kandidat dites dari VPS (**Singapura**, timeout 5 dtk, maks. 4 stream): `hidup` (HTTP 200 + playlist HLS), `geo-block` (HTTP 403/451), `mati` (lainnya), `tanpa stream`. Label **Geo-blocked** dari iptv-org dicatat terpisah (`geoLabel`).
6. Yang tidak ditemukan di iptv-org tidak dimasukkan; dicatat di `notFound`.

7. Bahasa siaran: `languages` (ISO 639-3) diambil dari `feeds.json` iptv-org per entri (aturan di `docs/channels-schema.md`), diteruskan `finalize.py` ke `channels.json` bila ada. `python3 tools/build-channels/curate.py --languages` mengisi ulang hanya bahasa di `data/curated.json` tanpa mengetes ulang stream (cepat, lalu jalankan `finalize.py`).

Hasil tes dari VPS bisa berbeda dengan dari TV (wilayah dan decoder), jadi status bukan keputusan akhir.

## Tier (`tiers.json` + `finalize.py`)

| Tier | Arti | Masuk app? |
|---|---|---|
| **A** | Aktif. Sumber hidup di tes dan domainnya dianggap resmi, atau diterima pemilik proyek (lihat tabel di bawah). | ya |
| **B** | Aktif dengan label **"perlu dicek di TV"** (`note` di data, tampil di info preview). Gagal/terblokir dari VPS, atau domain sumber kurang jelas. | ya |
| **C** | Sisanya. Disimpan di `data/excluded.json` dengan alasan: `IP mentah`, `agregator`, `tanpa stream`, `mati (...)`, `geo-block (...)`, `tidak dipilih (tier C)`, `tidak ditemukan`. | **tidak** |

Keputusan tier ditentukan pemilik proyek per channel (daftar id di `tiers.json`). Aturan otomatis: entri berkelompok `TVRI Daerah` selalu Tier A. Hasil terakhir (2026-11-02): **54 channel di app (A=46, B=8)**, 67 di `excluded.json`. Alasan manual (mis. hasil cek di TV) bisa ditulis di `exclude_reasons` pada `tiers.json`; ia menggantikan alasan otomatis.

### Hasil cek di TV (2026-11-02)

Dijalankan dengan `AuditActivity` di TV SWTV_24AE_FHD (Indonesia), 57 channel, batas 25 detik per channel:

- **Tier A: 46 dari 46 hidup** (termasuk 30 TVRI Daerah).
- **Tier B: 7 hidup** (Cartoonito, CNA, CNN International, Euronews English, NBC News NOW, NHK World-Japan, TVRI Sport). **Olympic Channel**: "Dibatasi wilayah" (403 dari TV juga). **RCTI, GTV, Indosiar**: timeout 25 detik; sumbernya IP mentah `202.150.172.59` (http) tidak terjangkau dari TV, jadi bukan soal pembatasan wilayah. **Ketiganya kemudian dipindah ke Tier C** (keputusan pemilik proyek), dengan alasan di `exclude_reasons`. Akibatnya grup MNC kosong dan tidak tampil; Nasional tersisa Garuda TV dan Nusantara TV. "RCTI HD", "GTV HD", dan "Indosiar HD" di iptv-org adalah **judul stream** dari channel yang sama (`RCTI.id`, `GTV.id`, `Indosiar.id`), bukan channel lain, dan tidak ada stream tanpa channel untuk mereka. Seluruh sumbernya sudah dicoba di TV (2026-11-02) dan **gagal**: IP `202.150.172.59` timeout untuk ketiganya; `mncmedia.malingtv.workers.dev` (proxy pihak ketiga tidak resmi) untuk RCTI "Dibatasi wilayah" (451) dan untuk GTV 404. Tidak ada sumber lain di iptv-org, jadi ketiganya tetap Tier C sampai ada sumber baru.

### Domain sumber yang dianggap resmi

Penilaian dari **nama domain**; belum diverifikasi ke pemilik channel kecuali disebut.

| Penilaian | Domain → channel |
|---|---|
| Resmi: milik stasiun/pemerintah | `ott-balancer.tvri.go.id` → TVRI HD, TVRI World, TVRI Daerah ×30, TVRI Sport · `getaj.net` → Al Jazeera English · `medya.trt.com.tr` → TRT World · `cbsnstream.cbsnews.com` → CBS News 24/7 |
| Grup media yang sama dengan channel (dugaan) | `video.detik.com` → CNN Indonesia, CNBC Indonesia · `edge.medcom.id` → Metro TV |
| Mitra distribusi resmi (FAST/OTT) (dugaan) | `amagi.tv` → DW, France 24 · `wurl.com` → Bloomberg TV · `tubi.video` → ABC News Live · `mediatailor...amazonaws.com` → Red Bull TV |
| CDN tanpa nama pemilik (Tier B) | `cloudfront.net` → NBC News NOW, CNN International, CNA · `akamaized.net` → Cartoonito (IT), Olympic Channel |
| **Diterima pemilik proyek, belum diverifikasi (Tier A)** | `hgmtv.com` → Garuda TV · `nusantaratv.siar.us` → Nusantara TV · `tvstreamcast.com` → Indonesiana.TV |
| **Kurang jelas, perlu dicek (Tier B)** | `cdn.pishow.tv` → NHK World-Japan · `a-cdn.klowdtv.com` → Euronews English |

Domain yang **tidak** dianggap resmi (alasan masuk Tier C): alamat IP mentah, dan agregator/siaran ulang seperti `freem3u.xyz`, `jmp2.uk`, `aynascope.net`, `cinerama.uz` (daftar di `aggregator_domains`, `tiers.json`). Satu IP yang sama (`23.237.104.106`) menyiarkan banyak channel berbeda, itu tanda siaran ulang pihak ketiga.

### Menambah channel baru (wajib)

1. Tambahkan namanya di `curated_wanted.json`, jalankan `curate.py`, lihat tabel `--report`.
2. **Cek domain sumber** (kolom Domain). IP mentah atau agregator → Tier C. Domain milik stasiun, grup medianya, atau mitra distribusi resmi → boleh Tier A. Ragu → Tier B.
3. Tambahkan id-nya ke `A` atau `B` di `tiers.json` (mereka yang belum dipilih otomatis Tier C), jalankan `finalize.py`.
4. Catat domain barunya di tabel di atas bila dianggap resmi.

## Audit caption (`tools/audit-captions/audit.py`)

Mengukur dari VPS (tanpa TV) channel mana di `channels.json` yang membawa caption:

```
python3 tools/audit-captions/audit.py                       # manifest: master + media playlist pertama
python3 tools/audit-captions/audit.py --segments --samples 8 --write   # + isi video (CEA-608/708), tulis data/captions.json
python3 tools/build-channels/finalize.py                    # captions.json -> kolom "caption" di channels.json
```

- **subtitle**: master punya `#EXT-X-MEDIA TYPE=SUBTITLES` (WebVTT). **cc**: `CLOSED-CAPTIONS` dideklarasikan, atau (`--segments`) segmen video membawa data caption ATSC A/53 ("GA94" di SEI H.264/H.265) yang **berisi teks**. **none**: keduanya tidak ada. Channel yang tidak terbaca dari VPS (geo-block, mati) tidak ditulis; nilai lamanya tetap, atau tidak ada (tidak diaudit).
- Caption siaran langsung datang dan pergi (jeda iklan, program tanpa caption): satu cuplikan bisa salah. Pakai `--samples 8` (segmen terbaru), dan `--write` mempertahankan `cc` yang lama bila encoder caption masih ada di video tetapi cuplikan ini tidak berisi teks.
- Tidak terjangkau audit ini: teletext/DVB subtitle di MPEG-TS dan subtitle bergambar (Media3 tidak membacanya juga).
- **Hasil 2026-11-03** (53 channel terbaca, 1 geo-block): subtitle 3 (CBS News 24/7, NBC News NOW, Red Bull TV; NBC dan Red Bull juga mendeklarasikan CC1), cc 2 (**ABC News Live** dan **Bloomberg TV**: manifest tidak mendeklarasikan apa pun tetapi videonya membawa CEA-608/708 berisi teks), none 48. Olympic Channel (HTTP 403 dari VPS) tidak diaudit. Cartoonito punya blok caption kosong (hanya padding) jadi none. Seluruh channel Indonesia (TVRI, Metro TV, CNN/CNBC Indonesia, Garuda, Nusantara, Indonesiana) none.
- **Media3 dan CEA-608 yang tidak dideklarasikan** (diuji di TV, ABC News Live dan Bloomberg TV, ExoPlayer 1.11.1): dengan pengaturan bawaan **tidak ada track teks** (hanya video, audio, ID3) walau segmennya membawa caption, karena HLS memakai *chunkless preparation* yang mengambil daftar track dari playlist. `DefaultHlsExtractorFactory(0, true)` saja **belum cukup**. Yang berhasil: `HlsMediaSource.Factory(...).setAllowChunklessPreparation(false).setExtractorFactory(DefaultHlsExtractorFactory(0, true))`, lalu track `application/cea-608` muncul dan baris captionnya terbaca dan diterjemahkan (mis. ABC: "Itu pertanyaan yang sangat nyata yang Anda minta."). Diuji di TV: ABC News Live, Bloomberg TV, dan NBC News NOW menampilkan caption yang diterjemahkan; CBS News 24/7 (WebVTT `x-media3-cues`) dan Red Bull TV (WebVTT + CC1) juga. Harganya persiapan stream menunggu satu segmen dulu, jadi app memakai pengaturan ini **hanya untuk channel `caption: "cc"`** (`PlayerController.start`); yang lain tetap bawaan. Hanya kanal CC1 yang dibaca.

## Siaran antena (tuner TV)

RCTI, GTV, MNCTV, Indosiar, SCTV, ANTV, tvOne, dan lainnya tidak punya sumber stream resmi yang berfungsi (lihat Tier C di atas), tetapi disiarkan **gratis lewat udara** dan TV ini punya tuner. Itu sumber legal yang tidak bergantung pada stream pihak ketiga. App memutar channel antena fullscreen lewat `TvView` (preview kecil opsional, bawaan mati) dan punya tile "TV Antena" yang membuka aplikasi Live TV bawaan.

**Mendeteksi aplikasi Live TV di sebuah TV** (jangan menebak; dilakukan 2026-11-03 di SWTV_24AE_FHD):

```bash
adb -s $T shell pm list packages -f | grep -i -E 'cltv|livetv|tvcenter|tvinput'     # kandidat paket
adb -s $T shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER
adb -s $T shell dumpsys package <paket> | grep -E 'Action: |Category: '             # filter intent yang dideklarasikan
adb -s $T shell input keyevent KEYCODE_TV                                            # yang dibuka tombol TV = Live TV sebenarnya
adb -s $T shell dumpsys activity activities | grep topResumedActivity               # lihat package/activity-nya
```

Hasil di TV ini: tombol TV membuka **`com.cltv.hybrid/com.iwedia.cltv.MainActivity`** (LiveTV by iWedia, v3.6 dari Play Store, di atas `CommonLiveTvO21` bawaan). Paket MediaTek (`com.mediatek.wwtv.tvcenter`, `.plus`) terpasang tetapi activity-nya tidak ada untuk pengguna ini.

**Membuka satu channel lewat Intent (gagal; yang akhirnya dipakai adalah `TvView`, di bawah):**

- `adb shell content query --uri content://android.media.tv/channel` mengembalikan **kosong** dari shell: TvProvider menyaring hasil bagi pemanggil yang tidak punya izin. Izin yang dibutuhkan, `android.permission.READ_TV_LISTINGS`, berstatus **`dangerous`** (izin runtime biasa) dan **bisa diberikan** ke app kita (`pm grant` berhasil setelah dideklarasikan). Dengan izin itu app membaca **55 channel hasil scan** dari TvProvider (izin ini sekarang dipakai app, lihat TvView di bawah).
- Hasil scan di TV ini (nomor Live TV; bisa berubah setelah scan ulang): Trans TV HD 20, Trans 7 HD 21, Metro TV HD 22, SCTV HD 23, Indosiar HD 24, ANTV HD 26, tvOne HD 27, RCTI HD 28, MNCTV HD 29, GTV HD 30, iNews HD 31; juga TVRI, Kompas TV, CNN/CNBC Indonesia, Garuda TV, Nusantara TV, MDTV, dan lainnya.
- `ACTION_VIEW` dengan `content://android.media.tv/channel/<id>` (id TvProvider, mis. RCTI HD = 452): satu-satunya penangan adalah LiveTV (`com.cltv.hybrid`). Ia menerima intent dan membawa dirinya ke depan, tetapi **hanya membuka berandanya dan tidak menyetel channel** (diuji 4 kali, termasuk dari kondisi bersih dan menunggu 28 detik). Lognya menunjukkan ia memakai id internal MediaTek (`channelId 7864449`), bukan `_id` TvProvider. Activity lain yang dideklarasikan (`CanaliTvActivity`, Live TV Plus MediaTek) tidak ada ("Activity class does not exist").
- **`TvView` berhasil** (2026-11-03). `TvView.tune(inputId, TvContract.buildChannelUri(id))` dari app kita pada input `com.mediatek.tvinput/.tuner.TunerInputService/HW0`: `dumpsys tv_input` mencatat sesi milik uid app (`callingUid`), callback `onVideoAvailable` terpanggil, dan tidak ada `onConnectionFailed`. Jadi Android TV ini mengizinkan app pihak ketiga memakai tuner lewat API resmi, tanpa izin istimewa selain `READ_TV_LISTINGS`.
- **Yang tidak bisa diperiksa dari VPS:** gambarnya. `screencap` mengembalikan kosong (0 byte, atau gambar hitam) begitu ada `TvView` di jendela, dan lapisan `SurfaceView`-nya tidak menerima buffer lewat Surface kita (jalur video tuner perangkat keras). Yang dipakai sebagai bukti: callback, sesi di `dumpsys tv_input`, dan pohon aksesibilitas (`uiautomator dump`) untuk isi UI. Posisi dan ukuran video di slot preview perlu dilihat langsung di TV.

## Menjalankan

```bash
cd ~/projectyaya/tv-channel-app
python3 tools/build-channels/curate.py --search     # cek pencocokan nama saja
python3 tools/build-channels/curate.py              # cocokkan + tes stream -> data/curated.json (beberapa menit)
python3 tools/build-channels/curate.py --report     # tabel: hidup / gagal / tidak ditemukan
python3 tools/build-channels/finalize.py            # -> data/channels.json + data/excluded.json
python3 -m unittest discover -s tools/build-channels
```

Hanya butuh Python 3 (stdlib). Setelah `finalize.py`, build ulang app (data ikut di APK sebagai cadangan offline).

## Alat lain

- `common.py`: fungsi bersama `curate.py` dan `finalize.py` (urutan stream, deteksi HLS, penulisan file). Generator massal lama (dump seluruh kategori iptv-org, 1.945 channel, beserta `prune_audit.py`) sudah dihapus karena sumbernya tidak jelas dan start app di TV sangat lambat; masih ada di riwayat git (commit `c6b888c`, `e578009`).
- `AuditActivity` (hanya build debug): memutar tiap channel di `channels.json` di TV (bisu) dan mencatat hasilnya di `files/audit.jsonl`: `adb shell am start -n com.projectyaya.tvchannel/.AuditActivity`. Berguna untuk memeriksa Tier B langsung di TV (±1 menit per 10 channel). **Kandidat baru bisa dicoba dulu** tanpa masuk data: taruh daftar uji (skema yang sama dengan `channels.json`) di `files/audit-input.json` (`adb push` ke `/data/local/tmp/`, lalu `adb shell run-as com.projectyaya.tvchannel cp /data/local/tmp/audit-input.json files/`), hapus `files/audit.jsonl`, jalankan `AuditActivity`; hapus `audit-input.json` setelahnya agar audit berikutnya kembali memakai data bundled. Hasilnya dipakai untuk memutuskan tier secara manual di `tiers.json`.

- **Daftar uji di TV (debug saja):** `channels-override.json` di `files/` menggantikan seluruh daftar (skema sama dengan `channels.json`): `adb push x.json /data/local/tmp/` lalu `adb shell run-as com.projectyaya.tvchannel cp /data/local/tmp/x.json files/channels-override.json`; hapus berkas itu untuk kembali ke daftar normal. Berguna untuk menguji channel mati/geo-block, error saat zapping, dan stream uji. Tidak aktif di build rilis (`BuildConfig.DEBUG`).

## Rilis (cadangan di luar adb)

- `tools/release.sh`: menjalankan tes, membangun dan **menandatangani** APK rilis, memverifikasi tanda tangannya, menaruhnya di `release/tvchannel-<versi>.apk` (git-ignored) dan mencetak sha256 serta caranya sampai ke TV. Versi dari `versionName` di `app/build.gradle.kts` (naikkan `versionCode` dan `versionName` sebelum rilis baru).
- **Keystore** ada di `~/.tvchannel-release/release.jks` (di luar repo; jalur dan kata sandi di `local.properties`, git-ignored, kunci `release.keystore`, `release.alias`, `release.password`). **Cadangkan file itu dan kata sandinya**: tanpa kunci yang sama, APK yang terpasang tidak bisa diperbarui (harus dicopot dulu, data app hilang). APK debug (kunci debug) dan rilis (kunci ini) saling tidak bisa menimpa; pindah dari satu ke yang lain butuh `adb uninstall com.projectyaya.tvchannel` sekali.
- Rilis belum diminify (`isMinifyEnabled = false`): R8 dengan ML Kit dan Media3 belum diuji di TV. Ukuran ±41 MB. Bagian debug (`AuditActivity`, probe, override daftar) tidak ikut.
- Jalur ke TV: `adb install -r`, atau **Downloader** lewat Tailscale: `python3 -m http.server 8000 --bind 100.82.45.9 --directory release` lalu buka `http://100.82.45.9:8000/tvchannel-<versi>.apk` di Downloader (TV harus ada di Tailscale). GitHub Release bisa dipakai karena repo publik; `gh` belum terpasang di VPS, jadi unggah lewat web GitHub.
- Repo publik, jadi `channels.token` tidak diperlukan dan APK tidak memuat token. Bila repo suatu saat dijadikan private, token di `local.properties` **ikut tertanam di APK**: jangan membagikan APK itu.

## Pemantau kesehatan channel

`python3 tools/health-check/check.py` memeriksa tiap stream di `channels.json` dari VPS (playlist HLS + 16 KB segmen pertama) dan membandingkannya dengan hasil sebelumnya di `~/.tvchannel-health/last.json`: `ok`, `geo` (403/451), `dead`. Mencetak channel yang **baru bermasalah** atau **pulih**, dan keluar dengan kode 1 bila ada yang baru jatuh. Contoh pertama (2026-11-03): 53 ok, Olympic Channel `geo` (403 dari VPS), TVRI Bengkulu sempat 502 (sementara). Dijalankan terjadwal dengan cron (tidak dipasang otomatis): `0 6 * * * cd <repo> && python3 tools/health-check/check.py >> ~/.tvchannel-health/cron.log 2>&1`. Hasil dari VPS berbeda dari TV (wilayah, decoder): konfirmasi di TV dengan `AuditActivity` sebelum membuang channel; mengubah daftar tetap lewat `curated_wanted.json`/`tiers.json`.

## Batasan

- Lolos tes berarti playlist terbaca saat itu, bukan bahwa siarannya legal atau akan tetap hidup. Stream sering mati; jalankan ulang kurasi berkala.
- Penilaian "resmi" di atas adalah tebakan dari nama domain, bukan konfirmasi dari pemilik channel.
- Tes dari VPS di Singapura berbeda dengan dari TV (wilayah dan decoder): selalu cek Tier B langsung di TV dengan `AuditActivity`. Contohnya RCTI/GTV/Indosiar: 403 dari VPS tampak seperti pembatasan wilayah, tetapi di TV ternyata timeout karena IP mentahnya tidak terjangkau.
- Stasiun TV nasional besar (RCTI, GTV, Indosiar, SCTV, ANTV, tvOne, Kompas TV) saat ini tidak punya sumber yang lolos kriteria Tier A/B di iptv-org.
