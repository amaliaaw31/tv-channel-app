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

Keputusan tier ditentukan pemilik proyek per channel (daftar id di `tiers.json`). Aturan otomatis: entri berkelompok `TVRI Daerah` selalu Tier A. Hasil terakhir (2026-11-02): **57 channel di app (A=46, B=11)**, 64 di `excluded.json`.

### Domain sumber yang dianggap resmi

Penilaian dari **nama domain**; belum diverifikasi ke pemilik channel kecuali disebut.

| Penilaian | Domain → channel |
|---|---|
| Resmi: milik stasiun/pemerintah | `ott-balancer.tvri.go.id` → TVRI HD, TVRI World, TVRI Daerah ×30, TVRI Sport · `getaj.net` → Al Jazeera English · `medya.trt.com.tr` → TRT World · `cbsnstream.cbsnews.com` → CBS News 24/7 |
| Grup media yang sama dengan channel (dugaan) | `video.detik.com` → CNN Indonesia, CNBC Indonesia · `edge.medcom.id` → Metro TV |
| Mitra distribusi resmi (FAST/OTT) (dugaan) | `amagi.tv` → DW, France 24 · `wurl.com` → Bloomberg TV · `tubi.video` → ABC News Live · `mediatailor...amazonaws.com` → Red Bull TV |
| CDN tanpa nama pemilik (Tier B) | `cloudfront.net` → NBC News NOW, CNN International, CNA · `akamaized.net` → Cartoonito (IT), Olympic Channel |
| **Diterima pemilik proyek, belum diverifikasi (Tier A)** | `hgmtv.com` → Garuda TV · `nusantaratv.siar.us` → Nusantara TV · `tvstreamcast.com` → Indonesiana.TV |
| **Kurang jelas, perlu dicek (Tier B)** | `202.150.172.59` (IP mentah) → RCTI, GTV, Indosiar · `cdn.pishow.tv` → NHK World-Japan · `a-cdn.klowdtv.com` → Euronews English |

Domain yang **tidak** dianggap resmi (alasan masuk Tier C): alamat IP mentah, dan agregator/siaran ulang seperti `freem3u.xyz`, `jmp2.uk`, `aynascope.net`, `cinerama.uz` (daftar di `aggregator_domains`, `tiers.json`). Satu IP yang sama (`23.237.104.106`) menyiarkan banyak channel berbeda, itu tanda siaran ulang pihak ketiga.

### Menambah channel baru (wajib)

1. Tambahkan namanya di `curated_wanted.json`, jalankan `curate.py`, lihat tabel `--report`.
2. **Cek domain sumber** (kolom Domain). IP mentah atau agregator → Tier C. Domain milik stasiun, grup medianya, atau mitra distribusi resmi → boleh Tier A. Ragu → Tier B.
3. Tambahkan id-nya ke `A` atau `B` di `tiers.json` (mereka yang belum dipilih otomatis Tier C), jalankan `finalize.py`.
4. Catat domain barunya di tabel di atas bila dianggap resmi.

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

## Alat lain (arsip)

- `build_channels.py`: dump massal iptv-org per kategori ke `data/bulk-channels.json` (git-ignored, **tidak dipakai app**). Dipakai juga sebagai pustaka oleh `curate.py`. Dulu (commit `c6b888c`) hasilnya 1.945 channel, dihentikan karena stream tidak jelas asalnya dan start app di TV sangat lambat.
- `AuditActivity` (hanya build debug): memutar tiap channel di `channels.json` di TV (bisu) dan mencatat hasilnya di `files/audit.jsonl`: `adb shell am start -n com.projectyaya.tvchannel/.AuditActivity`. Berguna untuk memeriksa Tier B langsung di TV (±1 menit per 10 channel). `prune_audit.py` memangkas daftar berdasarkan hasilnya (untuk `bulk-channels.json`).

## Batasan

- Lolos tes berarti playlist terbaca saat itu, bukan bahwa siarannya legal atau akan tetap hidup. Stream sering mati; jalankan ulang kurasi berkala.
- Penilaian "resmi" di atas adalah tebakan dari nama domain, bukan konfirmasi dari pemilik channel.
- Tes dari VPS di Singapura; channel Indonesia yang dibatasi wilayah (mis. RCTI, GTV, Indosiar) kemungkinan hidup di TV tapi belum terbukti.
