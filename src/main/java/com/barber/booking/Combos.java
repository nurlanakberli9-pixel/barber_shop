package com.barber.booking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

final class Combos {

    private Combos() {}

    static String key(Collection<ServiceKind> kinds) {
        return kinds.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    static String label(Collection<ServiceKind> kinds) {
        return kinds.stream().sorted().map(ServiceKind::getLabel).collect(Collectors.joining(" + "));
    }

    static List<List<ServiceKind>> all() {
        ServiceKind[] values = ServiceKind.values();
        List<Integer> masks = new ArrayList<>();
        for (int m = 1; m < (1 << values.length); m++) masks.add(m);
        masks.sort(Comparator.comparingInt((Integer m) -> Integer.bitCount(m)).thenComparingInt(m -> m));
        List<List<ServiceKind>> out = new ArrayList<>();
        for (int m : masks) {
            List<ServiceKind> list = new ArrayList<>();
            for (int i = 0; i < values.length; i++) {
                if ((m & (1 << i)) != 0) list.add(values[i]);
            }
            out.add(list);
        }
        return out;
    }
}
