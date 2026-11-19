package com.ganjunbao.transaction.exception;

public class PerformanceLabDisabledException extends RuntimeException {

    public PerformanceLabDisabledException() {
        super("性能实验接口未开启");
    }
}
