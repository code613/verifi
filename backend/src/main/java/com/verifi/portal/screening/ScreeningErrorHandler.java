package com.verifi.portal.screening;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import static com.verifi.portal.screening.ScreeningDtos.ApiError;

@RestControllerAdvice(assignableTypes = ScreeningController.class)
public class ScreeningErrorHandler {
    @ExceptionHandler(ScreeningException.class)
    public ResponseEntity<ApiError> domain(ScreeningException failure) {
        return ResponseEntity.status(failure.status).body(new ApiError(failure.code, failure.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> invalid(Exception failure) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST",
                "Check the required fields, valid identifiers, and explicit authorization attestation."));
    }
}

