package com.github.hrdlickajan.multicurrencywallet.api;

import com.github.hrdlickajan.multicurrencywallet.rates.RatesUnavailableException;
import com.github.hrdlickajan.multicurrencywallet.wallet.InsufficientFundsException;
import com.github.hrdlickajan.multicurrencywallet.wallet.InvalidMoneyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidMoneyException.class)
    public ProblemDetail handleInvalidMoney(InvalidMoneyException exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setProperty("code", "INVALID_MONEY");
        return detail;
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        detail.setProperty("code", "INSUFFICIENT_FUNDS");
        return detail;
    }

    @ExceptionHandler(RatesUnavailableException.class)
    public ProblemDetail handleRatesUnavailable(RatesUnavailableException exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        detail.setProperty("code", "RATES_UNAVAILABLE");
        return detail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception exception) {
        log.error("Unexpected wallet failure", exception);
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred.");
        detail.setProperty("code", "INTERNAL_ERROR");
        return detail;
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        var detail = ProblemDetail.forStatus(status);
        if (body instanceof ProblemDetail existingDetail) {
            detail = existingDetail;
        }
        if (status.is5xxServerError()) {
            detail.setProperty("code", "INTERNAL_ERROR");
            detail.setDetail("An unexpected server error occurred.");
        } else {
            detail.setProperty("code", "INVALID_REQUEST");
        }
        return super.createResponseEntity(detail, headers, status, request);
    }
}