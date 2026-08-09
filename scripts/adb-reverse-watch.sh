#!/usr/bin/env bash
#
# Menjaga tunnel `adb reverse tcp:4100 tcp:4100` tetap hidup untuk APK uji coba
# (`-PlocalApi`, yang menembak http://localhost:4100/).
#
# MASALAH YANG DITUTUP
#
# `adb reverse` TIDAK bertahan melewati putusnya koneksi. Kabel tersenggol,
# HP sleep sampai USB di-suspend, atau daemon adb restart — tunnelnya lenyap.
# Yang dilihat pengguna cuma "app tidak bisa terhubung", tanpa petunjuk bahwa
# penyebabnya di sisi laptop dan bukan di app maupun backend. Dalam satu sesi
# pengembangan hal ini terjadi enam kali.
#
# Memasangnya kembali dengan tangan bukan solusi: yang salah bukan orangnya,
# melainkan bahwa keadaan yang dibutuhkan app tidak dijaga oleh apa pun.
#
# PEMAKAIAN
#
#   ./scripts/adb-reverse-watch.sh            # jaga port 4100
#   ./scripts/adb-reverse-watch.sh 4100 5173  # jaga beberapa port sekaligus
#
# Biarkan berjalan di satu terminal selagi mengembangkan. Ctrl-C untuk berhenti.
#
# KENAPA POLLING, BUKAN `adb wait-for-device`
#
# `wait-for-device` hanya menunggu perangkat MUNCUL; ia tak memberi tahu saat
# perangkat HILANG, jadi loop di atasnya akan menganggap satu perangkat yang
# tak pernah dicabut sebagai "sudah beres" dan berhenti menjaga. Polling murah
# (satu panggilan adb tiap 3 detik) dan menutup dua-duanya: tunnel yang hilang
# karena cabut-colok MAUPUN karena daemon adb di-restart.
#
# `adb reverse` bersifat idempoten — memasangnya ulang saat sudah ada tidak
# berefek apa-apa, jadi loop ini aman dijalankan berdampingan dengan pemasangan
# manual.

set -uo pipefail

PORTS=("${@:-4100}")
JEDA="${ADB_WATCH_INTERVAL:-3}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb tidak ada di PATH." >&2
  echo "Coba: export PATH=\"\$HOME/AppData/Local/Android/Sdk/platform-tools:\$PATH\"" >&2
  exit 1
fi

echo "Menjaga tunnel untuk port: ${PORTS[*]} (cek tiap ${JEDA} dtk). Ctrl-C untuk berhenti."

terpasang_terakhir=""

while true; do
  # `adb reverse --list` gagal (bukan sekadar kosong) saat tak ada perangkat.
  daftar="$(adb reverse --list 2>/dev/null || true)"

  if [ -z "$(adb devices | awk 'NR>1 && $2=="device"')" ]; then
    if [ "$terpasang_terakhir" != "nihil" ]; then
      echo "[$(date +%H:%M:%S)] perangkat tak terhubung — menunggu"
      terpasang_terakhir="nihil"
    fi
    sleep "$JEDA"
    continue
  fi

  kurang=()
  for p in "${PORTS[@]}"; do
    case "$daftar" in
      *"tcp:$p tcp:$p"*) ;;
      *) kurang+=("$p") ;;
    esac
  done

  if [ ${#kurang[@]} -gt 0 ]; then
    for p in "${kurang[@]}"; do
      if adb reverse "tcp:$p" "tcp:$p" >/dev/null 2>&1; then
        echo "[$(date +%H:%M:%S)] tunnel port $p dipasang"
      else
        echo "[$(date +%H:%M:%S)] GAGAL memasang tunnel port $p" >&2
      fi
    done
    terpasang_terakhir="terpasang"
  elif [ "$terpasang_terakhir" != "terpasang" ]; then
    echo "[$(date +%H:%M:%S)] tunnel sudah lengkap"
    terpasang_terakhir="terpasang"
  fi

  sleep "$JEDA"
done
