package com.microsoft.azure.samples.perfsimjava.model.dto;

import jakarta.validation.constraints.Min;

/**
 * Request DTO for connection pool exhaustion simulation.
 */
public class ConnectionPoolRequest {

    /**
     * Size of the simulated connection pool (like HikariCP maximumPoolSize).
     */
    @Min(value = 1, message = "Pool size must be at least 1")
    private int poolSize = 10;

    /**
     * How long each "query" holds a connection (simulates slow DB queries).
     */
    @Min(value = 1, message = "Query duration must be at least 1 second")
    private int queryDurationSeconds = 30;

    /**
     * Number of concurrent queries to execute (should exceed pool size to cause exhaustion).
     */
    @Min(value = 1, message = "Concurrent queries must be at least 1")
    private int concurrentQueries = 20;

    /**
     * How long queries wait for a connection before timing out.
     */
    @Min(value = 1, message = "Connection timeout must be at least 1 second")
    private int connectionTimeoutSeconds = 5;

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public int getQueryDurationSeconds() {
        return queryDurationSeconds;
    }

    public void setQueryDurationSeconds(int queryDurationSeconds) {
        this.queryDurationSeconds = queryDurationSeconds;
    }

    public int getConcurrentQueries() {
        return concurrentQueries;
    }

    public void setConcurrentQueries(int concurrentQueries) {
        this.concurrentQueries = concurrentQueries;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }
}
