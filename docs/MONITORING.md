# Monitorinq

## Health
- `GET /actuator/health` - ümumi vəziyyət (yalnız UP və ya DOWN)
- `GET /actuator/health/liveness` - proses yaşayır
- `GET /actuator/health/readiness` - proses və baza hazırdır
Digər `/actuator/*` ünvanları bağlıdır.

## Kənar nəzarət (pulsuz)
UptimeRobot və ya BetterStack-də HTTP monitor yarat:
- ünvan: `https://<sənin-domenin>/actuator/health`
- müddət: 5 dəqiqə, "keyword" yoxlaması: `UP`
- bildiriş: e-poçt və ya Telegram
Qeyd: Codespaces-in özəl portuna kənardan çatmaq olmur, bu yalnız deploydan sonra işləyir.

## Loglar
Hər `/api/*` sorğusu: `http method=.. path=.. status=.. ms=.. ip=<hash> rid=<nömrə>`
- `rid` cavabdakı `X-Request-Id` başlığıdır. Müştəri xəta bildirəndə həmin nömrə ilə loqda axtar.
- IP ham yazılmır, hər proses başlayanda dəyişən duz ilə hash olunur.
- Query, body və başlıqlar loga yazılmır.
- `prod` profilində JSON formatındadır.

## Loga nə yazmaq olmaz
Parol, telefon, ad, token, push açarları, `getMessage()` (SQL və dəyərlər ola bilər).
`LogSafetyTests` bu qaydanı avtomatik yoxlayır.

## Xəta siqnalları
- `ERROR` səviyyəsi: 5xx və baza xətaları - dərhal bax
- `WARN`: 403 və 429 - təkrarlanırsa sui-istifadə ola bilər
