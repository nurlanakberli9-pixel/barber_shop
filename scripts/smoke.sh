#!/usr/bin/env bash
# Canlı və ya lokal ünvanı yoxlayır.
# İstifadə: ./scripts/smoke.sh https://sizin-ünvan
# İstəyə görə admin girişi: ADMIN_PASSWORD (və ADMIN_USERNAME, default admin) verin.
set -u

BASE="${1:?İstifadə: ./scripts/smoke.sh https://sizin-ünvan}"
BASE="${BASE%/}"
PASS=0
FAIL=0
WARN=0

ok()   { echo "OK    $1"; PASS=$((PASS + 1)); }
bad()  { echo "FAIL  $1"; FAIL=$((FAIL + 1)); }
warn() { echo "WARN  $1"; WARN=$((WARN + 1)); }

status()  { curl -s -o /dev/null -m 25 -w '%{http_code}' "$@" 2>/dev/null || true; }
body()    { curl -s -m 25 "$@" 2>/dev/null || true; }
headers() { curl -s -o /dev/null -D - -m 25 "$@" 2>/dev/null || true; }

expect_status() {
  local name="$1" want="$2" url="$3" got
  got=$(status "$url")
  if [ "$got" = "$want" ]; then ok "$name ($got)"; else bad "$name: gözlənilən $want, gələn $got"; fi
}

echo "== Ünvan: $BASE"

h=$(body "$BASE/actuator/health")
case "$h" in
  *'"status":"UP"'*) ok "health UP" ;;
  *) bad "health UP deyil: $h" ;;
esac
expect_status "liveness" 200 "$BASE/actuator/health/liveness"
expect_status "readiness (baza daxil)" 200 "$BASE/actuator/health/readiness"
head_code=$(status -I "$BASE/actuator/health")
if [ "$head_code" = "200" ]; then ok "HEAD health (uptime monitorları üçün)"; else bad "HEAD health: $head_code"; fi

for p in / /panel.html /admin.html /sw.js /js/index.js /js/panel.js /js/admin.js /css/index.css; do
  expect_status "səhifə $p" 200 "$BASE$p"
done
if body "$BASE/" | grep -q "BarberConnect"; then ok "ana səhifə məzmunu"; else bad "ana səhifədə BarberConnect yoxdur"; fi

shops=$(body "$BASE/api/shops")
case "$shops" in
  "["*) ok "/api/shops JSON siyahıdır" ;;
  *) bad "/api/shops gözlənilməz cavab" ;;
esac

for p in /actuator/env /actuator/heapdump /actuator/beans /api/panel/me /api/admin/me /api/hack; do
  expect_status "bağlıdır: $p" 401 "$BASE$p"
done

hdr=$(headers "$BASE/api/shops")
for name in content-security-policy x-frame-options x-content-type-options referrer-policy x-request-id; do
  if echo "$hdr" | grep -qi "^$name:"; then ok "başlıq $name"; else bad "başlıq yoxdur: $name"; fi
done

if headers "$BASE/api/shops" -H "Origin: https://evil.example" | grep -qi '^access-control-allow-origin:'; then
  bad "CORS yad origin üçün açıqdır"
else
  ok "CORS bağlıdır"
fi

err=$(body "$BASE/api/shops/1/day")
case "$err" in
  *Exception*|*"com.barber"*|*"at org."*) bad "xəta cavabı daxili məlumat sızdırır" ;;
  *) ok "xəta cavabı təmizdir" ;;
esac

case "$BASE" in
  https://*)
    if echo "$hdr" | grep -qi '^strict-transport-security:'; then
      ok "HSTS"
    else
      warn "HSTS yoxdur: proxy arxasında HTTPS tanınmır (docs/DEPLOY.md bölməsinə baxın)"
    fi
    plain=$(status "http://${BASE#https://}/actuator/health")
    case "$plain" in
      301|302|307|308|000) ok "HTTP ($plain) yönləndirilir və ya bağlıdır" ;;
      *) warn "HTTP ilə açıq cavab verir ($plain)" ;;
    esac
    ;;
  *) warn "HTTPS deyil: HSTS və Secure cookie yoxlanmadı" ;;
esac

if [ -n "${ADMIN_PASSWORD:-}" ]; then
  user="${ADMIN_USERNAME:-admin}"
  jar=$(mktemp)
  curl -s -m 25 -c "$jar" -o /dev/null "$BASE/api/csrf"
  tok=$(awk '/XSRF-TOKEN/ {print $7}' "$jar")
  login=$(curl -s -m 25 -b "$jar" -c "$jar" -o /dev/null -D - -X POST "$BASE/api/login" \
    -H "X-XSRF-TOKEN: $tok" --data-urlencode "username=$user" --data-urlencode "password=$ADMIN_PASSWORD" 2>/dev/null || true)
  code=$(echo "$login" | head -1 | awk '{print $2}')
  if [ "$code" = "200" ]; then ok "admin girişi"; else bad "admin girişi uğursuz ($code)"; fi
  me=$(status -b "$jar" "$BASE/api/admin/me")
  if [ "$me" = "200" ]; then ok "admin sessiyası işləyir"; else bad "admin sessiyası: $me"; fi
  case "$BASE" in
    https://*)
      if echo "$login" | grep -i '^set-cookie: *JSESSIONID' | grep -qi 'secure'; then
        ok "sessiya cookie-si Secure"
      else
        warn "sessiya cookie-sində Secure yoxdur"
      fi
      ;;
  esac
  rm -f "$jar"
else
  warn "ADMIN_PASSWORD verilməyib: admin girişi yoxlanmadı"
fi

echo "== Nəticə: $PASS keçdi, $FAIL uğursuz, $WARN xəbərdarlıq"
[ "$FAIL" -eq 0 ]
