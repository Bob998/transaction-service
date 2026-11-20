package com.ganjunbao.transaction.deployment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class DeploymentLabService {

    private final ApplicationEventPublisher eventPublisher;
    private final boolean enabled;
    private final String version;
    private final String releaseTrack;
    private final String podName;
    private volatile ReadinessState readinessState = ReadinessState.ACCEPTING_TRAFFIC;
    private volatile Instant changedAt = Instant.now();

    public DeploymentLabService(
            ApplicationEventPublisher eventPublisher,
            @Value("${transaction.deployment-lab.enabled:false}")
            boolean enabled,
            @Value("${transaction.release.version:local}")
            String version,
            @Value("${transaction.release.track:stable}")
            String releaseTrack,
            @Value("${transaction.release.pod-name:local}")
            String podName
    ) {
        this.eventPublisher = eventPublisher;
        this.enabled = enabled;
        this.version = version;
        this.releaseTrack = releaseTrack;
        this.podName = podName;
    }

    public ReleaseSnapshot snapshot() {
        return new ReleaseSnapshot(
                version,
                releaseTrack,
                podName,
                readinessState.name(),
                changedAt
        );
    }

    public ReleaseSnapshot changeReadiness(boolean ready) {
        if (!enabled) {
            throw new DeploymentLabDisabledException();
        }
        ReadinessState nextState = ready
                ? ReadinessState.ACCEPTING_TRAFFIC
                : ReadinessState.REFUSING_TRAFFIC;
        readinessState = nextState;
        changedAt = Instant.now();
        AvailabilityChangeEvent.publish(
                eventPublisher,
                this,
                nextState
        );
        return snapshot();
    }

    public record ReleaseSnapshot(
            String version,
            String track,
            String podName,
            String readiness,
            Instant changedAt
    ) {
    }
}
