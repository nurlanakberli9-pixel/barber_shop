package com.barber.booking;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shops")
public class ShopController {

    record ShopDto(Long id, String name, String masterName, String address, double rating) {}

    private final BarbershopRepository shops;

    public ShopController(BarbershopRepository shops) {
        this.shops = shops;
    }

    @GetMapping
    public List<ShopDto> list() {
        return shops.findAll().stream()
                .map(s -> new ShopDto(s.getId(), s.getName(), s.getMasterName(),
                        s.getAddress(), s.getRating()))
                .toList();
    }
}
