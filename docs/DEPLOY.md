# Deploy

## Mühit dəyişənləri
| Ad | Məcburi | Nə üçün |
|---|---|---|
| SPRING_PROFILES_ACTIVE=prod | bəli | Secure cookie, JSON log, seeder söndürülür, PostgreSQL məcburidir |
| DB_URL, DB_USER, DB_PASSWORD | bəli | PostgreSQL (`scripts/db-env.sh` ilə çevir) |
| DB_POOL_SIZE | xeyr | pulsuz bazalarda bağlantı limiti üçün 5 |
| ADMIN_USERNAME, ADMIN_PASSWORD | ilk deployda | admin hesabı yalnız yoxdursa yaranır |
| VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY | bəli | sabit push açarları (`scripts/gen-secrets.py`) |
| VAPID_SUBJECT | bəli | `mailto:real@domen.tld` (example.* qəbul olunmur) |
| PORT | platforma verir | tətbiq bu portda dinləyir |

Bu dəyişənlərin heç biri GitHub-a yazılmır, yalnız platformanın secret bölməsinə.

## İlk deploy
1. Platformada PostgreSQL yarat, əlaqə ünvanını götür. Pooler (PgBouncer) ünvanından qaç, birbaşa ünvanı işlət (miqrasiya kilidi üçün).
2. `./scripts/gen-secrets.py` və `./scripts/db-env.sh 'ünvan' >> .secrets.local`
3. `code .secrets.local` ilə aç, dəyərləri platformaya köçür, sonra `rm .secrets.local`.
4. `VAPID_SUBJECT`, `SPRING_PROFILES_ACTIVE=prod` əlavə et. Deploy et.
5. `ADMIN_PASSWORD=... ./scripts/smoke.sh https://sizin-ünvan` işlət. Hamısı OK olmalıdır.
6. `/admin.html` ilə bərbər hesabı yarat. Prod-da test bərbərləri yoxdur.

## Hər deploydan əvvəl
- `main`-də `test`, `test-postgres`, `docker`, `CodeQL` yaşıl olsun.
- Backup: `DATABASE_URL='postgresql://...' ./scripts/backup.sh`. `pg_dump` versiyası bazanın versiyasından az olmasın.

## Geri qaytarma
- Tətbiq: platformada əvvəlki uğurlu deploya qayıt (və ya əvvəlki commit-i yenidən deploy et).
- Baza: Flyway yalnız irəli gedir. Mövcud `V*.sql` faylını dəyişmə, yeni fayl yaz. Dağıdıcı miqrasiyadan (drop, silmə) əvvəl backup al.
- Bərpa: `pg_restore --clean --no-owner -d "$DATABASE_URL" backups/fayl.dump` (əvvəl `restore-check.sh` ilə yoxla).

## Proxy arxasında IP yoxlaması
Rate limiting IP-yə görə işləyir. Platforma proxy-si IP-ni `X-Forwarded-For` ilə ötürür və `server.forward-headers-strategy=native` onu oxuyur.
- Deploydan sonra bir telefondan mobil internetlə, birindən wifi ilə sayta gir.
- Loqda `ip=` hash-ları fərqli olmalıdır. Hamısı eyni hash-dırsa, bütün istifadəçilər bir IP kimi sayılır və limit hamını birlikdə bloklayar. Bu halda `server.tomcat.remoteip.internal-proxies` ayarını platformanın proxy aralığına görə təyin et.
- Smoke testdə HSTS xəbərdarlığı da eyni problemin əlamətidir.

## Məlum məhdudiyyətlər
- Admin parolunu dəyişdirən ekran yoxdur. Dəyişmək üçün: bazada admin sətrini sil (`delete from app_user where role = 'ADMIN'`), yeni `ADMIN_PASSWORD` ilə yenidən başlat. Parolu menecerdə saxla.
- Limitlər yaddaşdadır: restartda sıfırlanır, bir neçə nüsxə işləsə hər birində ayrıdır.
- Boş qalanda yuxuya gedən pulsuz planlarda push bildirişi və ilk sorğu gecikə bilər.
