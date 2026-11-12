package com.ganjunbao.transaction.exception;

public class DuplicateIdempotencyKeyException
        extends RuntimeException {

    public DuplicateIdempotencyKeyException(String idempotencyKey) {
        super("幂等键已存在：" + idempotencyKey);
    }
}