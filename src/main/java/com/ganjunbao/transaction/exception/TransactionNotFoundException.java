package com.ganjunbao.transaction.exception;

import com.ganjunbao.transaction.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.Map;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(String id) {
        super("交易不存在， id=" + id);
    }
}
