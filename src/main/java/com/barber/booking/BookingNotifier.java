package com.barber.booking;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookingNotifier.class);

    private final PushSender sender;

    public BookingNotifier(PushSender sender) {
        this.sender = sender;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBooking(BookingCreatedEvent e) {
        try {
            String body = String.format(Locale.ROOT, "%02d.%02d · %s–%s",
                    e.date().getDayOfMonth(), e.date().getMonthValue(),
                    DayPlanner.fmt(e.start()), DayPlanner.fmt(e.end()));
            sender.sendToShop(e.shopId(),
                    PushSender.json("Yeni rezerv", body, "booking-" + System.nanoTime()));
        } catch (Exception ex) {
            log.warn("Bildiriş hazırlanmadı: {}", ex.getClass().getSimpleName());
        }
    }
}
