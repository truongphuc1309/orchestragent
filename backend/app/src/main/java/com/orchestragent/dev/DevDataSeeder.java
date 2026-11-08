package com.orchestragent.dev;

import com.orchestragent.identity.domain.Email;
import com.orchestragent.identity.domain.User;
import com.orchestragent.identity.domain.UserRepository;
import com.orchestragent.issue.application.CommentService;
import com.orchestragent.issue.application.IssueService;
import com.orchestragent.issue.application.IssueService.CreateIssue;
import com.orchestragent.issue.application.ProjectService;
import com.orchestragent.issue.application.ProjectService.CreateProject;
import com.orchestragent.issue.application.Views.IssueView;
import com.orchestragent.issue.domain.Actor;
import com.orchestragent.issue.domain.Priority;
import com.orchestragent.issue.domain.Status;
import com.orchestragent.workspace.application.Views.WorkspaceView;
import com.orchestragent.workspace.application.WorkspaceService;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fills an empty development database with a demo user, a workspace with two projects and a handful of issues, so
 * the board, the list, the project pages and the filters have something to show on the first run. Runs only in the {@code dev} profile, only
 * when the demo user does not exist yet (so restarting never duplicates anything), and can be turned off with
 * {@code orchestragent.dev-seed.enabled=false}.
 *
 * <p>It uses the same application services as the web API, so the seeded data obeys every rule of the real
 * thing and raises the same events. Agents are not seeded on purpose: an agent needs a runtime, and a runtime
 * exists only once a daemon on a real computer registers; create the first agent in the web app after
 * {@code orchestragent-daemon start}.
 *
 * <p>This class lives in the application module (the composition root) because it needs several contexts at
 * once. Nothing else depends on it.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "orchestragent.dev-seed.enabled", havingValue = "true", matchIfMissing = true)
class DevDataSeeder implements ApplicationRunner {

    static final String DEMO_EMAIL = "demo@example.com";
    static final String WORKSPACE_NAME = "Demo";
    static final String WEB_KEY = "WEB";
    static final String API_KEY = "API";

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository users;
    private final WorkspaceService workspaces;
    private final ProjectService projects;
    private final IssueService issues;
    private final CommentService comments;
    private final Clock clock;
    private final TransactionTemplate transaction;

    DevDataSeeder(
            UserRepository users,
            WorkspaceService workspaces,
            ProjectService projects,
            IssueService issues,
            CommentService comments,
            Clock clock,
            TransactionTemplate transaction) {
        this.users = users;
        this.workspaces = workspaces;
        this.projects = projects;
        this.issues = issues;
        this.comments = comments;
        this.clock = clock;
        this.transaction = transaction;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (RuntimeException e) {
            // Seed data is a convenience; a failure must never stop the application from starting.
            log.warn("Dev seed data was not created: {}", e.toString());
        }
    }

    /** One transaction: either the whole demo workspace exists afterwards or nothing does. */
    void seed() {
        transaction.executeWithoutResult(status -> doSeed());
    }

    private void doSeed() {
        Email email = Email.of(DEMO_EMAIL);
        if (users.findByEmail(email).isPresent()) {
            log.info("Dev seed data already exists ({}); nothing to do", DEMO_EMAIL);
            return;
        }

        User user = users.createIfAbsent(User.register(email, clock.instant()));
        UUID userId = user.id().value();
        WorkspaceView workspace = workspaces.create(userId, WORKSPACE_NAME);
        UUID workspaceId = workspace.id();
        Actor me = Actor.member(userId);
        UUID web = projects.create(userId, workspaceId, new CreateProject(
                WEB_KEY, "Web app", "The browser app: pages, styles and everything people click.")).id();
        UUID api = projects.create(userId, workspaceId, new CreateProject(
                API_KEY, "Backend", "The server: REST API, database and background jobs.")).id();

        issue(userId, workspaceId, api, "Set up the project repository", Status.DONE, Priority.MEDIUM, me,
                "Create the repository, the CI pipeline and the first README.");
        IssueView login = issue(userId, workspaceId, web, "Design the login page", Status.IN_REVIEW, Priority.HIGH, me,
                "Email and one-time code, no password. Show a clear message when the code is wrong or expired.");
        issue(userId, workspaceId, api, "Write the API documentation", Status.IN_PROGRESS, Priority.MEDIUM, me,
                "Describe every endpoint with an example request and response.");
        IssueView flaky = issue(userId, workspaceId, api, "Fix the flaky upload test", Status.BLOCKED, Priority.URGENT,
                null, "Fails about one run in ten on CI. Waiting for the CI logs of the last failure.");
        issue(userId, workspaceId, web, "Add dark mode", Status.TODO, Priority.LOW, null, null);
        issue(userId, workspaceId, web, "Export issues to CSV", Status.TODO, Priority.MEDIUM, me, null);
        issue(userId, workspaceId, web, "Plan the next release", Status.BACKLOG, Priority.NONE, null,
                "Collect what should go into the next release and agree on a date.");
        issue(userId, workspaceId, api, "Evaluate a second database", Status.CANCELLED, Priority.NONE, null,
                "Not needed: one PostgreSQL instance is enough for now.");

        comments.add(userId, workspaceId, login.id(), "Mockups are ready, please check the error states.");
        comments.add(userId, workspaceId, flaky.id(), "Blocked until someone with access sends the CI logs.");

        log.info("Dev seed data created: sign in as {} (workspace '{}', projects {} and {}, {} issues)",
                DEMO_EMAIL, WORKSPACE_NAME, WEB_KEY, API_KEY, 8);
    }

    private IssueView issue(
            UUID userId,
            UUID workspaceId,
            UUID projectId,
            String title,
            Status status,
            Priority priority,
            Actor assignee,
            String description) {
        return issues.create(
                userId, workspaceId, new CreateIssue(projectId, title, description, status, priority, assignee));
    }
}
