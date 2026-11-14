package com.ganjunbao.transaction.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class PostgresContainerTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("transaction_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        POSTGRES.start();
    }
}
