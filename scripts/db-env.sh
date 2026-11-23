#!/usr/bin/env bash
# Platformanın verdiyi postgresql://user:parol@host:5432/db?sslmode=require ünvanını
# tətbiqin DB_URL, DB_USER, DB_PASSWORD formatına çevirir.
# İstifadə: ./scripts/db-env.sh 'postgresql://...' >> .secrets.local
set -euo pipefail
URL="${1:?İstifadə: db-env.sh 'postgresql://user:parol@host:5432/db?sslmode=require'}"
python3 - "$URL" <<'PY'
import sys
from urllib.parse import parse_qs, unquote, urlparse

u = urlparse(sys.argv[1])
if u.scheme not in ("postgres", "postgresql") or not u.hostname or not u.path.strip("/"):
    sys.exit("Format düzgün deyil: postgresql://user:parol@host:5432/db?sslmode=require")
ssl = parse_qs(u.query).get("sslmode", ["require"])[0]
print(f"DB_URL=jdbc:postgresql://{u.hostname}:{u.port or 5432}{u.path}?sslmode={ssl}")
print(f"DB_USER={unquote(u.username or '')}")
print(f"DB_PASSWORD={unquote(u.password or '')}")
PY
