package com.barber.booking;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String ALPHABET =
            "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final BarbershopRepository shops;
    private final AppUserRepository users;
    private final ServiceTypeRepository types;
    private final WorkRangeRepository workRanges;
    private final PasswordEncoder encoder;

    public DataSeeder(BarbershopRepository shops, AppUserRepository users,
            ServiceTypeRepository types, WorkRangeRepository workRanges,
            PasswordEncoder encoder) {
        this.shops = shops;
        this.users = users;
        this.types = types;
        this.workRanges = workRanges;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (shops.count() > 0) return;
        String[][] data = {
            {"Gentlemen's Choice", "David", "Nizami küç. 10"},
            {"The Mod Barber", "Mia", "Fizuli küç. 25"},
            {"The Cutting Edge", "Anna", "Rəsul Rza küç. 7"},
            {"Royal Barber", "Elvin", "Xaqani küç. 14"},
            {"Classic Cut", "Rauf", "Azadlıq pr. 90"},
            {"Urban Style", "Kamran", "Tbilisi pr. 33"},
            {"Barber House", "Murad", "Bülbül pr. 21"},
            {"Sharp & Clean", "Leyla", "Neftçilər pr. 48"}
        };
        double[] ratings = {4.8, 4.7, 4.9, 4.6, 4.5, 4.4, 4.8, 4.7};
        List<Barbershop> created = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            Barbershop shop = shops.save(
                    new Barbershop(data[i][0], data[i][1], data[i][2], ratings[i]));
            created.add(shop);
            for (ServiceKind k : ServiceKind.values()) {
                ServiceType t = new ServiceType(shop, k);
                if (k == ServiceKind.HAIRCUT) t.setLevels(15, 20, 25);
                else if (k == ServiceKind.BEARD) t.setLevels(10, 15, 20);
                else t.setSingle(10);
                types.save(t);
            }
            workRanges.save(new WorkRange(shop, 8 * 60, 12 * 60));
            workRanges.save(new WorkRange(shop, 14 * 60, 18 * 60));
        }
        seedBarberAccounts(created);
    }

    private void seedBarberAccounts(List<Barbershop> created) {
        String password = System.getenv("BARBER_SEED_PASSWORD");
        boolean generated = password == null || password.isBlank();
        if (generated) password = randomPassword();
        List<String> names = new ArrayList<>();
        for (Barbershop shop : created) {
            String username = shop.getMasterName().toLowerCase(Locale.ROOT);
            users.save(new AppUser(username, encoder.encode(password), "BARBER", shop));
            names.add(username);
        }
        if (generated) {
            log.warn("DEV-ONLY barber accounts: {} | password: {}", names, password);
        } else {
            log.warn("DEV-ONLY barber accounts created: {}", names);
        }
    }

    private static String randomPassword() {
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
