package com.ganjunbao.transaction.deployment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeploymentLabServiceTest {

    @Test
    void shouldPublishRefusingTrafficWithoutChangingLiveness() {
        ApplicationEventPublisher publisher = mock(
                ApplicationEventPublisher.class
        );
        DeploymentLabService service = new DeploymentLabService(
                publisher,
                true,
                "week11-v1",
                "stable",
                "pod-1"
        );

        DeploymentLabService.ReleaseSnapshot result =
                service.changeReadiness(false);

        assertThat(result.readiness()).isEqualTo("REFUSING_TRAFFIC");
        assertThat(result.version()).isEqualTo("week11-v1");
        verify(publisher).publishEvent(argThat(event ->
                event instanceof AvailabilityChangeEvent<?>
                        && ((AvailabilityChangeEvent<?>) event).getState()
                        == ReadinessState.REFUSING_TRAFFIC
        ));
    }

    @Test
    void shouldRejectReadinessChangeWhenLabIsDisabled() {
        DeploymentLabService service = new DeploymentLabService(
                mock(ApplicationEventPublisher.class),
                false,
                "local",
                "stable",
                "local"
        );

        assertThatThrownBy(() -> service.changeReadiness(false))
                .isInstanceOf(DeploymentLabDisabledException.class);
    }
}
