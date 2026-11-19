package com.ganjunbao.transaction.observability;

import com.ganjunbao.transaction.exception.PerformanceLabDisabledException;
import com.ganjunbao.transaction.exception.PerformanceLabRequestException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PerformanceLabService {

    private static final int BYTES_PER_MEGABYTE = 1024 * 1024;

    private final DataSource dataSource;
    private final boolean enabled;
    private final int maxTasks;
    private final long maxDurationMs;
    private final int maxRetainedMb;
    private final ThreadPoolExecutor workerExecutor;
    private final ThreadPoolExecutor databaseExecutor;
    private final List<byte[]> retainedMemory = new ArrayList<>();
    private final AtomicLong retainedBytes = new AtomicLong();
    private final Counter completedExperiments;
    private final Counter failedExperiments;
    private final Timer databaseQueryTimer;
    private volatile double cpuSink;

    public PerformanceLabService(
            DataSource dataSource,
            MeterRegistry registry,
            @Value("${transaction.performance-lab.enabled:false}")
            boolean enabled,
            @Value("${transaction.performance-lab.worker.core-size:4}")
            int workerCoreSize,
            @Value("${transaction.performance-lab.worker.max-size:8}")
            int workerMaxSize,
            @Value("${transaction.performance-lab.worker.queue-capacity:20}")
            int workerQueueCapacity,
            @Value("${transaction.performance-lab.database-workers:20}")
            int databaseWorkers,
            @Value("${transaction.performance-lab.max-tasks:100}")
            int maxTasks,
            @Value("${transaction.performance-lab.max-duration-ms:10000}")
            long maxDurationMs,
            @Value("${transaction.performance-lab.max-retained-mb:128}")
            int maxRetainedMb
    ) {
        this.dataSource = dataSource;
        this.enabled = enabled;
        this.maxTasks = maxTasks;
        this.maxDurationMs = maxDurationMs;
        this.maxRetainedMb = maxRetainedMb;
        this.workerExecutor = new ThreadPoolExecutor(
                workerCoreSize,
                workerMaxSize,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(workerQueueCapacity),
                namedThreadFactory("performance-worker-"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.databaseExecutor = new ThreadPoolExecutor(
                databaseWorkers,
                databaseWorkers,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxTasks),
                namedThreadFactory("database-pressure-"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.completedExperiments = Counter.builder(
                        "performance.lab.experiments"
                )
                .tag("outcome", "completed")
                .register(registry);
        this.failedExperiments = Counter.builder(
                        "performance.lab.experiments"
                )
                .tag("outcome", "failed")
                .register(registry);
        this.databaseQueryTimer = Timer.builder(
                        "performance.lab.database.query.duration"
                )
                .publishPercentileHistogram()
                .description("性能实验SQL执行耗时")
                .register(registry);
        registerExecutorMetrics(registry, workerExecutor, "worker");
        registerExecutorMetrics(registry, databaseExecutor, "database");
        Gauge.builder(
                        "performance.lab.memory.retained.bytes",
                        retainedBytes,
                        AtomicLong::get
                )
                .description("性能实验主动保留的堆内存")
                .register(registry);
    }

    public PressureSubmission submitThreadPressure(
            int tasks,
            long durationMs
    ) {
        checkRequest(tasks, durationMs);
        return submit(
                "thread-pool",
                workerExecutor,
                tasks,
                () -> sleep(durationMs),
                durationMs
        );
    }

    public PressureSubmission submitCpuPressure(
            int tasks,
            long durationMs
    ) {
        checkRequest(tasks, durationMs);
        return submit(
                "cpu",
                workerExecutor,
                tasks,
                () -> burnCpu(durationMs),
                durationMs
        );
    }

    public PressureSubmission submitDatabasePressure(
            int tasks,
            long durationMs
    ) {
        checkRequest(tasks, durationMs);
        return submit(
                "database-pool",
                databaseExecutor,
                tasks,
                () -> executeSleepQuery(durationMs),
                durationMs
        );
    }

    public SlowQueryResult executeSlowQuery(long durationMs) {
        checkEnabled();
        checkDuration(durationMs);
        long startedAt = System.nanoTime();
        executeSleepQuery(durationMs);
        return new SlowQueryResult(
                durationMs,
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
        );
    }

    public synchronized MemoryAllocation allocateMemory(int megabytes) {
        checkEnabled();
        if (megabytes <= 0) {
            throw new PerformanceLabRequestException(
                    "megabytes 必须大于0"
            );
        }
        long proposed = retainedBytes.get()
                + (long) megabytes * BYTES_PER_MEGABYTE;
        long maximum = (long) maxRetainedMb * BYTES_PER_MEGABYTE;
        if (proposed > maximum) {
            throw new PerformanceLabRequestException(
                    "保留内存不能超过 " + maxRetainedMb + "MB"
            );
        }
        for (int index = 0; index < megabytes; index++) {
            byte[] block = new byte[BYTES_PER_MEGABYTE];
            block[0] = 1;
            retainedMemory.add(block);
        }
        retainedBytes.set(proposed);
        return new MemoryAllocation(megabytes, proposed);
    }

    public synchronized MemoryAllocation releaseMemory() {
        checkEnabled();
        int released = retainedMemory.size();
        retainedMemory.clear();
        retainedBytes.set(0);
        return new MemoryAllocation(-released, 0);
    }

    public Map<String, Object> snapshot() {
        return Map.of(
                "enabled", enabled,
                "worker", executorSnapshot(workerExecutor),
                "database", executorSnapshot(databaseExecutor),
                "retainedMemoryBytes", retainedBytes.get(),
                "limits", Map.of(
                        "maxTasks", maxTasks,
                        "maxDurationMs", maxDurationMs,
                        "maxRetainedMb", maxRetainedMb
                )
        );
    }

    @PreDestroy
    public void shutdown() {
        workerExecutor.shutdownNow();
        databaseExecutor.shutdownNow();
    }

    private PressureSubmission submit(
            String experiment,
            ThreadPoolExecutor executor,
            int tasks,
            Runnable action,
            long durationMs
    ) {
        int accepted = 0;
        int rejected = 0;
        for (int index = 0; index < tasks; index++) {
            try {
                executor.execute(() -> runExperiment(action));
                accepted++;
            } catch (RejectedExecutionException exception) {
                rejected++;
            }
        }
        return new PressureSubmission(
                experiment,
                tasks,
                accepted,
                rejected,
                durationMs
        );
    }

    private void runExperiment(Runnable action) {
        try {
            action.run();
            completedExperiments.increment();
        } catch (RuntimeException exception) {
            failedExperiments.increment();
        }
    }

    private void executeSleepQuery(long durationMs) {
        long startedAt = System.nanoTime();
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT pg_sleep(?)"
                )
        ) {
            statement.setDouble(1, durationMs / 1000.0);
            statement.execute();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "数据库压力实验执行失败",
                    exception
            );
        } finally {
            databaseQueryTimer.record(
                    System.nanoTime() - startedAt,
                    TimeUnit.NANOSECONDS
            );
        }
    }

    private void burnCpu(long durationMs) {
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(durationMs);
        double value = cpuSink;
        while (System.nanoTime() < deadline
                && !Thread.currentThread().isInterrupted()) {
            value = Math.sqrt(value + 13.37) * 1.000001;
        }
        cpuSink = value;
    }

    private void sleep(long durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void checkRequest(int tasks, long durationMs) {
        checkEnabled();
        if (tasks <= 0 || tasks > maxTasks) {
            throw new PerformanceLabRequestException(
                    "tasks 必须在1到" + maxTasks + "之间"
            );
        }
        checkDuration(durationMs);
    }

    private void checkDuration(long durationMs) {
        if (durationMs <= 0 || durationMs > maxDurationMs) {
            throw new PerformanceLabRequestException(
                    "durationMs 必须在1到" + maxDurationMs + "之间"
            );
        }
    }

    private void checkEnabled() {
        if (!enabled) {
            throw new PerformanceLabDisabledException();
        }
    }

    private void registerExecutorMetrics(
            MeterRegistry registry,
            ThreadPoolExecutor executor,
            String pool
    ) {
        Gauge.builder(
                        "performance.lab.executor.active",
                        executor,
                        ThreadPoolExecutor::getActiveCount
                )
                .tag("pool", pool)
                .register(registry);
        Gauge.builder(
                        "performance.lab.executor.queue.size",
                        executor,
                        value -> value.getQueue().size()
                )
                .tag("pool", pool)
                .register(registry);
        Gauge.builder(
                        "performance.lab.executor.pool.size",
                        executor,
                        ThreadPoolExecutor::getPoolSize
                )
                .tag("pool", pool)
                .register(registry);
        Gauge.builder(
                        "performance.lab.executor.completed",
                        executor,
                        ThreadPoolExecutor::getCompletedTaskCount
                )
                .tag("pool", pool)
                .register(registry);
    }

    private ExecutorSnapshot executorSnapshot(ThreadPoolExecutor executor) {
        return new ExecutorSnapshot(
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getQueue().remainingCapacity(),
                executor.getCompletedTaskCount()
        );
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(
                    runnable,
                    prefix + sequence.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        };
    }

    public record PressureSubmission(
            String experiment,
            int requested,
            int accepted,
            int rejected,
            long durationMs
    ) {
    }

    public record SlowQueryResult(
            long requestedDurationMs,
            long actualDurationMs
    ) {
    }

    public record MemoryAllocation(
            int changedMegabytes,
            long retainedBytes
    ) {
    }

    public record ExecutorSnapshot(
            int poolSize,
            int activeThreads,
            int queuedTasks,
            int remainingQueueCapacity,
            long completedTasks
    ) {
    }
}
