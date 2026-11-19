package com.ganjunbao.transaction.gateway;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GatewayFaultSimulator {

    private final Map<String, FaultSettings> settings =
            new ConcurrentHashMap<>();

    public FaultSettings get(String channelCode) {
        return settings.getOrDefault(
                channelCode,
                new FaultSettings(0, false)
        );
    }

    public FaultSettings configure(
            String channelCode,
            long delayMs,
            boolean fail
    ) {
        FaultSettings faultSettings = new FaultSettings(delayMs, fail);
        settings.put(channelCode, faultSettings);
        return faultSettings;
    }

    public void reset(String channelCode) {
        settings.remove(channelCode);
    }

    public Map<String, FaultSettings> all() {
        return Map.copyOf(settings);
    }

    public record FaultSettings(long delayMs, boolean fail) {
    }
}
