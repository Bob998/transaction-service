package com.ganjunbao.transaction.exception;

public class DuplicateIdempotencyKeyException
        extends RuntimeException {

    public DuplicateIdempotencyKeyException(String idempotencyKey) {
        super("幂等键已存在：" + idempotencyKey);
    }

    public DuplicateIdempotencyKeyException(
            String idempotencyKey,
            String reason
    ) {
        super("幂等键已存在：" + idempotencyKey + "，" + reason);
    }
}
