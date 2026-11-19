package com.ganjunbao.transaction.controller;

import com.ganjunbao.transaction.gateway.GatewayFaultSimulator;
import com.ganjunbao.transaction.observability.ChannelCircuitBreaker;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/operations")
public class OperationsController {

    private final GatewayFaultSimulator faultSimulator;
    private final ChannelCircuitBreaker circuitBreaker;

    public OperationsController(
            GatewayFaultSimulator faultSimulator,
            ChannelCircuitBreaker circuitBreaker
    ) {
        this.faultSimulator = faultSimulator;
        this.circuitBreaker = circuitBreaker;
    }

    @PostMapping("/channels/{channelCode}/fault")
    public GatewayFaultSimulator.FaultSettings configureFault(
            @PathVariable String channelCode,
            @RequestParam(defaultValue = "0") long delayMs,
            @RequestParam(defaultValue = "false") boolean fail
    ) {
        return faultSimulator.configure(channelCode, delayMs, fail);
    }

    @DeleteMapping("/channels/{channelCode}/fault")
    public void resetFault(@PathVariable String channelCode) {
        faultSimulator.reset(channelCode);
    }

    @PostMapping("/channels/{channelCode}/circuit/reset")
    public void resetCircuit(@PathVariable String channelCode) {
        circuitBreaker.reset(channelCode);
    }

    @GetMapping("/channels")
    public Map<String, Object> status() {
        return Map.of(
                "faults", faultSimulator.all(),
                "circuits", circuitBreaker.snapshots()
        );
    }
}
