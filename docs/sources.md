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

## Batasan

- Lolos tes berarti playlist terbaca saat itu, bukan bahwa siarannya legal atau akan tetap hidup. Stream sering mati; jalankan ulang kurasi berkala.
- Penilaian "resmi" di atas adalah tebakan dari nama domain, bukan konfirmasi dari pemilik channel.
- Tes dari VPS di Singapura berbeda dengan dari TV (wilayah dan decoder): selalu cek Tier B langsung di TV dengan `AuditActivity`. Contohnya RCTI/GTV/Indosiar: 403 dari VPS tampak seperti pembatasan wilayah, tetapi di TV ternyata timeout karena IP mentahnya tidak terjangkau.
- Stasiun TV nasional besar (RCTI, GTV, Indosiar, SCTV, ANTV, tvOne, Kompas TV) saat ini tidak punya sumber yang lolos kriteria Tier A/B di iptv-org.
