package com.ganjunbao.transaction.controller;

import com.ganjunbao.transaction.gateway.GatewayFaultSimulator;
import com.ganjunbao.transaction.gateway.PaymentGatewayRequest;
import com.ganjunbao.transaction.gateway.PaymentGatewayResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/mock-gateway/channels")
public class MockPaymentGatewayController {

    private final GatewayFaultSimulator faultSimulator;

    public MockPaymentGatewayController(
            GatewayFaultSimulator faultSimulator
    ) {
        this.faultSimulator = faultSimulator;
    }

    @PostMapping("/{channelCode}/payments/confirm")
    @ResponseStatus(HttpStatus.OK)
    public PaymentGatewayResponse confirm(
            @PathVariable String channelCode,
            @RequestBody PaymentGatewayRequest request
    ) throws InterruptedException {
        GatewayFaultSimulator.FaultSettings settings =
                faultSimulator.get(channelCode);
        if (settings.delayMs() > 0) {
            Thread.sleep(settings.delayMs());
        }
        if (settings.fail()) {
            throw new IllegalStateException(
                    "模拟通道失败: " + channelCode
            );
        }
        return new PaymentGatewayResponse(
                "SUCCESS",
                channelCode + "-" + UUID.randomUUID()
        );
    }
}
