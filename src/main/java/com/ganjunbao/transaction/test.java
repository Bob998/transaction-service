package com.ganjunbao.transaction;

import java.math.BigDecimal;

public class test {
    public static void main(String[] args) {
        boolean res = new BigDecimal("1.0").equals(new BigDecimal("1.00"));
        System.out.println(res);
    }
}
