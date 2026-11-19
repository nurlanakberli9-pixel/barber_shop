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

    record ServiceInfo(String code, String name, boolean levels, Integer minutes,
            Integer low, Integer medium, Integer high) {}
    record SpanDto(int start, int end) {}
    record DayView(int step, int earliest, List<SpanDto> ranges, List<SpanDto> busy) {}
    record DayFree(LocalDate date, int workMinutes, int freeMinutes) {}
    record ServiceChoice(@NotBlank String code, String level) {}
    record BookingRequest(
            @NotNull LocalDate date,
            @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String startTime,
            @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String endTime,
            @NotNull @Size(min = 1, max = 3) List<@NotNull @Valid ServiceChoice> services,
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,15}$") String phone) {}
    record BookingDto(Long id, LocalDate date, String start, String end, int minutes,
            List<String> services) {}

    private final BarbershopRepository shops;
    private final ServiceTypeRepository types;
    private final WorkRangeRepository workRanges;
    private final BookingRepository bookings;
    private final ComboBufferRepository buffers;

    public PublicBookingController(BarbershopRepository shops, ServiceTypeRepository types,
            WorkRangeRepository workRanges, BookingRepository bookings,
            ComboBufferRepository buffers) {
        this.shops = shops;
        this.types = types;
        this.workRanges = workRanges;
        this.bookings = bookings;
        this.buffers = buffers;
    }

    @GetMapping("/services")
    @Transactional(readOnly = true)
    public List<ServiceInfo> services(@PathVariable Long shopId) {
        requireShop(shopId);
        Map<ServiceKind, ServiceType> m = typesOf(shopId);
        List<ServiceInfo> out = new ArrayList<>();
        for (ServiceKind k : ServiceKind.values()) {
            ServiceType t = m.get(k);
            out.add(new ServiceInfo(k.name(), k.getLabel(), k.hasLevels(),
                    t == null ? null : t.getSingleMinutes(),
                    t == null ? null : t.getLowMinutes(),
                    t == null ? null : t.getMediumMinutes(),
                    t == null ? null : t.getHighMinutes()));
        }
        return out;
    }

    @GetMapping("/day")
    @Transactional(readOnly = true)
    public DayView day(@PathVariable Long shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        requireShop(shopId);
        checkDate(date);
        List<Span> ranges = rangesOf(shopId);
        List<Span> busy = busyOf(
                bookings.findByBarbershopIdAndBookingDateOrderByStartMinute(shopId, date));
        return new DayView(DayPlanner.STEP, DayPlanner.earliest(date, LocalDateTime.now()),
                dto(ranges), dto(busy));
    }

    @GetMapping("/calendar")
    @Transactional(readOnly = true)
    public List<DayFree> calendar(@PathVariable Long shopId,
            @RequestParam(defaultValue = "60") int days) {
        requireShop(shopId);
        int n = Math.min(Math.max(days, 1), 60);
        LocalDate from = LocalDate.now();
        List<Span> ranges = rangesOf(shopId);
        Map<LocalDate, List<Booking>> byDate = bookings
                .findByBarbershopIdAndBookingDateBetween(shopId, from, from.plusDays(n - 1))
                .stream().collect(Collectors.groupingBy(Booking::getBookingDate));
        LocalDateTime now = LocalDateTime.now();
        List<DayFree> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LocalDate d = from.plusDays(i);
            int earliest = DayPlanner.earliest(d, now);
            List<Span> busy = busyOf(byDate.getOrDefault(d, List.of()));
            int work = DayPlanner.sum(DayPlanner.clipFrom(ranges, earliest));
            int free = DayPlanner.sum(DayPlanner.clipFrom(DayPlanner.freeGaps(ranges, busy), earliest));
            out.add(new DayFree(d, work, free));
        }
        return out;
    }

    @PostMapping("/bookings")
    @Transactional
    public BookingDto book(@PathVariable Long shopId, @Valid @RequestBody BookingRequest req) {
        Barbershop shop = shops.findByIdForUpdate(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        LocalDate date = req.date();
        checkDate(date);
        List<Choice> choices = parseChoices(req.services());
        int start = DayPlanner.parse(req.startTime());
        int end = DayPlanner.parse(req.endTime());
        if (start % DayPlanner.STEP != 0 || end % DayPlanner.STEP != 0 || end <= start) {
            throw bad("Vaxt 5 dəqiqənin qatı olmalıdır və bitmə başlamadan sonra gəlməlidir");
        }
        Map<ServiceKind, ServiceType> m = typesOf(shopId);
        int duration = 0;
        Set<ServiceKind> kinds = EnumSet.noneOf(ServiceKind.class);
        for (Choice c : choices) {
            ServiceType t = m.get(c.kind());
            Integer minutes = t == null ? null : t.minutesFor(c.level());
            if (minutes == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Bərbər hələ bu xidmətin müddətini yazmayıb");
            }
            duration += minutes;
            kinds.add(c.kind());
        }
        if (end - start != duration) {
            throw bad("Seçilən vaxt " + duration + " dəqiqə olmalıdır");
        }
        if (!DayPlanner.covered(rangesOf(shopId), start, end)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seçilən vaxt bərbərin iş saatından kənardadır");
        }
        if (start < DayPlanner.earliest(date, LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu vaxt keçib");
        }
        int buffer = bufferFor(shopId, kinds);
        int occupiedEnd = end + buffer;
        for (Booking b : bookings.findByBarbershopIdAndBookingDateOrderByStartMinute(shopId, date)) {
            if (start < b.getOccupiedEnd() && b.getStartMinute() < occupiedEnd) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Bu vaxt artıq tutulub və ya uyğun deyil");
            }
        }
        Booking saved = bookings.save(new Booking(shop, date, start, duration, buffer, choices,
                req.name().trim(), req.phone().trim()));
        return new BookingDto(saved.getId(), date, DayPlanner.fmt(start), DayPlanner.fmt(end),
                duration, choices.stream().map(c -> c.kind().name()).toList());
    }

    private int bufferFor(Long shopId, Set<ServiceKind> kinds) {
        String key = Combos.key(kinds);
        for (ComboBuffer b : buffers.findByBarbershopId(shopId)) {
            if (b.getComboKey().equals(key)) return b.getAfterMinutes();
        }
        return 0;
    }

    private Map<ServiceKind, ServiceType> typesOf(Long shopId) {
        Map<ServiceKind, ServiceType> m = new EnumMap<>(ServiceKind.class);
        for (ServiceType t : types.findByBarbershopIdOrderById(shopId)) m.put(t.getKind(), t);
        return m;
    }

    private List<Span> rangesOf(Long shopId) {
        return DayPlanner.merge(workRanges.findByBarbershopIdOrderByStartMinute(shopId).stream()
                .map(r -> new Span(r.getStartMinute(), r.getEndMinute())).toList());
    }

    private static List<Span> busyOf(List<Booking> list) {
        return DayPlanner.merge(list.stream()
                .map(b -> new Span(b.getStartMinute(), b.getOccupiedEnd())).toList());
    }

    private static List<SpanDto> dto(List<Span> spans) {
        return spans.stream().map(s -> new SpanDto(s.start(), s.end())).toList();
    }

    private static List<Choice> parseChoices(List<ServiceChoice> list) {
        List<Choice> out = new ArrayList<>();
        Set<ServiceKind> seen = EnumSet.noneOf(ServiceKind.class);
        for (ServiceChoice sc : list) {
            ServiceKind kind;
            try {
                kind = ServiceKind.valueOf(sc.code().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw bad("Naməlum xidmət");
            }
            if (!seen.add(kind)) throw bad("Eyni xidmət təkrarlanıb");
            Level level = null;
            if (kind.hasLevels()) {
                if (sc.level() == null) {
                    throw bad("Saç və saqqal üçün az, orta və ya çox seçilməlidir");
                }
                try {
                    level = Level.valueOf(sc.level().trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw bad("Naməlum səviyyə");
                }
            }
            out.add(new Choice(kind, level));
        }
        return out;
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
