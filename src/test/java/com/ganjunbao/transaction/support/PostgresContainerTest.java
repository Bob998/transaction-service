package com.ganjunbao.transaction.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;

import java.util.stream.Stream;

public abstract class PostgresContainerTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("transaction_test")
                    .withUsername("test")
                    .withPassword("test");

    @ServiceConnection(name = "redis")
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    static {
        Startables.deepStart(Stream.of(POSTGRES, REDIS)).join();
    }
}
