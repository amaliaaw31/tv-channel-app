# Skema channels.json

File: `data/channels.json`, dihasilkan skrip `tools/build-channels` (lihat `sources.md`), bukan diedit manual.
Kode acuan: `Channels.kt` (`parseChannels`, `pickChannels`, `loadChannels`, `groupByCountry`).

## Pemuatan di app

Urutan sumber saat app dibuka; sumber dipakai hanya jika terbaca minimal satu channel valid:

1. **Remote**: `https://raw.githubusercontent.com/amaliaaw31/tv-channel-app/main/data/channels.json` (timeout sambung 5 dtk, baca 10 dtk). Jika berhasil, hasilnya menimpa cache.
2. **Cache lokal**: `filesDir/channels-cache.json`. Ditulis lewat file sementara lalu rename, dan tidak pernah ditimpa oleh unduhan yang rusak atau kosong.
3. **Bundled**: salinan `data/channels.json` yang ikut di APK (`assets/channels.json`) saat build.

Splash ditahan sampai pemuatan selesai, jadi saat GitHub tidak terjangkau start bisa tertunda sampai ±5 detik.

**Repo private butuh token.** Raw URL repo private mengembalikan 404 tanpa token, sehingga app memakai cache/bundled. Untuk mengaktifkan remote:

1. GitHub → Settings → Developer settings → Fine-grained tokens: akses hanya repo `tv-channel-app`, izin *Contents: Read-only*.
2. Di VPS, ketik sendiri (jangan ditempel ke chat): `echo 'channels.token=TOKEN_ANDA' >> ~/projectyaya/tv-channel-app/local.properties` (file ini ada di `.gitignore`).
3. Build ulang dan install. Token ikut tertanam di APK (`BuildConfig.CHANNELS_TOKEN`), jadi cukup untuk app pribadi; cabut token kalau APK pernah bocor.

Tanpa token, perubahan data baru sampai ke TV lewat build ulang (bundled).

Isi file adalah **array JSON**, satu objek per channel.

## Field

| Field | Tipe | Wajib | Aturan |
|---|---|---|---|
| `id` | string | ya | Unik. Duplikat dilewati (yang pertama dipakai). |
| `name` | string | ya | Nama tampil di daftar dan info preview. |
| `country` | string | ya | Kode ISO 3166-1 alpha-2, **huruf kapital** (`ID`, `JP`, `US`). `idn` atau `id` ditolak. |
| `category` | string | ya | Teks bebas (`news`, `movies`, ...). Ditampilkan di info preview. |
| `streamUrl` | string | ya | Harus diawali `https://` atau `http://`. |
| `logo` | string | tidak | URL gambar. Disimpan di model tetapi belum dipakai UI. |
| `headers` | objek string→string | tidak | Header HTTP tambahan untuk stream, mis. `User-Agent`, `Referer`. |

Nilai string dipangkas spasi di tepi. Nilai `null` atau key yang tidak ada dianggap kosong.

## Perilaku pembacaan

- JSON yang rusak (bukan array, sintaks salah) membuat pembacaan gagal. UI menampilkan "Gagal membaca channels.json".
- Entri tidak valid (field wajib kosong, negara bukan 2 huruf kapital, URL bukan http/https, bukan objek) atau `id` ganda **dilewati** dan dicatat di Logcat dengan tag `Channels`. Entri lain tetap tampil.
- Jika tidak ada entri valid, UI menampilkan "Tidak ada channel yang valid di channels.json".
- `headers`: nilai kosong dibuang. Semua header dikirim lewat `setDefaultRequestProperties`; `User-Agent` juga di-set sebagai user agent factory agar tidak tertimpa.
- Stream `http://` diizinkan (lihat cleartext di `ui-spec.md`).

## Contoh

```json
[
  {
    "id": "test-apple-16x9",
    "name": "[TEST] Apple BipBop 16:9",
    "country": "ID",
    "category": "test",
    "streamUrl": "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"
  },
  {
    "id": "test-unified-tears",
    "name": "[TEST] Tears of Steel (with headers)",
    "country": "ID",
    "category": "movies",
    "logo": "https://example.com/logo.png",
    "streamUrl": "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
    "headers": {
      "User-Agent": "TVChannelApp/0.1",
      "Referer": "https://example.com/"
    }
  }
]
```

## Isi saat ini

Hasil skrip dari iptv-org (angka terbaru ada di `sources.md`). Contoh di atas diambil dari 5 channel uji lama (nama diawali `[TEST]`) yang sudah tidak ada di data. Tes `ChannelsTest` memastikan `data/channels.json` valid seluruhnya (tanpa entri yang dilewati) dan menguji urutan remote → cache → bundled.
