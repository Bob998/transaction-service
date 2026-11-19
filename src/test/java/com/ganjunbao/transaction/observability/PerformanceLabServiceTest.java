package com.ganjunbao.transaction.observability;

import com.ganjunbao.transaction.exception.PerformanceLabDisabledException;
import com.ganjunbao.transaction.exception.PerformanceLabRequestException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PerformanceLabServiceTest {

    private PerformanceLabService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void shouldRejectExperimentWhenLabIsDisabled() {
        service = createService(false);

        assertThrows(
                PerformanceLabDisabledException.class,
                () -> service.submitThreadPressure(1, 10)
        );
    }

    @Test
    void shouldBoundAndReleaseRetainedMemory() {
        service = createService(true);

        PerformanceLabService.MemoryAllocation allocation =
                service.allocateMemory(2);

        assertEquals(2L * 1024 * 1024, allocation.retainedBytes());
        assertThrows(
                PerformanceLabRequestException.class,
                () -> service.allocateMemory(1)
        );

        PerformanceLabService.MemoryAllocation released =
                service.releaseMemory();
        assertEquals(-2, released.changedMegabytes());
        assertEquals(0, released.retainedBytes());
    }

    @Test
    void shouldExposeQueueRejectionWhenWorkerPoolIsFull() {
        service = createService(true);

        PerformanceLabService.PressureSubmission result =
                service.submitThreadPressure(3, 100);

        assertEquals(3, result.requested());
        assertEquals(2, result.accepted());
        assertEquals(1, result.rejected());
    }

    private PerformanceLabService createService(boolean enabled) {
        return new PerformanceLabService(
                mock(DataSource.class),
                new SimpleMeterRegistry(),
                enabled,
                1,
                1,
                1,
                1,
                10,
                100,
                2
        );
    }
}
