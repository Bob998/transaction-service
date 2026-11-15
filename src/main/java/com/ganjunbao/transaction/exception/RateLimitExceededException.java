package com.ganjunbao.transaction.exception;

public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(long limit) {
        super("请求过于频繁，当前窗口最多允许 " + limit + " 次请求");
    }
}
