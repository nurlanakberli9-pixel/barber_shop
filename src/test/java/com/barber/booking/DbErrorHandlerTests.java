package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

class DbErrorHandlerTests {

    @Test
    void sqlDetailsNeverReachTheResponseOrTheLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            var ex = new DataIntegrityViolationException(
                    "could not execute statement [Key (customer_phone)=(+994501234567) already exists]",
                    new RuntimeException("password=secret123"));
            var res = new ApiExceptionHandler().dataError(ex);

            assertEquals(500, res.getStatusCode().value());
            String body = String.valueOf(res.getBody());
            assertTrue(body.contains("internal"), body);
            assertEquals(1, appender.list.size());
            ILoggingEvent e = appender.list.get(0);
            assertEquals(Level.ERROR, e.getLevel());
            assertNull(e.getThrowableProxy());
            String all = body + " " + e.getFormattedMessage();
            for (String leak : new String[] {"994", "secret123", "customer_phone", "password"}) {
                assertFalse(all.contains(leak), leak + " -> " + all);
            }
            assertTrue(e.getFormattedMessage().contains("type=DataIntegrityViolationException"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(null);
        }
    }
}
