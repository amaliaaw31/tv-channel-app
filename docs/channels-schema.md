# Skema channels.json

File: `app/src/main/assets/channels.json` (dibaca dari assets lokal; nanti dipindah ke URL raw GitHub).
Kode acuan: `Channels.kt` (`parseChannels`, `groupByCountry`).

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

Lima channel uji publik (nama diawali `[TEST]`). Kode negara dan kategori hanya variasi untuk menguji pengelompokan, bukan asal stream yang sebenarnya. Tes `ChannelsTest` memastikan file ini valid seluruhnya.
