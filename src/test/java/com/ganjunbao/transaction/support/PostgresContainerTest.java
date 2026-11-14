package com.ganjunbao.transaction.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class PostgresContainerTest {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("transaction_test")
                    .withUsername("test")
                    .withPassword("test");
}