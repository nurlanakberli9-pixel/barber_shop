package com.barber.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final BarbershopRepository shops;
    private final SlotRepository slots;

    public DataSeeder(BarbershopRepository shops, SlotRepository slots) {
        this.shops = shops;
        this.slots = slots;
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
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<Slot> all = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            Barbershop shop = shops.save(
                    new Barbershop(data[i][0], data[i][1], data[i][2], ratings[i]));
            for (int d = 0; d < 60; d++) {
                for (int h = 9; h <= 20; h++) {
                    LocalDateTime t = today.plusDays(d).atTime(h, 0);
                    if (t.isBefore(now)) continue;
                    Slot s = new Slot(shop, t);
                    if ((i + d + h) % 4 == 0) s.book("Test", "+994500000000");
                    all.add(s);
                }
            }
        }
        slots.saveAll(all);
    }
}
