package com.verifi.portal.screening;

final class ScreeningException extends RuntimeException {
    final int status;
    final String code;
    ScreeningException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    static ScreeningException missing() {
        return new ScreeningException(404, "RESOURCE_NOT_FOUND", "The requested resource was not found.");
    }
    static ScreeningException conflict() {
        return new ScreeningException(409, "REQUEST_CONFLICT", "This request ID was already used with different details. Review the existing request.");
    }
}

