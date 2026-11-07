package com.orchestragent.agent.application;

import com.orchestragent.agent.domain.AgentTask;
import com.orchestragent.agent.domain.RuntimeId;
import com.orchestragent.agent.domain.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The housekeeping that keeps tasks from getting stuck when a daemon disappears. The jobs call these every 30
 * seconds; each method does one pass and says how many tasks it touched.
 */
@Service
public class TaskMaintenance {

    /** The explanation written on a task whose computer went away. */
    static final String RUNTIME_OFFLINE_MESSAGE =
            "The computer running this task went offline before the task finished.";

    static final String RUNTIME_OFFLINE = "runtime_offline";

    private final TaskRepository tasks;
    private final TaskEvents events;
    private final Clock clock;

    TaskMaintenance(TaskRepository tasks, TaskEvents events, Clock clock) {
        this.tasks = tasks;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Takes back tasks a daemon claimed but did not start in time. A task goes back to the queue, unless another
     * task of the same issue is already waiting (a comment arrived meanwhile): that one will read everything, so
     * this one is cancelled instead of queued twice.
     */
    @Transactional
    public int requeueExpiredLeases() {
        Instant now = clock.instant();
        int count = 0;
        for (AgentTask task : tasks.findExpiredDispatched(now)) {
            if (tasks.existsQueuedForIssue(task.issueId())) {
                task.cancel(now);
            } else {
                task.requeue();
            }
            tasks.save(task);
            events.changed(task);
            count++;
        }
        return count;
    }

    /** Fails running tasks whose runtime is offline: nobody is working on them any more. */
    @Transactional
    public int failTasksOfOfflineRuntimes() {
        return failAll(tasks.findRunningOnOfflineRuntimes());
    }

    /** Fails the running tasks of these runtimes (their daemon is shutting down). */
    @Transactional
    public int failRunningTasksOf(Collection<RuntimeId> runtimeIds) {
        return runtimeIds.isEmpty() ? 0 : failAll(tasks.findRunningOnRuntimes(runtimeIds));
    }

    private int failAll(List<AgentTask> running) {
        Instant now = clock.instant();
        int count = 0;
        for (AgentTask task : running) {
            if (task.fail(RUNTIME_OFFLINE_MESSAGE, RUNTIME_OFFLINE, null, null, now)) {
                tasks.save(task);
                events.failed(task);
                count++;
            }
        }
        return count;
    }
}
