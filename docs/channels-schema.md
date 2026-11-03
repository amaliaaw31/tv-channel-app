# Skema channels.json

File: `data/channels.json`, dihasilkan `tools/build-channels/finalize.py` dari kurasi Tier A + B (lihat `sources.md`), bukan diedit manual.
Kode acuan: `Channels.kt` (`parseChannels`, `pickChannels`, `loadChannels`, `buildSections`).

## Pemuatan di app

Urutan sumber saat app dibuka; sumber dipakai hanya jika terbaca minimal satu channel valid:

1. **Remote**: `https://raw.githubusercontent.com/amaliaaw31/tv-channel-app/main/data/channels.json` (timeout sambung 5 dtk, baca 10 dtk). Jika berhasil, hasilnya menimpa cache.
2. **Cache lokal**: `filesDir/channels-cache.json`. Ditulis lewat file sementara lalu rename, dan tidak pernah ditimpa oleh unduhan yang rusak atau kosong.
3. **Bundled**: salinan `data/channels.json` yang ikut di APK (`assets/channels.json`) saat build. File lain di `data/` (`curated.json`, `excluded.json`) sengaja **tidak** ikut di APK (`ignoreAssetsPattern` di `app/build.gradle.kts`).

Splash ditahan sampai pemuatan selesai. Dengan ±54 channel, pemuatan ±1 detik di TV; saat repo private tanpa token ada tambahan ±1 detik untuk percobaan remote yang dijawab 404.

**Repo private butuh token.** Raw URL repo private mengembalikan 404 tanpa token, sehingga app memakai cache/bundled. Untuk mengaktifkan remote:

1. GitHub → Settings → Developer settings → Fine-grained tokens: akses hanya repo `tv-channel-app`, izin *Contents: Read-only*.
2. Di VPS, ketik sendiri (jangan ditempel ke chat): `echo 'channels.token=TOKEN_ANDA' >> ~/projectyaya/tv-channel-app/local.properties` (file ini ada di `.gitignore`).
3. Build ulang dan install. Token ikut tertanam di APK (`BuildConfig.CHANNELS_TOKEN`), jadi cukup untuk app pribadi; cabut token kalau APK pernah bocor.

Tanpa token, perubahan data baru sampai ke TV lewat build ulang (bundled).

Isi file adalah **array JSON**, satu objek per channel.

## Field

| Field | Tipe | Wajib | Aturan |
|---|---|---|---|
| `id` | string | ya | Unik. Duplikat dilewati (yang pertama dipakai). Feed bisa berbentuk `ChannelId#Feed` (mis. `TVRI.id#Aceh`). |
| `name` | string | ya | Nama tampil di daftar dan info preview. |
| `country` | string | ya | Kode ISO 3166-1 alpha-2, **huruf kapital** (`ID`, `JP`, `US`). `idn` atau `id` ditolak. |
| `category` | string | ya | Teks bebas (`news`, `sports`, ...). Dipakai tab kategori dan info preview. |
| `streamUrl` | string | ya | Harus diawali `https://` atau `http://`. |
| `logo` | string | tidak | URL gambar. Disimpan di model tetapi belum dipakai UI. |
| `headers` | objek string→string | tidak | Header HTTP tambahan untuk stream, mis. `User-Agent`, `Referer`. |
| `groups` | array string | tidak | Grup tampilan di daftar kanan (`Nasional`, `MNC`, `Berita`, `Publik`, `TVRI Daerah`, `Daerah`, ...). Channel muncul di **setiap** grupnya. Kosong/tidak ada = muncul di bagian negaranya. |
| `note` | string | tidak | Catatan pendek di samping nama di daftar dan di info preview, mis. `perlu dicek di TV` (Tier B). |
| `tier` | string | tidak | `A` atau `B`. Hanya untuk perkakas/audit; app mengabaikannya. |

Nilai string dipangkas spasi di tepi. Nilai `null` atau key yang tidak ada dianggap kosong. Elemen `groups` yang kosong/`null`/ganda dibuang; `groups` yang bukan array diabaikan. Key yang tidak dikenal diabaikan.

`finalize.py` hanya menulis `groups` untuk negara di `group_countries` (`tiers.json`, sekarang `ID`). Channel internasional tanpa `groups` muncul di bagian negaranya; topiknya dicari lewat tab kategori.

## antenna.json (tile "TV Antena")

File terpisah: `data/antenna.json`, ikut di APK sebagai `assets/antenna.json`. Bukan bagian dari `channels.json` dan tidak berisi stream atau id channel.

```json
{
  "tile": {"name": "TV Antena", "note": "membuka Live TV bawaan"},
  "liveTv": [
    {"package": "com.cltv.hybrid", "activity": "com.iwedia.cltv.MainActivity"}
  ],
  "channels": ["RCTI", "GTV", "MNCTV", "iNews", "Indosiar", "SCTV", "ANTV", "tvOne", "Metro TV", "Trans TV", "Trans7"]
}
```

| Field | Aturan |
|---|---|
| `liveTv` | **Wajib.** Daftar aplikasi Live TV (package + activity) yang dicoba berurutan; dipakai yang pertama ada di TV. Elemen yang tidak lengkap dibuang. Daftar kosong atau file hilang berarti tile tidak ditampilkan. |
| `tile.name` | Opsional, bawaan "TV Antena". |
| `tile.note` | Opsional, teks kecil di samping nama di daftar. |
| `channels` | Opsional. Nama channel antena yang dicari di hasil scan TV; tiap elemen berupa nama atau `{"name", "aliases": [...]}`. Pencocokan toleran: huruf besar/kecil, spasi, tanda baca, dan akhiran "HD" diabaikan ("Trans7" cocok dengan "TRANS 7 HD"). Yang tidak ada di scan dilewati. **Id channel tidak disimpan**: dibaca dari TvProvider saat berjalan (izin `READ_TV_LISTINGS`), karena berubah setelah scan ulang. |

Isi `liveTv` **dideteksi di TV, bukan ditebak** (caranya di `sources.md`). Untuk TV lain tambahkan entri di sini. Tile dan channel antena masuk ke grup `TV Nasional (Antena)` (konstanta `ANTENNA_GROUP`), selalu grup pertama setelah "Untuk Anda". Di kode keduanya berupa `Channel` dengan `streamUrl` kosong: tile punya `launch` (kandidat Live TV), channel antena punya `antenna` (`AntennaRef`: id kanal TvProvider dan id input tuner) dan `note` "no. N". Keduanya ikut tersaring oleh pencarian dan tab kategori "Umum".

## Perilaku pembacaan

- JSON yang rusak (bukan array, sintaks salah) membuat pembacaan gagal. UI menampilkan "Gagal membaca channels.json".
- Entri tidak valid (field wajib kosong, negara bukan 2 huruf kapital, URL bukan http/https, bukan objek) atau `id` ganda **dilewati** dan dicatat di Logcat dengan tag `Channels`. Entri lain tetap tampil.
- Jika tidak ada entri valid, UI menampilkan "Tidak ada channel yang valid di channels.json".
- `headers`: nilai kosong dibuang. Semua header dikirim lewat `setDefaultRequestProperties`; `User-Agent` juga di-set sebagai user agent factory agar tidak tertimpa.
- Stream `http://` diizinkan (lihat cleartext di `ui-spec.md`).

## Contoh

```json
[
  {"id": "TVRI.id#Aceh", "name": "TVRI Aceh", "country": "ID", "category": "general", "streamUrl": "https://ott-balancer.tvri.go.id/live/eds/Aceh/hls/Aceh.m3u8", "groups": ["TVRI Daerah"], "tier": "A"},
  {"id": "TVRISport.id", "name": "TVRI Sport", "country": "ID", "category": "sports", "streamUrl": "https://ott-balancer.tvri.go.id/live/eds/SportHD/hls/SportHD.m3u8", "groups": ["Publik"], "tier": "B", "note": "perlu dicek di TV"},
  {"id": "DW.de", "name": "DW", "country": "DE", "category": "news", "streamUrl": "https://example.invalid/dw.m3u8", "tier": "A"}
]
```

Satu channel per baris di berkasnya. Contoh terakhir tanpa `groups`, jadi tampil di bagian "Jerman (DE)".

## Isi saat ini

54 channel: Tier A 46 dan Tier B 8 (daftar dan alasan pemilihan di `sources.md`). Tes `ChannelsTest` memastikan `data/channels.json` valid seluruhnya (tanpa entri yang dilewati), setiap channel punya bagian dengan kunci baris yang unik, dan menguji urutan remote → cache → bundled.
