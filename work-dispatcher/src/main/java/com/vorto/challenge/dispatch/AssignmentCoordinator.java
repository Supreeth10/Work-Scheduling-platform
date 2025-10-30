package com.vorto.challenge.dispatch;

import com.vorto.challenge.config.DispatchOptimizerProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AssignmentCoordinator {
    private static final Logger log = LoggerFactory.getLogger(AssignmentCoordinator.class);

    private final AssignmentService2 assignmentService2;
    private final DispatchOptimizerProperties properties;
    private final JdbcTemplate jdbcTemplate;
    
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final ReentrantLock lock = new ReentrantLock();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "assignment-optimizer");
        t.setDaemon(true);
        return t;
    });

    public AssignmentCoordinator(AssignmentService2 assignmentService2,
                                 DispatchOptimizerProperties properties,
                                 JdbcTemplate jdbcTemplate) {
        this.assignmentService2 = assignmentService2;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Requests an optimization run. Sets the dirty flag and submits a worker task.
     *
     * @param reason The reason for the run (e.g., "MANUAL", "SCHEDULED")
     * @param correlationId The correlation ID for tracing
     */
    public void requestRun(String reason, String correlationId) {
        log.info("[{}] Optimization run requested. Reason: {}", correlationId, reason);
        
        boolean wasClean = dirty.compareAndSet(false, true);
        if (wasClean) {
            // Only submit a new task if we transitioned from clean to dirty
            executor.submit(() -> optimizationWorker(correlationId));
        } else {
            log.debug("[{}] Optimization already scheduled, will pick up this request", correlationId);
        }
    }

    /**
     * Worker task that drains optimization requests.
     */
    private void optimizationWorker(String correlationId) {
        // Loop while there are pending requests
        while (dirty.compareAndSet(true, false)) {
            lock.lock();
            try {
                // Try to acquire Postgres advisory lock for multi-instance protection
                Boolean acquired = acquireAdvisoryLock();
                if (acquired != null && acquired) {
                    try {
                        log.info("[{}] Advisory lock acquired, running optimization", correlationId);
                        assignmentService2.runOnce(correlationId);
                    } finally {
                        releaseAdvisoryLock();
                        log.debug("[{}] Advisory lock released", correlationId);
                    }
                } else {
                    log.info("[{}] Could not acquire advisory lock, skipping this run (another instance may be running)", 
                            correlationId);
                }
            } catch (Exception e) {
                log.error("[{}] Error during optimization run", correlationId, e);
            } finally {
                lock.unlock();
            }
        }
        log.debug("[{}] Optimization worker finished, no more pending requests", correlationId);
    }

    /**
     * Attempts to acquire a Postgres advisory lock.
     *
     * @return true if acquired, false if already held, null if error
     */
    private Boolean acquireAdvisoryLock() {
        try {
            int advisoryKey = properties.getAdvisoryKey();
            return jdbcTemplate.queryForObject(
                    "SELECT pg_try_advisory_lock(?)",
                    Boolean.class,
                    advisoryKey
            );
        } catch (Exception e) {
            log.warn("Failed to acquire advisory lock, continuing without it", e);
            return null; // Continue without advisory lock on error
        }
    }

    /**
     * Releases the Postgres advisory lock.
     */
    private void releaseAdvisoryLock() {
        try {
            int advisoryKey = properties.getAdvisoryKey();
            jdbcTemplate.execute("SELECT pg_advisory_unlock(" + advisoryKey + ")");
        } catch (Exception e) {
            log.warn("Failed to release advisory lock", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down AssignmentCoordinator executor");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate in time, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for executor shutdown", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

