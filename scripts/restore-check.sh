#!/usr/bin/env bash
# Backup-ı lokal docker compose bazasında müvəqqəti bazaya qaytarıb yoxlayır.
# İstifadə: ./scripts/restore-check.sh [backups/fayl.dump]
set -euo pipefail
FILE="${1:-$(ls -1t backups/*.dump 2>/dev/null | head -1 || true)}"
if [ -z "$FILE" ] || [ ! -f "$FILE" ]; then
  echo "Backup faylı tapılmadı" >&2
  exit 1
fi
DC="docker compose exec -T db"
$DC dropdb -U barber --if-exists restore_check
$DC createdb -U barber restore_check
$DC pg_restore -U barber --no-owner --no-privileges -d restore_check < "$FILE"
for t in barbershop app_user service_type work_range booking closed_day; do
  n=$($DC psql -U barber -d restore_check -tAc "select count(*) from $t")
  echo "$t: $n"
done
v=$($DC psql -U barber -d restore_check -tAc "select count(*) from flyway_schema_history where success")
echo "flyway miqrasiyaları: $v"
$DC dropdb -U barber restore_check
echo "Bərpa testi uğurludur: $FILE"
