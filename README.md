# Nailong Auto Dialer (Android)

Starter project Kotlin + Jetpack Compose, minimum Android 10 (API 29).

## Fitur prototipe
- Import CSV (nomor pada kolom pertama) dan XLSX sederhana (nomor pada kolom A, sheet pertama).
- Daftar nomor, urutan panggilan, interval, batas percobaan.
- Assisted dialing memakai `Intent.ACTION_DIAL`; pengguna menekan tombol panggil sendiri.
- Penandaan hasil manual: Terjawab, Tidak dijawab, Sibuk, Gagal.
- Export laporan `.xlsx` sederhana berisi nomor, jumlah percobaan, status, waktu terakhir.

## Cara membuka dan membangun
1. Ekstrak ZIP.
2. Buka folder `NailongAutoDialer` di Android Studio (versi yang mendukung AGP 8.7.3).
3. Tunggu Gradle Sync.
4. Sambungkan HP Android 10+ dengan USB debugging aktif atau gunakan emulator.
5. Tekan Run untuk memasang aplikasi.

Untuk membuat APK: Android Studio > Build > Build Bundle(s) / APK(s) > Build APK(s).

## Format file impor
- Excel: nomor telepon berada di kolom A pada sheet pertama. Baris pertama boleh berupa judul `Nomor Telepon`.
- CSV: nomor telepon berada di kolom pertama, satu nomor per baris. Pemisah koma/titik koma/tab didukung.
- Simpan nomor telepon sebagai teks agar angka 0 di depan tidak hilang.

## Batasan penting prototipe
- Aplikasi tidak melakukan panggilan diam-diam/otomatis. `ACTION_DIAL` membuka aplikasi telepon dan pengguna mengonfirmasi panggilan.
- Android tidak menyediakan status terjawab/tidak dijawab yang andal untuk aplikasi biasa; hasil dicatat setelah pengguna memilih status.
- XLSX reader awal ditujukan untuk workbook sederhana, kolom A dan sheet pertama. Untuk workbook kompleks, ekspor ulang ke CSV atau sederhanakan lembar kerja.
- Prototipe ini belum menyimpan sesi/daftar secara permanen jika aplikasi ditutup. Pengembangan berikutnya dapat menambahkan database lokal, riwayat sesi, validasi nomor, dan penanganan panggilan masuk.
- Gunakan hanya untuk nomor yang memang berwenang dihubungi dan patuhi aturan operator serta hukum setempat.
