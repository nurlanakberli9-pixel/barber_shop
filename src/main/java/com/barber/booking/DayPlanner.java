package com.barber.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class DayPlanner {

    static final int STEP = 5;

    record Span(int start, int end) {
        int minutes() { return end - start; }
    }

    private DayPlanner() {}

    static int minutes(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    static int parse(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }

    static String fmt(int m) {
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    static int alignUp(int m) {
        return (m + STEP - 1) / STEP * STEP;
    }

    static int earliest(LocalDate date, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (date.isAfter(today)) return 0;
        if (date.isBefore(today)) return 24 * 60;
        return alignUp(now.getHour() * 60 + now.getMinute() + 1);
    }

    static List<Span> merge(List<Span> spans) {
        List<Span> sorted = new ArrayList<>(spans);
        sorted.sort(Comparator.comparingInt(Span::start));
        List<Span> out = new ArrayList<>();
        for (Span s : sorted) {
            if (s.end() <= s.start()) continue;
            if (!out.isEmpty() && s.start() <= out.get(out.size() - 1).end()) {
                Span last = out.remove(out.size() - 1);
                out.add(new Span(last.start(), Math.max(last.end(), s.end())));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    static boolean covered(List<Span> merged, int start, int end) {
        for (Span s : merged) {
            if (s.start() <= start && end <= s.end()) return true;
        }
        return false;
    }

    static List<Span> freeGaps(List<Span> segments, List<Span> busy) {
        List<Span> b = merge(busy);
        List<Span> gaps = new ArrayList<>();
        for (Span seg : segments) {
            int cursor = seg.start();
            for (Span x : b) {
                if (x.end() <= cursor) continue;
                if (x.start() >= seg.end()) break;
                if (x.start() > cursor) gaps.add(new Span(cursor, x.start()));
                cursor = Math.max(cursor, x.end());
            }
            if (cursor < seg.end()) gaps.add(new Span(cursor, seg.end()));
        }
        return gaps;
    }

    static List<Span> clipFrom(List<Span> spans, int from) {
        List<Span> out = new ArrayList<>();
        for (Span s : spans) {
            int start = Math.max(s.start(), from);
            if (start < s.end()) out.add(new Span(start, s.end()));
        }
        return out;
    }

    static int sum(List<Span> spans) {
        return spans.stream().mapToInt(Span::minutes).sum();
    }
}
