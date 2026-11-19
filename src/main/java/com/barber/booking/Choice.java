package com.barber.booking;

public record Choice(ServiceKind kind, Level level) {

    String token() {
        return level == null ? kind.name() : kind.name() + ":" + level.name();
    }

    static Choice parse(String token) {
        int i = token.indexOf(':');
        if (i < 0) return new Choice(ServiceKind.valueOf(token), null);
        return new Choice(ServiceKind.valueOf(token.substring(0, i)),
                Level.valueOf(token.substring(i + 1)));
    }
}
