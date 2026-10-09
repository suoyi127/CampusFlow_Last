package com.campusflow.common;

import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ExceptionAdvice {
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> business(BusinessException e) {
        return ResponseEntity.status(e.status).body(ApiError.of(e.code, e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
        HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalid(Exception e) {
        String message = e instanceof MethodArgumentNotValidException validation
            ? validation.getBindingResult().getAllErrors().getFirst().getDefaultMessage() : "请求参数格式不正确";
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_INPUT", message));
    }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> missing(Exception e) {
        return ResponseEntity.status(404).body(ApiError.of("NOT_FOUND", "接口或资源不存在"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        LoggerFactory.getLogger(ExceptionAdvice.class).error("请求处理失败", e);
        return ResponseEntity.internalServerError().body(ApiError.of("INTERNAL_ERROR", "服务暂时不可用，请稍后重试"));
    }
}
