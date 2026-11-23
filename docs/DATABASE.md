# Baza

- Sxema yalnız `src/main/resources/db/migration` altındakı Flyway fayllarından gəlir.
- Mövcud `V*.sql` faylını heç vaxt dəyişmə. Dəyişiklik üçün yeni fayl yaz: `V2__aciqlama.sql`.
- Hibernate `ddl-auto=validate` rejimindədir: kod bazaya uyğun deyilsə tətbiq başlamır.
- `DB_URL` verilməyibsə yaddaşdakı H2 işləyir (yalnız dev və test). Canlıda `SPRING_PROFILES_ACTIVE=prod` və PostgreSQL məcburidir.
- Rezerv toqquşması salon sətrinin `FOR UPDATE` kilidi ilə qorunur.

## Lokal PostgreSQL
    export POSTGRES_PASSWORD='...'
    docker compose up -d db
    export DB_URL=jdbc:postgresql://localhost:5432/barber DB_USER=barber DB_PASSWORD="$POSTGRES_PASSWORD"

## Backup və bərpa testi
    ./scripts/backup.sh
    ./scripts/restore-check.sh

Bərpa edilməyən backup backup sayılmır: həftədə bir `restore-check.sh` işlət.
Canlı bazada (Neon, Supabase və s.) provayderin avtomatik backup-ı da yandırılmalıdır.
