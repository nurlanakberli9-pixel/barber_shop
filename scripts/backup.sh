#!/usr/bin/env bash
# İstifadə: DATABASE_URL=postgresql://user:pass@host:5432/db ./scripts/backup.sh
# DATABASE_URL verilməyibsə lokal docker compose bazasından götürür.
set -euo pipefail
mkdir -p backups
OUT="backups/barber-$(date -u +%Y%m%dT%H%M%SZ).dump"
if [ -n "${DATABASE_URL:-}" ]; then
  pg_dump --format=custom --no-owner --no-privileges --file "$OUT" "$DATABASE_URL"
else
  docker compose exec -T db pg_dump -U barber --format=custom --no-owner --no-privileges barber > "$OUT"
fi
if [ ! -s "$OUT" ]; then
  echo "Backup boşdur, uğursuz oldu" >&2
  rm -f "$OUT"
  exit 1
fi
echo "Yaradıldı: $OUT ($(du -h "$OUT" | cut -f1))"
