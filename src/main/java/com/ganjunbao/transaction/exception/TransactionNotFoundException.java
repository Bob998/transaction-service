package com.ganjunbao.transaction.exception;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(String id) {
        super("交易不存在， id=" + id);
    }
}
