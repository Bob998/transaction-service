package com.ganjunbao.transaction.controller;

import com.ganjunbao.transaction.gateway.GatewayFaultSimulator;
import com.ganjunbao.transaction.observability.ChannelCircuitBreaker;
import com.ganjunbao.transaction.observability.PerformanceLabService;
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
    private final PerformanceLabService performanceLabService;

    public OperationsController(
            GatewayFaultSimulator faultSimulator,
            ChannelCircuitBreaker circuitBreaker,
            PerformanceLabService performanceLabService
    ) {
        this.faultSimulator = faultSimulator;
        this.circuitBreaker = circuitBreaker;
        this.performanceLabService = performanceLabService;
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

    @PostMapping("/performance/thread-pool")
    public PerformanceLabService.PressureSubmission threadPoolPressure(
            @RequestParam(defaultValue = "40") int tasks,
            @RequestParam(defaultValue = "5000") long durationMs
    ) {
        return performanceLabService.submitThreadPressure(
                tasks,
                durationMs
        );
    }

    @PostMapping("/performance/database-pool")
    public PerformanceLabService.PressureSubmission databasePoolPressure(
            @RequestParam(defaultValue = "20") int tasks,
            @RequestParam(defaultValue = "5000") long durationMs
    ) {
        return performanceLabService.submitDatabasePressure(
                tasks,
                durationMs
        );
    }

    @PostMapping("/performance/slow-query")
    public PerformanceLabService.SlowQueryResult slowQuery(
            @RequestParam(defaultValue = "2000") long durationMs
    ) {
        return performanceLabService.executeSlowQuery(durationMs);
    }

    @PostMapping("/performance/cpu")
    public PerformanceLabService.PressureSubmission cpuPressure(
            @RequestParam(defaultValue = "8") int tasks,
            @RequestParam(defaultValue = "5000") long durationMs
    ) {
        return performanceLabService.submitCpuPressure(tasks, durationMs);
    }

    @PostMapping("/performance/memory")
    public PerformanceLabService.MemoryAllocation allocateMemory(
            @RequestParam(defaultValue = "32") int megabytes
    ) {
        return performanceLabService.allocateMemory(megabytes);
    }

    @DeleteMapping("/performance/memory")
    public PerformanceLabService.MemoryAllocation releaseMemory() {
        return performanceLabService.releaseMemory();
    }

    @GetMapping("/performance")
    public Map<String, Object> performanceStatus() {
        return performanceLabService.snapshot();
    }
}
