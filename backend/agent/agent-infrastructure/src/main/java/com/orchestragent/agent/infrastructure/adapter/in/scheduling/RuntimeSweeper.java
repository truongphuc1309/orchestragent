package com.orchestragent.agent.infrastructure.adapter.in.scheduling;

import com.orchestragent.agent.application.RuntimeService;
import com.orchestragent.agent.application.TaskMaintenance;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every 30 seconds, looks after what a daemon that went quiet leaves behind: runtimes with no heartbeat for
 * over 90 seconds go offline, running tasks of offline runtimes fail, and tasks a daemon claimed but did not
 * start within 60 seconds go back to the queue. Each step runs on its own, so one failing step does not stop
 * the others, and the next run tries again.
 */
@Component
class RuntimeSweeper {

    private static final Logger log = LoggerFactory.getLogger(RuntimeSweeper.class);

    private final RuntimeService runtimes;
    private final TaskMaintenance maintenance;

    RuntimeSweeper(RuntimeService runtimes, TaskMaintenance maintenance) {
        this.runtimes = runtimes;
        this.maintenance = maintenance;
    }

    @Scheduled(fixedRate = 30_000)
    void sweep() {
        step("Marked {} silent runtime(s) offline", runtimes::markStaleOffline);
        step("Failed {} running task(s) of offline runtimes", maintenance::failTasksOfOfflineRuntimes);
        step("Put {} unstarted task(s) back in the queue", maintenance::requeueExpiredLeases);
    }

    private void step(String message, IntSupplier action) {
        try {
            int count = action.getAsInt();
            if (count > 0) {
                log.info(message, count);
            }
        } catch (RuntimeException e) {
            log.warn("Housekeeping step failed ({}): {}", message, e.toString());
        }
    }
}
