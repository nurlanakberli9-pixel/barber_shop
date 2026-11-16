package com.barber.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/panel/windows")
public class WindowController {

    private static final int WORK_START = 8 * 60;
    private static final int WORK_END = 22 * 60;
    private static final int STEP = 5;

    record CubeDto(String code, String name, int minutes) {}
    record WindowDto(String startTime, String endTime, int totalMinutes, List<CubeDto> cubes) {}
    record WindowIn(
            @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String startTime,
            @NotNull @Size(min = 1, max = 3) List<@NotBlank String> cubes) {}
    record WindowsRequest(@NotNull @Size(max = 20) List<@NotNull @Valid WindowIn> windows) {}

    private final AppUserRepository users;
    private final ServiceTypeRepository types;
    private final WorkWindowRepository windows;
    private final BookingGuard guard;

    public WindowController(AppUserRepository users, ServiceTypeRepository types,
            WorkWindowRepository windows, BookingGuard guard) {
        this.users = users;
        this.types = types;
        this.windows = windows;
        this.guard = guard;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<WindowDto> day(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication auth) {
        Barbershop shop = myShop(auth);
        checkDate(date);
        return windows.findByBarbershopIdAndWindowDateOrderByStartTime(shop.getId(), date)
                .stream().map(this::dto).toList();
    }

    @PutMapping
    @Transactional
    public List<WindowDto> replace(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody WindowsRequest req, Authentication auth) {
        record Plan(int start, List<ServiceKind> kinds, int total) {}

        Barbershop shop = myShop(auth);
        checkDate(date);
        Map<ServiceKind, Integer> minutes = durations(shop);

        List<Plan> plans = new ArrayList<>();
        for (WindowIn item : req.windows()) {
            LocalTime t = LocalTime.parse(item.startTime());
            int start = t.getHour() * 60 + t.getMinute();
            if (start % STEP != 0) {
                throw bad("Başlama vaxtı 5 dəqiqənin qatı olmalıdır");
            }
            List<ServiceKind> kinds = new ArrayList<>();
            for (String code : item.cubes()) {
                ServiceKind k = parse(code);
                if (kinds.contains(k)) {
                    throw bad("Eyni xidmət bir pəncərədə təkrarlana bilməz");
                }
                kinds.add(k);
            }
            int total = kinds.stream().mapToInt(minutes::get).sum();
            if (start < WORK_START || start + total > WORK_END) {
                throw bad("Pəncərə 08:00-22:00 aralığından kənara çıxır");
            }
            plans.add(new Plan(start, kinds, total));
        }
        plans.sort(Comparator.comparingInt(Plan::start));
        for (int i = 1; i < plans.size(); i++) {
            Plan prev = plans.get(i - 1);
            if (plans.get(i).start() < prev.start() + prev.total()) {
                throw bad("Pəncərələr üst-üstə düşür");
            }
        }

        List<int[]> spans = new ArrayList<>();
        for (Plan p : plans) spans.add(new int[] {p.start(), p.start() + p.total()});
        guard.assertBookingsStillFit(shop.getId(), date, spans);

        List<WorkWindow> old = windows.findByBarbershopIdAndWindowDateOrderByStartTime(
                shop.getId(), date);
        windows.deleteAll(old);
        windows.flush();

        List<WorkWindow> created = new ArrayList<>();
        for (Plan p : plans) {
            WorkWindow nw = new WorkWindow(shop, date, LocalTime.of(p.start() / 60, p.start() % 60));
            for (ServiceKind k : p.kinds()) nw.addCube(k, minutes.get(k));
            created.add(windows.save(nw));
        }
        return created.stream().map(this::dto).toList();
    }

    private WindowDto dto(WorkWindow w) {
        List<CubeDto> cubes = w.getCubes().stream()
                .map(c -> new CubeDto(c.getKind().name(), c.getKind().getLabel(), c.getMinutes()))
                .toList();
        return new WindowDto(w.getStartTime().toString(), w.getEndTime().toString(),
                w.totalMinutes(), cubes);
    }

    private Map<ServiceKind, Integer> durations(Barbershop shop) {
        Map<ServiceKind, Integer> m = new EnumMap<>(ServiceKind.class);
        for (ServiceType t : types.findByBarbershopIdOrderById(shop.getId())) {
            if (t.getDurationMinutes() != null) m.put(t.getKind(), t.getDurationMinutes());
        }
        if (m.size() != ServiceKind.values().length) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Əvvəl Xidmətlərim bölməsində üç xidmətin müddətini yazın");
        }
        return m;
    }

    private static void checkDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today) || date.isAfter(today.plusDays(59))) {
            throw bad("Tarix bugündən 60 günlük aralıqda olmalıdır");
        }
    }

    private static ServiceKind parse(String code) {
        try {
            return ServiceKind.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw bad("Naməlum xidmət");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private Barbershop myShop(Authentication auth) {
        AppUser u = users.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Barbershop b = u.getBarbershop();
        if (b == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return b;
    }
}
