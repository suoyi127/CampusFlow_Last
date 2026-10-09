package com.campusflow.common;

public class BusinessException extends RuntimeException {
    public final int status;
    public final String code;
    public BusinessException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
}
