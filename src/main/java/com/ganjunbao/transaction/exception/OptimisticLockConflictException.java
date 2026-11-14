package com.ganjunbao.transaction.exception;

public class OptimisticLockConflictException
        extends RuntimeException {

    public OptimisticLockConflictException(String transactionNo) {
        super("交易正在被其他请求更新，请稍后重试：" + transactionNo);
    }
}