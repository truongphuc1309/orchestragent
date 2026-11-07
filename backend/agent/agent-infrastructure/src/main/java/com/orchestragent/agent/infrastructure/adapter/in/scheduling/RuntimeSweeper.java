package com.orchestragent.agent.infrastructure.adapter.in.scheduling;

import com.orchestragent.agent.application.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 30 seconds, marks runtimes that have not sent a heartbeat for over 90 seconds as offline. */
@Component
class RuntimeSweeper {

    private static final Logger log = LoggerFactory.getLogger(RuntimeSweeper.class);

    private final RuntimeService runtimes;

    RuntimeSweeper(RuntimeService runtimes) {
        this.runtimes = runtimes;
    }

    @Scheduled(fixedRate = 30_000)
    void markSilentRuntimesOffline() {
        try {
            int count = runtimes.markStaleOffline();
            if (count > 0) {
                log.info("Marked {} silent runtime(s) offline", count);
            }
        } catch (RuntimeException e) {
            // The next run tries again; one failed run must not stop the schedule.
            log.warn("Could not mark silent runtimes offline: {}", e.toString());
        }
    }
}
