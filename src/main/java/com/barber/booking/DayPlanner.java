package com.barber.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class DayPlanner {

    static final int ALIGN = 5;

    record Span(int start, int end) {
        int minutes() { return end - start; }
    }

    private DayPlanner() {}

    static int minutes(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    static String fmt(int m) {
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    static int alignUp(int m) {
        return (m + ALIGN - 1) / ALIGN * ALIGN;
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

    static List<Span> freeGaps(List<Span> segments, List<Span> booked) {
        List<Span> b = merge(booked);
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

    static List<Span> clipPast(List<Span> gaps, LocalDate date, LocalDateTime now) {
        if (date.isAfter(now.toLocalDate())) return gaps;
        if (date.isBefore(now.toLocalDate())) return List.of();
        int from = now.getHour() * 60 + now.getMinute() + 1;
        List<Span> out = new ArrayList<>();
        for (Span g : gaps) {
            int s = Math.max(g.start(), from);
            if (s < g.end()) out.add(new Span(s, g.end()));
        }
        return out;
    }

    static int sum(List<Span> spans) {
        return spans.stream().mapToInt(Span::minutes).sum();
    }

    static int fitMinutes(List<Span> gaps) {
        int best = 0;
        for (Span g : gaps) best = Math.max(best, g.end() - alignUp(g.start()));
        return best;
    }

    static Integer earliestFit(List<Span> gaps, int total) {
        for (Span g : gaps) {
            int s = alignUp(g.start());
            if (s + total <= g.end()) return s;
        }
        return null;
    }
}
