package com.orchestragent.daemon.cli;

import com.orchestragent.daemon.cli.client.DaemonApiException;
import com.orchestragent.daemon.cli.client.DaemonClient;
import com.orchestragent.daemon.cli.runner.TaskRunner;
import com.orchestragent.daemon.protocol.ClaimedTask;
import com.orchestragent.daemon.protocol.DaemonWorkspace;
import com.orchestragent.daemon.protocol.HeartbeatResponse;
import com.orchestragent.daemon.protocol.ProviderInfo;
import com.orchestragent.daemon.protocol.RegisterRequest;
import com.orchestragent.daemon.protocol.RegisteredRuntime;
import com.orchestragent.daemon.provider.CancellationToken;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * The running daemon ({@code start}). It registers the chosen providers in every workspace of the token's owner,
 * keeps them online with a heartbeat, claims tasks while it has free slots and runs each on its own thread with
 * {@link TaskRunner}. {@link #stop()} stops claiming, waits for running tasks (up to a grace period), stops the rest,
 * and takes the runtimes offline.
 */
public final class Daemon {

    /** How the daemon paces itself. */
    public record Settings(int maxTasks, Duration heartbeatInterval, Duration pollInterval, Duration shutdownGrace) {

        public static final int MAX_TASKS_LIMIT = 10;

        public Settings {
            if (maxTasks < 1 || maxTasks > MAX_TASKS_LIMIT) {
                throw new UserError("--max-tasks must be between 1 and " + MAX_TASKS_LIMIT);
            }
        }

        public static Settings defaults(int maxTasks) {
            return new Settings(maxTasks, Duration.ofSeconds(15), Duration.ofSeconds(3), Duration.ofSeconds(30));
        }
    }

    private static final Logger LOG = System.getLogger(Daemon.class.getName());

    /** How long tasks get to report after being told to stop, once the grace period is over. */
    private static final Duration REPORT_GRACE = Duration.ofSeconds(15);

    private final DaemonClient client;
    private final String daemonId;
    private final String deviceName;
    private final List<ProviderInfo> providers;
    private final Settings settings;
    private final ScheduledExecutorService scheduler;
    private final TaskRunnerFactory runners;

    private final ExecutorService workers;
    private final Semaphore slots;
    private final Map<UUID, CancellationToken> running = new ConcurrentHashMap<>();
    private final Map<UUID, RegisteredRuntime> runtimes = new LinkedHashMap<>();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final List<ScheduledFuture<?>> loops = new ArrayList<>();
    private volatile boolean stopping;
    private String lastPollError;

    /** Makes the task runner once the scheduler exists (the runner sends logs and polls on it). */
    @FunctionalInterface
    public interface TaskRunnerFactory {
        TaskRunner create(ScheduledExecutorService scheduler);
    }

    public Daemon(
            DaemonClient client,
            String daemonId,
            String deviceName,
            List<ProviderInfo> providers,
            Settings settings,
            ScheduledExecutorService scheduler,
            TaskRunnerFactory runners) {
        this.client = Objects.requireNonNull(client, "client");
        this.daemonId = Objects.requireNonNull(daemonId, "daemonId");
        this.deviceName = Objects.requireNonNull(deviceName, "deviceName");
        this.providers = List.copyOf(providers);
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.runners = Objects.requireNonNull(runners, "runners");
        this.workers = Executors.newThreadPerTaskExecutor(Thread.ofPlatform().name("task-", 1).factory());
        this.slots = new Semaphore(settings.maxTasks());
    }

    /**
     * Registers in every workspace and starts the heartbeat and the claim loop.
     *
     * @return the runtimes registered
     * @throws UserError when nothing could be registered
     */
    public synchronized List<RegisteredRuntime> start() {
        List<DaemonWorkspace> workspaces;
        try {
            workspaces = client.workspaces();
        } catch (DaemonApiException e) {
            if (e.isUnauthorized()) {
                throw new UserError("The server refused the API token; create a new one and run login again");
            }
            throw e;
        }
        if (workspaces.isEmpty()) {
            throw new UserError("The token's owner is not a member of any workspace yet; create or join one first");
        }
        for (DaemonWorkspace workspace : workspaces) {
            try {
                for (RegisteredRuntime runtime : client.register(
                        new RegisterRequest(daemonId, deviceName, workspace.id(), providers)).runtimes()) {
                    runtimes.put(runtime.id(), runtime);
                    LOG.log(Level.INFO, "Registered {0} in workspace {1} (runtime {2})",
                            runtime.provider(), workspace.name(), runtime.id());
                }
            } catch (DaemonApiException e) {
                if (e.isUnauthorized()) {
                    throw new UserError("The server refused the API token; create a new one and run login again");
                }
                LOG.log(Level.WARNING, "Cannot register in workspace {0}: {1}", workspace.name(), e.getMessage());
            }
        }
        if (runtimes.isEmpty()) {
            throw new UserError("Could not register in any workspace");
        }

        TaskRunner runner = runners.create(scheduler);
        loops.add(scheduler.scheduleWithFixedDelay(this::heartbeat,
                settings.heartbeatInterval().toMillis(), settings.heartbeatInterval().toMillis(), TimeUnit.MILLISECONDS));
        loops.add(scheduler.scheduleWithFixedDelay(() -> poll(runner),
                0, settings.pollInterval().toMillis(), TimeUnit.MILLISECONDS));
        LOG.log(Level.INFO, "Daemon {0} on {1} is waiting for tasks (at most {2} at a time)",
                daemonId, deviceName, settings.maxTasks());
        return List.copyOf(runtimes.values());
    }

    /** Blocks until {@link #stop()} has finished. */
    public void awaitStopped() throws InterruptedException {
        stopped.await();
    }

    /** IDs of the tasks running now. */
    public List<UUID> runningTasks() {
        return List.copyOf(running.keySet());
    }

    /**
     * Stops claiming, gives running tasks the grace period to finish, cancels what is still running (reported as
     * {@code daemon_stopped}), and takes the runtimes offline. Safe to call more than once.
     */
    public void stop() {
        synchronized (this) {
            if (stopping) {
                return;
            }
            stopping = true;
            loops.forEach(loop -> loop.cancel(false));
        }
        try {
            workers.shutdown();
            if (!running.isEmpty()) {
                LOG.log(Level.INFO, "Waiting up to {0} s for {1} running task(s)", settings.shutdownGrace().toSeconds(), running.size());
            }
            if (!workers.awaitTermination(settings.shutdownGrace().toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.log(Level.WARNING, "Stopping {0} task(s) that did not finish in time", running.size());
                running.values().forEach(token -> token.cancel(TaskRunner.SHUTDOWN));
                if (!workers.awaitTermination(REPORT_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                    workers.shutdownNow();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workers.shutdownNow();
        } finally {
            deregister();
            scheduler.shutdownNow();
            stopped.countDown();
        }
    }

    // ---- loops -------------------------------------------------------------------------------------------

    private void heartbeat() {
        List<UUID> ids = runtimeIds();
        try {
            HeartbeatResponse response = client.heartbeat(ids);
            List<UUID> refreshed = response.runtimeIds() == null ? List.of() : response.runtimeIds();
            if (!refreshed.containsAll(ids)) {
                LOG.log(Level.WARNING, "The server no longer knows some runtimes of this daemon; restart it to register again");
            }
        } catch (DaemonApiException e) {
            LOG.log(Level.WARNING, "Heartbeat failed: {0}", e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.ERROR, "Heartbeat failed", e);
        }
    }

    /** Claims as many tasks as there are free slots and starts them. */
    void poll(TaskRunner runner) {
        if (stopping) {
            return;
        }
        int free = slots.availablePermits();
        if (free == 0) {
            return;
        }
        List<ClaimedTask> claimed;
        try {
            claimed = client.claim(runtimeIds(), free);
            lastPollError = null;
        } catch (DaemonApiException e) {
            if (e.isUnauthorized()) {
                LOG.log(Level.ERROR, "The server refused the API token (revoked?); stopping. Create a new token and run login again.");
                Thread.ofPlatform().name("stop").start(this::stop);
                return;
            }
            if (!Objects.equals(lastPollError, e.getMessage())) {
                LOG.log(Level.WARNING, "Cannot claim tasks: {0}", e.getMessage());
            }
            lastPollError = e.getMessage();
            return;
        } catch (RuntimeException e) {
            LOG.log(Level.ERROR, "Claiming tasks failed", e);
            return;
        }
        for (ClaimedTask task : claimed) {
            launch(runner, task);
        }
    }

    private void launch(TaskRunner runner, ClaimedTask task) {
        if (!slots.tryAcquire()) {
            // Cannot happen (we ask for at most the free slots); the server puts the task back after its lease.
            LOG.log(Level.WARNING, "No free slot for task {0}; leaving it to go back to the queue", task.taskId());
            return;
        }
        CancellationToken token = new CancellationToken();
        running.put(task.taskId(), token);
        try {
            workers.execute(() -> {
                try {
                    runner.run(task, token);
                } finally {
                    running.remove(task.taskId());
                    slots.release();
                }
            });
        } catch (RejectedExecutionException e) {
            // Shutting down: the task goes back to the queue when its lease ends.
            running.remove(task.taskId());
            slots.release();
        }
    }

    private void deregister() {
        if (runtimes.isEmpty()) {
            return;
        }
        try {
            client.deregister(runtimeIds());
            LOG.log(Level.INFO, "Runtimes are offline; the daemon stopped");
        } catch (DaemonApiException e) {
            LOG.log(Level.WARNING, "Could not take the runtimes offline (the server will notice in 90 s): {0}", e.getMessage());
        }
    }

    private synchronized List<UUID> runtimeIds() {
        return List.copyOf(runtimes.keySet());
    }
}
