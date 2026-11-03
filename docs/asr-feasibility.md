# Studi kelayakan: subtitle otomatis dari audio (ASR) di VPS

**Status: hanya diukur, tidak dibangun.** Pertanyaannya: untuk channel yang tidak membawa caption (DW, France 24, Al Jazeera English, dst.), bisakah VPS ini menyalin ucapan menjadi teks secara real-time lewat whisper.cpp, lalu diterjemahkan seperti caption biasa (`docs/ui-spec.md`, "Subtitle")?

Diukur 2026-11-03. Skrip ada di `tools/asr-bench/` (`bench.py`, `chunk_bench.py`, `wer.py`; butuh checkout whisper.cpp yang sudah di-build, jalur lewat `WHISPER_DIR`).

## Lingkungan

| | |
|---|---|
| VPS | AMD EPYC-Milan, **8 vCPU** (1 thread per core, AVX2, tanpa AVX-512, tanpa GPU), RAM **22 GB** (±10 GB tersedia; 12 GB dipakai proses lain; swap 4 GB hampir penuh), disk 28 GB kosong |
| Beban awal | load average 0,2–2,0 (hampir menganggur); VPS ini juga dipakai untuk pekerjaan lain |
| Alat | whisper.cpp `482279b` (2026-10-30), CMake Release, `GGML_NATIVE=ON`, OpenMP; ffmpeg 6.1.1 |
| Model | `ggml-tiny`, `base`, `small` (multibahasa, tanpa kuantisasi); `medium` hanya sebagai pembanding akurasi |
| Sampel | 60 detik audio mono 16 kHz dari stream di `channels.json` lewat `ffmpeg -t 60`: **DW**, **France 24**, **Al Jazeera English** |

Catatan sampel: stream "DW" di daftar bertanda `deu`, tetapi isi saat diambil adalah **siaran berbahasa Inggris** (wawancara tentang Taiwan), jadi dipaksa `-l de` hasilnya campur aduk. Angka DW di bawah memakai deteksi bahasa otomatis. France 24 berbahasa Prancis dengan sisipan Inggris; Al Jazeera English berbahasa Inggris.

## Hasil 1: sekali jalan, 60 detik audio, 8 thread

RTF = waktu proses (tanpa memuat model) dibagi durasi audio; di bawah 1 berarti lebih cepat dari real-time.

| Model | RTF (DW / France 24 / AJE) | Waktu dinding 60 dtk | CPU dipakai | RAM puncak |
|---|---|---|---|---|
| tiny | 0,04 / 0,08 / 0,04 | 2,2–4,9 dtk | ±7,3 core | ±200 MB |
| base | 0,06 / 0,10 / 0,07 | 2,4–6,4 dtk | ±7,3 core | ±310–340 MB |
| small | 0,13 / 0,11 / 0,09 | 6,0–8,3 dtk | ±7,2 core | ±780–800 MB |
| medium (pembanding) | 0,39 / 0,30 / 0,39 | 19–24 dtk | ±7,4 core | ±2,1 GB |

Skala thread (AJE, RTF): tiny 0,108 → 0,062 → 0,041 (1/2/4 thread); base 0,234 → 0,137 → 0,078 → 0,068 (1/2/4/8); small 0,457 → 0,258 → 0,140 → 0,097 (1/2/4/8). Skalanya hampir linear sampai 4 thread, lalu melandai.

## Hasil 2: simulasi streaming, potongan 5 detik

Audio dipotong per 5 detik dan tiap potongan diproses sendiri (model dimuat ulang tiap panggilan, jadi CPU di bawah sedikit berlebih). `proc` = waktu proses satu potongan; **RTF stream = proc / 5 dtk**, harus < 1 supaya tidak tertinggal.

| Model, pengaturan | proc rata-rata (maks) | RTF stream | CPU berkelanjutan | RAM |
|---|---|---|---|---|
| base, 8 thread | 0,70 (0,94) dtk | 0,14 | 1,1 core | 285 MB |
| small, 8 thread | 2,01 (2,36) dtk | 0,40 | 3,1 core | 751 MB |
| small, 4 thread | 3,21 (3,75) dtk | 0,64 | ±2,6 core (perkiraan) | 750 MB |
| **small, 8 thread, `-ac 400`** | **0,86 (1,05) dtk** | **0,17** | **1,3 core** | 706 MB |
| small, 4 thread, `-ac 400` | 1,5 dtk (60 dtk dalam 17,8 dtk, termasuk memuat model) | ±0,3 | 0,9 core | 705 MB |
| small, France 24, 8 thread | 2,44 (**6,37**) dtk | 0,49 | | |
| **medium**, France 24, 8 thread | **5,74 (6,74) dtk** | **1,15** | ≥ 8 core | 2,1 GB |

Penjelasan: whisper selalu mengodekan jendela 30 detik walau potongannya 5 detik, jadi sebagian besar biaya terbuang. `-ac 400` (konteks audio dipangkas ±8 detik) memangkas waktu 57% tanpa penurunan akurasi yang terukur (WER 24% vs 27%, selisih itu dalam batas derau). "Perkiraan" = dihitung dari data lain, bukan diukur langsung.

## Akurasi

Tidak ada transkrip acuan manusia. Pembandingnya model `medium` pada audio yang sama, jadi angka di bawah adalah **kesepakatan dengan medium, bukan WER sebenarnya**. Ditambah jumlah kata (ucapan yang hilang atau diulang) dan bacaan langsung.

| Sampel | Kata (medium) | tiny | base | small |
|---|---|---|---|---|
| DW (Inggris, `-l auto`) | 129 | 139 kata, selisih 12% | 133 kata, selisih 5% | 131 kata, selisih 3% |
| Al Jazeera English | 164 | 118 kata, 29% | 187 kata, 23% | 111 kata, 32% |
| France 24 (Prancis) | 93 | **14 kata**, 97% | **27 kata**, 90% | 85 kata, 73% |

Bacaan langsung:
- **Inggris (DW, Al Jazeera): `base` dan `small` terbaca baik** dan kalimatnya utuh ("...it's still possible for peace for unification, but a lot of people may not agree with me, especially the younger people..."). `tiny` salah di tempat yang berarti ("have a different status to us China" vs "stance towards China"). Selisih 23–32% di AJE bukan karena kalimat salah: `small` **melewatkan satu bagian ucapan** (111 vs 164 kata) dan `base` menambah pengulangan (187 kata). Ini perilaku whisper pada audio panjang tanpa penjaga; hasil 60 detik tunggal sangat berisik.
- **Prancis: model kecil tidak layak.** `tiny` masuk **loop halusinasi** ("de la pandémie. La pandémie, de la pandémie...") dan hanya mencatat 14 dari 93 kata; `base` berhenti setelah satu kalimat (27 kata); `small` memuat sebagian besar tetapi dengan salah dengar ("Les Poles", "la Texas", "les Démocrats n'auraient besoin d'en mettre un plus fort"). Hanya `medium` yang cukup baik, dan ia bukan real-time di sini (lihat atas). Jadi untuk bahasa non-Inggris, minimal `small` sangat berisiko dan `medium` baru bisa dipercaya.
- Potongan 5 detik berdiri sendiri tidak menurunkan hasil bahasa Inggris (selisih 23–27%, sama dengan 60 detik utuh). Jendela 10 detik yang saling menimpa menggandakan kata (312 vs 164): butuh logika menghapus tumpang tindih. Potongan 5 detik di France 24 dengan `small` memuncak 6,4 dtk pada satu potongan (whisper mengulang dengan suhu lebih tinggi bila ragu): satu potongan lambat itu cukup untuk tertinggal.
- **Tidak diukur:** halusinasi pada musik, jingle, dan jeda iklan (whisper terkenal mengarang teks "Thank you for watching" pada senyap/musik; whisper.cpp punya VAD, tidak dicoba); nama orang dan istilah; ucapan bertumpuk.

## Perkiraan latensi total (streaming per potongan 5 detik)

| Tahap | Waktu |
|---|---|
| Menunggu potongan penuh | 5 dtk (rata-rata 2,5 dtk bila ucapan jatuh di tengah) |
| Whisper (base 8 thread / small `-ac 400` 8 thread) | 0,7 / 0,9 dtk (2,0 dtk small tanpa `-ac`) |
| ffmpeg, HTTP, antrean | ±0,3 dtk (perkiraan) |
| Terjemahan ML Kit di TV, per baris | ±0,1–0,3 dtk (**perkiraan**; waktunya tidak diukur terpisah, hanya terlihat bahwa baris tampil dalam sepersekian detik setelah model terunduh) |
| **Total sejak ucapan selesai sampai teks siap** | **±6,5–7 dtk** (perkiraan) |

Latensi ini **tidak otomatis buruk** untuk siaran langsung HLS: pemutar TV memutar dengan jarak ±10–30 dtk dari tepi live (segmen 6 detik, tiga di buffer), sedangkan VPS menarik tepi live. Teks dari VPS jadi **tiba lebih dulu** daripada audio yang sama di layar TV, asal diberi cap waktu media (`PROGRAM-DATE-TIME` atau nomor urut segmen) dan TV menampilkannya saat posisi putarnya sampai. Yang harus dijaga adalah sinkronisasi cap waktu antara tarikan VPS dan putaran TV, bukan kecepatan mentah. Ini belum dibuktikan; hanya perhitungan.

## Kesimpulan

**Apakah VPS ini sanggup real-time untuk satu channel yang ditonton?**

- **Ya, untuk satu channel berbahasa Inggris**, dengan `base` (RTF 0,14, ±1,1 core, 285 MB) atau `small -ac 400` (RTF 0,17, ±1,3 core, 700 MB). Itu sekitar seperenam CPU VPS dan memori yang sangat kecil, jadi aman berdampingan dengan pekerjaan lain, selama jatah ±2 core dijaga. Kualitasnya cukup untuk dibaca dan diterjemahkan, dengan catatan ada kalimat yang hilang dan halusinasi pada musik (belum diukur).
- **Tidak, untuk bahasa non-Inggris yang akurat.** Untuk Prancis (dan kemungkinan Jerman) model yang akurat adalah `medium`, yang di VPS ini RTF 1,15 per potongan 5 detik: **tidak mengejar real-time** walau memakai seluruh 8 core. `small` real-time tetapi mutunya mencurigakan (salah dengar, satu potongan 6,4 dtk). `tiny` dan `base` tidak boleh dipakai untuk Prancis (halusinasi dan terpotong).
- Satu channel saja. Dua penonton atau dua channel sekaligus sudah memerlukan inti lebih banyak (`small` penuh 3 core/channel).

**Apa yang perlu dinaikkan atau diubah bila ingin bahasa non-Inggris yang akurat:**

1. Mesin yang lebih cepat daripada CPU mentah ini: **GPU** (pada GPU `medium` lazimnya jauh lebih cepat dari real-time; **tidak diukur di sini**), atau **faster-whisper/CTranslate2 int8** atau model **terkuantisasi** (`q5_0`) di CPU yang sama (biasanya 2–4× lebih cepat; tidak diukur di sini). Alternatif: ASR dari layanan pihak ketiga (akurat, tetapi mengirim audio ke luar dan berbiaya).
2. Tanpa itu: potongan lebih panjang untuk `medium` (mis. 15–20 detik dengan `-ac` kecil), dengan latensi lebih besar yang masih bisa disembunyikan oleh jarak putar TV; belum dicoba.
3. Bila hanya Inggris cukup: tidak ada yang perlu dinaikkan. VPS 8 vCPU yang ada sudah cukup.

**Risiko dan hal yang belum diketahui:** halusinasi pada senyap dan musik (perlu VAD), potongan yang lambat (perlu antrean dengan batas waktu dan mengabaikan potongan terlambat), sinkronisasi cap waktu dengan pemutar TV, VPS dipakai bersama pekerjaan lain (load average sempat 4,7 saat uji), serta ketersediaan stream dari VPS (geo-block, ganti URL). Hanya tiga channel dan 60 detik per channel yang diukur: ini studi kelayakan, bukan jaminan mutu.

## Mengulang

```
git clone https://github.com/ggml-org/whisper.cpp && cd whisper.cpp
cmake -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build -j 6 --config Release
for m in tiny base small medium; do bash models/download-ggml-model.sh $m; done
ffmpeg -t 60 -i <url stream> -vn -ac 1 -ar 16000 -c:a pcm_s16le audio/aje.wav     # juga dw.wav, f24.wav
WHISPER_DIR=$PWD python3 tools/asr-bench/bench.py            # sekali jalan; hasil ke bench8.json
WHISPER_DIR=$PWD python3 tools/asr-bench/chunk_bench.py      # potongan 5 dtk; hasil ke bench_chunks.json
python3 tools/asr-bench/wer.py                               # selisih terhadap medium (butuh out/medium_*.txt)
```
