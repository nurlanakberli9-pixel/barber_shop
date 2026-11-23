package com.barber.booking;

import jakarta.persistence.PersistenceException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({DataAccessException.class, TransactionException.class,
            PersistenceException.class})
    public ResponseEntity<Map<String, String>> dataError(Exception ex) {
        String type = ex.getClass().getSimpleName();
        String cause = NestedExceptionUtils.getMostSpecificCause(ex).getClass().getSimpleName();
        String rid = MDC.get(RequestLogFilter.MDC_KEY);
        if (rid == null) rid = "n/a";
        log.error("Verilənlər bazası xətası: type={} cause={} rid={}", type, cause, rid);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "internal", "requestId", rid));
    }
}
