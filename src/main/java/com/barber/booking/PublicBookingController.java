package com.barber.booking;

import com.barber.booking.DayPlanner.Span;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/shops/{shopId}")
public class PublicBookingController {

    record CubeDto(String code, String name, int minutes) {}
    record WindowView(Long id, String start, String end, int totalMinutes,
            int freeMinutes, int fitMinutes, List<CubeDto> cubes) {}
    record GapDto(String start, String end, int minutes) {}
    record DayFree(LocalDate date, int windowMinutes, int freeMinutes) {}
    record BookingRequest(
            @NotNull @Size(min = 1, max = 3) List<@NotBlank String> services,
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,15}$") String phone) {}
    record BookingDto(Long id, LocalDate date, String start, String end, int minutes,
            List<String> services) {}

    private final BarbershopRepository shops;
    private final WorkWindowRepository windows;
    private final BookingRepository bookings;

    public PublicBookingController(BarbershopRepository shops, WorkWindowRepository windows,
            BookingRepository bookings) {
        this.shops = shops;
        this.windows = windows;
        this.bookings = bookings;
    }

    @GetMapping("/windows")
    @Transactional(readOnly = true)
    public List<WindowView> listWindows(@PathVariable Long shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        requireShop(shopId);
        checkDate(date);
        LocalDateTime now = LocalDateTime.now();
        List<Span> booked = bookedOf(
                bookings.findByBarbershopIdAndBookingDateOrderByStartMinute(shopId, date));
        List<WindowView> out = new ArrayList<>();
        for (WorkWindow w : windows.findByBarbershopIdAndWindowDateOrderByStartTime(shopId, date)) {
            int s = DayPlanner.minutes(w.getStartTime());
            int e = s + w.totalMinutes();
            List<Span> free = DayPlanner.clipPast(
                    DayPlanner.freeGaps(List.of(new Span(s, e)), booked), date, now);
            List<CubeDto> cubes = w.getCubes().stream()
                    .map(c -> new CubeDto(c.getKind().name(), c.getKind().getLabel(), c.getMinutes()))
                    .toList();
            out.add(new WindowView(w.getId(), DayPlanner.fmt(s), DayPlanner.fmt(e),
                    w.totalMinutes(), DayPlanner.sum(free), DayPlanner.fitMinutes(free), cubes));
        }
        return out;
    }

    @GetMapping("/gaps")
    @Transactional(readOnly = true)
    public List<GapDto> gaps(@PathVariable Long shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        requireShop(shopId);
        checkDate(date);
        List<Span> segments = segmentsOf(
                windows.findByBarbershopIdAndWindowDateOrderByStartTime(shopId, date));
        List<Span> booked = bookedOf(
                bookings.findByBarbershopIdAndBookingDateOrderByStartMinute(shopId, date));
        return DayPlanner.clipPast(DayPlanner.freeGaps(segments, booked), date, LocalDateTime.now())
                .stream()
                .map(g -> new GapDto(DayPlanner.fmt(g.start()), DayPlanner.fmt(g.end()), g.minutes()))
                .toList();
    }

    @GetMapping("/calendar")
    @Transactional(readOnly = true)
    public List<DayFree> calendar(@PathVariable Long shopId,
            @RequestParam(defaultValue = "60") int days) {
        requireShop(shopId);
        int n = Math.min(Math.max(days, 1), 60);
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(n - 1);
        Map<LocalDate, List<WorkWindow>> ws = windows
                .findByBarbershopIdAndWindowDateBetweenOrderByWindowDateAscStartTimeAsc(
                        shopId, from, to)
                .stream().collect(Collectors.groupingBy(WorkWindow::getWindowDate));
        Map<LocalDate, List<Booking>> bs = bookings
                .findByBarbershopIdAndBookingDateBetween(shopId, from, to)
                .stream().collect(Collectors.groupingBy(Booking::getBookingDate));
        LocalDateTime now = LocalDateTime.now();
        List<DayFree> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LocalDate d = from.plusDays(i);
            List<Span> segments = segmentsOf(ws.getOrDefault(d, List.of()));
            List<Span> booked = bookedOf(bs.getOrDefault(d, List.of()));
            List<Span> free = DayPlanner.clipPast(DayPlanner.freeGaps(segments, booked), d, now);
            out.add(new DayFree(d, DayPlanner.sum(segments), DayPlanner.sum(free)));
        }
        return out;
    }

    @PostMapping("/windows/{windowId}/bookings")
    @Transactional
    public BookingDto book(@PathVariable Long shopId, @PathVariable Long windowId,
            @Valid @RequestBody BookingRequest req) {
        Barbershop shop = shops.findByIdForUpdate(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        WorkWindow w = windows.findByIdAndBarbershopId(windowId, shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        LocalDate date = w.getWindowDate();
        if (date.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu pəncərənin vaxtı keçib");
        }
        Set<ServiceKind> kinds = parseKinds(req.services());
        Map<ServiceKind, Integer> cubeMinutes = new EnumMap<>(ServiceKind.class);
        w.getCubes().forEach(c -> cubeMinutes.put(c.getKind(), c.getMinutes()));
        int total = 0;
        for (ServiceKind k : kinds) {
            Integer m = cubeMinutes.get(k);
            if (m == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Bu pəncərədə seçilmiş xidmət yoxdur");
            }
            total += m;
        }
        int ws = DayPlanner.minutes(w.getStartTime());
        int we = ws + w.totalMinutes();
        List<Span> booked = bookedOf(
                bookings.findByBarbershopIdAndBookingDateOrderByStartMinute(shopId, date));
        List<Span> free = DayPlanner.clipPast(
                DayPlanner.freeGaps(List.of(new Span(ws, we)), booked), date, LocalDateTime.now());
        Integer start = DayPlanner.earliestFit(free, total);
        if (start == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu pəncərədə seçilən xidmətlər üçün kifayət qədər boş vaxt yoxdur");
        }
        List<ServiceKind> ordered = new ArrayList<>(kinds);
        Booking saved = bookings.save(new Booking(shop, date, start, total, ordered,
                req.name().trim(), req.phone().trim()));
        return new BookingDto(saved.getId(), date, DayPlanner.fmt(saved.getStartMinute()),
                DayPlanner.fmt(saved.getEndMinute()), saved.getDurationMinutes(),
                ordered.stream().map(Enum::name).toList());
    }

    private static List<Span> segmentsOf(List<WorkWindow> ws) {
        return DayPlanner.merge(ws.stream().map(w -> {
            int s = DayPlanner.minutes(w.getStartTime());
            return new Span(s, s + w.totalMinutes());
        }).toList());
    }

    private static List<Span> bookedOf(List<Booking> bs) {
        return bs.stream().map(b -> new Span(b.getStartMinute(), b.getEndMinute())).toList();
    }

    private static Set<ServiceKind> parseKinds(List<String> codes) {
        Set<ServiceKind> kinds = EnumSet.noneOf(ServiceKind.class);
        for (String c : codes) {
            ServiceKind k;
            try {
                k = ServiceKind.valueOf(c.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw bad("Naməlum xidmət");
            }
            if (!kinds.add(k)) throw bad("Eyni xidmət təkrarlanıb");
        }
        return kinds;
    }

    private static void checkDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today) || date.isAfter(today.plusDays(59))) {
            throw bad("Tarix bugündən 60 günlük aralıqda olmalıdır");
        }
    }

    private void requireShop(Long shopId) {
        if (!shops.existsById(shopId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
