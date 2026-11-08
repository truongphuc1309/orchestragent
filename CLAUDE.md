@AGENTS.md

# Project state and decisions

Working notes for the next session. `AGENTS.md` has the rules, `PLAN.md` the plan (it is gitignored: it lives on
disk only), `README.md` the user-facing description. This file records where the work stands and the decisions
that the code alone does not explain.

## Status

Steps 0 to 12 of `PLAN.md` are implemented and **committed by the human**. **Step 13 (projects inside a workspace,
issue keys from the project key) is implemented and not committed yet** (waiting for the human's review; it changes
the daemon protocol, `IssueInfo` gained `key`). Its decisions (chosen by the user): project key fixed after creation,
any member creates projects, a page per project plus a Project filter on the Issues page, archive instead of delete;
projects live in the `issue` context, not a new one.

| Step | What | Commit |
| ---- | ---- | ------ |
| 0, 1 | Repo, Gradle, shared kernel, platform | `5f69aa8`, `cca378d` |
| 2 | Email-code login, session cookie, API tokens (needs human review) | `e0afec2` |
| 3 | Frontend skeleton, login page, Jira-style design tokens | `d3fdc0e` |
| 4 | Workspaces, members, invitations, settings pages | `166c25f` |
| 5 | Issues, kanban board (dnd-kit), list, comments | `a2d446f` |
| 6 | Live updates over SSE | `70e393a` |
| 7 | Agents, runtimes, assigning issues to agents | `ffe60a4` |
| 8 | Task queue, daemon API, review gate (needs human review) | `e3f06fa` |
| 9 | Daemon CLI, provider SPI, mock provider | `5a59664` |
| 10 | Claude Code provider | `30e90bc` |
| 11 | Usage page, inbox context and page | `52d0020` |
| 12 | Login rate limit, dev seed data, Playwright E2E, docs | `88090d2` |
| 13 | Projects, per-project issue keys (changes the daemon API: needs human review) | not committed |

Last full run (step 13): `./gradlew build` 952 tests (829 backend, 123 daemon; the real-Claude smoke test skipped), frontend 429 tests (34 files), E2E 1 test passing;
`typecheck`, `lint`, `build` clean. 5 break-the-rule checks (one needed a new test first, see PLAN.md step 13). By hand
on the dev database with old data (a `pg_dump` was taken first, outside the repo): the migration gave each workspace
`General`/`GEN` with the old numbers; projects, issues, filter and archive checked in the browser. Not done by hand: a
real mouse or keyboard drag on a project board (checked through the move API instead).

Step 12 run: `./gradlew build` 865 tests (745 backend, 120 daemon; the real-Claude smoke test skipped),
frontend 374 tests (31 files), E2E 1 test passing; `typecheck`, `lint`, `build` clean. 5 break-the-rule checks each failed
the right test (see PLAN.md step 12). Not done by hand: the seed data on the real dev database (only an integration test
on Postgres).

Previous run (step 11): `./gradlew build` 851 tests (731 backend, 120 daemon; the real-Claude smoke test skipped),
frontend 371 tests (29 files); `typecheck`, `lint`, `build` clean. 12 break-the-rule checks each failed the right test.
By hand (mock daemon, dev backend): the inbox page updated live (no reload) when the mock agent finished an issue and
when a second user (logged in with the dev code from a script) assigned an issue; the badge counted 1, 2, then 1 after
opening a notification (which went to the issue) and 0 after "Mark all as read"; the second user's inbox stayed empty.
The usage page showed the dev data (9 runs, 2 agents, 5 issues) for 30 and 7 days, cost and runs per day, tooltips.

Step 10 run: `./gradlew build` 813 tests (696 backend, 117 daemon; the real-Claude smoke test skipped),
0 failures. Frontend unchanged since step 9 (357 tests). By hand: `doctor` and `start` found the real Claude Code
2.1.311 (from inside a Claude Code shell, `CLAUDECODE=1`), registered a `claude` runtime, and reported a wrong
`claude.permissionMode` and the `bypassPermissions` warning. With the user's permission (it costs money) the real
Claude ran: the smoke test (`ORCHESTRAGENT_RUN_REAL_CLAUDE=1`, haiku, answer then resume) passed, and in the app an
agent on haiku created a file → comment → in review → request changes → resumed the same session in the same folder
and edited the file → approve → done (each run under $0.01). A real stream-json run was recorded, cleaned of paths and
account details, and kept as the fixture `real-2.1.311-haiku.jsonl`. Always ask before running the real Claude.

Step 9 by hand: the daemon was run with the mock provider against the real app: assign →
in progress → live log → agent comment → in review → request changes (resumed in the same folder) → approve → done;
cancel of a hanging run; `[mock:fail]` → blocked. Ctrl+C (graceful stop) was **not** tried by hand: background
processes in this harness can only be killed hard; `DaemonTest` covers it.

## The daemon (step 9)

- Modules: `daemon-provider-api` (SPI, package `com.orchestragent.daemon.provider`), `daemon-provider-mock`,
  `daemon-cli` (packages `cli`, `cli.client`, `cli.config`, `cli.runner`). Jackson 3 (`tools.jackson`), picocli,
  JDK `HttpClient`. Tests use a fake server on the JDK `HttpServer` (`cli.testing.FakeServer`, `FakeDaemonApi`, which
  mimics the real server's rules) and the mock provider with a 5 ms step.
- Run it by hand: `./gradlew :daemon:daemon-cli:installDist`, then `orchestragent-daemon.bat` in
  `daemon/daemon-cli/build/install/orchestragent-daemon/bin`. Set `ORCHESTRAGENT_HOME` to a scratch folder so the
  user's real `~/.orchestragent` is not touched. Use `--server http://localhost:8080`: Vite listens on `[::1]` only and
  Java connects to `localhost` over IPv4. Stop the daemon before `installDist` again (Windows locks the jars).
- Short task IDs (logs, folder names) are the **last** 8 characters: the first ones of a UUIDv7 are a timestamp.
- A test that runs `start` must not be able to hang: `CommandsTest` wraps it in `assertTimeoutPreemptively`.
- Known, not fixed: when a browser leaves a page, the backend logs `AuthorizationDeniedException ... response is
  already committed` for the closing SSE stream (async dispatch goes through Spring Security again). Noise from
  step 6, unrelated to the daemon.

## The Claude Code provider (step 10)

- `daemon-provider-claude`, package `com.orchestragent.daemon.provider.claude`: `StreamJsonParser`, `ClaudeSettings`
  (the `"claude"` section of `config.json`, through `AgentProvider.configure(ProviderSettings)`), `Executables`
  (PATH/PATHEXT lookup; a `.cmd` runs as `cmd.exe /d /s /c "<whole line>"`, one argument, quoted by hand),
  `ClaudeProvider`.
- Process tests use `FakeClaude`, a Java main class in the test sources started as a real process
  (`java -cp <test classes> FakeClaude`); the scenario is named in the prompt (`scenario:hang`, ...). Tests that could
  hang use `assertTimeoutPreemptively`, and every fake child exits by itself after 2 minutes.
- **Windows pitfall found by a break-the-rule run:** a child process that survives keeps the CLI's stdout open, and
  closing that pipe from another thread blocks forever on Windows (JUnit `@Timeout` cannot interrupt it). The provider
  therefore kills every process it saw during the run when the run ends, and never waits on a pipe: it closes leftover
  streams on a background thread and stops the parser.
- `daemon-cli` commands get their providers from outside (`OrchestragentDaemon.commandLine(store, providers)`), so CLI
  tests use only the mock and never run a real `claude` found on the machine.
- Processes started in the background by this harness (`bootRun`, the daemon) survive `TaskStop`, and killing them by
  PID is not permitted here: ask the user to run `taskkill /PID ... /T /F`.

## Usage and inbox (step 11)

- Usage is a read model in the agent context (`UsageService`, `UsageReader` port, SQL in `UsageReaderAdapter`), not a
  context of its own. Inbox is the fifth context (`backend/inbox`, schema `inbox`, migration order 50).
- `IssueDetails` (issue-contract) gained `creatorType` and `creatorId`; test fakes that build it pass them too.
- `SseHub` knows the user of each connection (`register(workspaceId, userId)`, `publishToUser`); workspace events still
  reach everyone. `EventStreamController` registers with the user.
- `TaskWorkflowTestBase` is public so tests of other packages (`com.orchestragent.inbox`) can use the world it sets up.
- Frontend `fakeServer` answers `GET /api/workspaces/{id}/inbox/unread-count` with 0 when a test does not handle it
  (every workspace page asks for it, for the sidebar badge). That is the only default; tests of the badge handle it.
- The daily usage chart is plain HTML/CSS (no chart library), one measure at a time, with a table view.

## Projects (step 13)

- Projects are in the issue context: `Project`, `ProjectKey`, `IssueKey`, `ProjectPolicy`, `ProjectRepository` (domain),
  `ProjectService` (application), `ProjectController`, `ProjectEntity`, migration `issue/V3__projects.sql`. Inbox
  migration `inbox/V2__issue_key.sql` replaced `issue_number` with `issue_key`.
- Issue keys (`WEB-12`) are built by the server and never change, so they may be copied (inbox, daemon prompt, work
  folder). Issues store `project_key`; a composite FK keeps it equal to the project's. `projects.next_number` is not
  mapped in JPA on purpose (only `IssueNumbersAdapter` changes it with SQL).
- Old data: one `General` project with key `GEN` per workspace that had issues; inbox rows got `'GEN-' || issue_number`.
  The user's dev database is migrated (test data left in "Daemon Check": archived project `WEB` with `WEB-1..3`).
- Board positions stay per (workspace, status) across projects; a project board is a filtered view. Lists sort by
  `created_at` (numbers only compare inside a project).
- Live event `project.updated` (`{projectId}`) for every project change; the frontend invalidates
  `['workspaces', ws, 'projects']`.
- Frontend: `features/project` (pages `ProjectsPage`, `ProjectIssuesPage`, `ProjectFormDialog`, `suggestKey`), shared
  `issue/components/IssuesView` for the Issues page and the project page, routes `projects`, `projects/:projectKey`,
  `issues/:issueKey`. `issue/key.ts` is gone. Test fixtures: `ACM_PROJECT` (key `ACM`), so sample issues are still
  `ACM-n`; `fakeServer` answers the sidebar's `GET .../projects` with `[]` by default (the second and last default);
  `?includeArchived=true` (issue pages) must be handled by each test.
- Tests that open an SSE stream must create their project first and wait until its `ProjectChanged` publication is
  handled, or `project.updated` shows up in the stream (`EventStreamApiTest.projectAnnounced`).
- Integration tests create issues with `projectIn(workspaceId, cookie)` / `createProject(...)` from `IntegrationTest`.
- Board drag and drop (fixed after review): `IssueBoard` keeps an `Arrangement` (column order) from drag start until
  the issues prop changes after the drop. `onDragOver` moves the card into the column it is over (so the drop
  animation lands right); `onDragEnd` computes the final order synchronously (the cache update comes a microtask
  later, after the drag has ended). A card's sortable `data.status` is the column showing it, not `issue.status`.
  To measure it in the browser pane, widen the viewport first (`resize_window`): off-screen pointer events trigger
  dnd-kit auto-scroll. A hidden pane never finishes Web Animations, so the drop overlay stays there.

## Hardening, seed data and E2E (step 12)

- Rate limit of `POST /api/auth/code` per IP: `SlidingWindowRateLimiter` (platform, in memory) used by `AuthController`;
  bean `authCodeRateLimiter` in `IdentityConfig`. The `test` profile sets the limit very high because the whole suite
  shares one address; `AuthCodeRateLimitTest` has its own context with limit 3. Behind a proxy:
  `server.forward-headers-strategy=native`.
- `DevDataSeeder` (`backend/app/.../dev`, `dev` profile): demo user `demo@example.com`, workspace "Demo", 8 issues, no
  agents (an agent needs a runtime, which only a real daemon creates). `backend/app` therefore depends on three
  `*-application` modules; nothing else may use them.
- `RouteErrorPage` is the `errorElement` of every page route. Every page already had loading, empty and error states.
- E2E: `pnpm -C e2e test` (set `JAVA_HOME` first; Docker must run). Own ports 18080/15173 and own database
  `orchestragent_e2e` (recreated with `docker exec` into `orchestragent-postgres`), real Chrome (no browser download),
  `E2E_SKIP_BUILD=1` skips Gradle. The test starts the daemon itself after creating the workspace (a daemon registers
  only in workspaces that exist at start). Logs: `e2e/test-results/logs/`.
- Experiment trap: `git checkout <file>` to undo a break-the-rule experiment destroys uncommitted work (the whole step
  is uncommitted); copy the file to a temp folder first and copy it back.
- Docs: README has the architecture diagrams (Mermaid), the provider guide and the demo script;
  `docs/architecture-report.md` (Vietnamese) is the report material.

## Working agreements with this user

- The user speaks Vietnamese; reply in Vietnamese. Code, comments, identifiers, logs and error messages stay English.
- **Never run `git commit` or `git push`.** The user reviews and commits after each step, then says "Làm Bước N luôn".
  Suggest a Conventional Commits message at the end of a step.
- Each step ends with: build passes, tests pass, a manual check in the real app when the step has UI or runtime
  behaviour, docs updated (`README.md`, `PLAN.md`), and a closing message with what was done, what was found, what
  is not done or not verified.
- Check that tests can fail: after writing tests for a rule, break the rule temporarily, see the test fail, restore.
  Report only what was actually run.
- Do not change an existing test just to make it pass. When behaviour changes on purpose, update the test and say why.

## Environment (Windows)

- `java` is not on PATH: `export JAVA_HOME="$USERPROFILE/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2"`.
- `pnpm` is not installed globally: `corepack pnpm@10.18.0 -C frontend <command>`.
- Docker Desktop must run for integration tests (Testcontainers) and for `docker compose up -d` (Postgres, Mailpit).
- `.env` has an empty `JWT_SECRET` and must not be edited. For manual runs pass a throwaway one in the environment
  with `DEV_LOGIN_CODE=123456` and `--spring.profiles.active=dev` (that code logs in any address).
- In this harness, long shell heredocs and commands full of backticks or nested quotes fail or get blocked. Create
  and edit files with the Write and Edit tools. Shell state does not carry over: start every Gradle or pnpm command
  with `cd` to the repo root.

## Architecture decisions worth knowing

- Modular monolith; each context (`identity`, `workspace`, `issue`, `agent`, `inbox`) has four layer modules and its own
  Postgres schema and Flyway location. Migration order: platform 0, identity 10, workspace 20, issue 30, agent 40.
  Contexts reach each other only through `*-contract` (facades: `WorkspaceAccess`, `WorkspaceDirectory`, `UserDirectory`,
  `AgentDirectory`, `IssueDirectory`; integration events). ArchUnit and `SchemaIsolationTest` enforce it.
- Event listeners (`@ApplicationModuleListener`) live in `*-infrastructure` and only forward to application services;
  the logic and the idempotency are in `*-application`. `@EnableAsync` is on, so listeners run after commit on their
  own thread: tests wait with Awaitility. `completion-mode: delete` keeps `event_publication` from growing.
- Idempotency of events is by **source event**: a task and a comment that an event causes store its `source_event_id`
  (unique). This replaces the `(issue_id, trigger, trigger_comment_id)` key of the plan.
- Auth: passwordless email code; JWT in HttpOnly cookie `oa_session`; CSRF double-submit cookie. The daemon API
  (`/api/daemon/**`) has its own filter chain: API tokens (`oat_...`) only, no cookies, no CSRF. A token is not a
  browser session and a cookie is not a daemon token (both are tested). Outsiders get 404, not 403, on workspace
  endpoints.
- Workspace roles: owner manages anyone, admin manages members only, member nobody; always at least one owner.
  `MemberPolicy` (backend) and `permissions.ts` (frontend) share hand-written truth tables.
- Issues: any status may follow any other; the board has six columns (cancelled only in the list). Drag and drop sends
  `{status, aboveId, belowId}` and the server computes a float `position` (renumbers a column when neighbours get too
  close; a stale drop is 409 `issue.stale_move`). Only the creator, admins and owners delete an issue.
- Agents: an agent runs work on the computer of whoever owns its runtime, so **a person can only point an agent at their
  own runtime** (even an admin). Creator, admins and owners edit, archive, restore (`AgentPolicy`). Active names are
  unique ignoring case (partial unique index). Archived agents cannot be assigned or edited, but keep their name.
- Tasks: states `queued, dispatched, running, completed, failed, cancelled`. One queued task per issue (partial unique
  index). Claim is one SQL statement with `FOR UPDATE SKIP LOCKED`; a task waits while another task of its issue is
  dispatched or running. Lease is 60 s; runtimes silent for 90 s go offline; a job every 30 s requeues expired leases
  and fails running tasks of offline runtimes (`runtime_offline`). `@Version` on `agent_tasks`. Daemon calls that
  may be retried (start, complete, fail, messages) are idempotent; reports for a cancelled task are ignored.
- Review gate: task started, issue to in progress; completed, the result becomes an agent comment and the issue goes to
  in review; failed, a system comment and blocked. An agent can never mark an issue done. Any member approves or
  sends back (comment required); both need the issue in review. The send-back comment carries `changesRequested` in
  `CommentAdded`, so it causes exactly one task.
- Realtime: `GET /api/workspaces/{id}/events` (members only). Events say only what changed (`issue.created|updated|deleted`,
  `comment.created`, `task.updated`, `task.message`); the client invalidates queries and refetches. The `heartbeat`
  event is a real event (browsers hide SSE comments), and the client treats 60 s of silence as a dead connection.
- Frontend: every response is parsed with zod, no `as` and no `any` outside `shared/ui` (lint errors). Colours only as
  tokens in `index.css` (a test forbids raw colours). Query keys always contain the workspace ID. `fakeServer` fails any
  unhandled request, so tests that render a page must handle every request that page makes. Every element with
  `overflow-auto`/`overflow-x-auto` must also be `relative` (a test checks it): otherwise absolute children such as
  `sr-only` texts escape it and the whole page scrolls sideways (that was the board's case).

## Known limitations and open points

- A daemon registers in the workspaces its owner has when it starts; joining another workspace needs a restart.
- Issues cannot move to another project and project keys cannot change; no per-project roles; a new workspace has no
  project until someone creates one.
- Real stream-json has more message kinds than the parser shows (`system/thinking_tokens`, `rate_limit_event`,
  thinking blocks); they are skipped on purpose. Only one real recording exists (haiku, 2.1.311).
- Cancelling a task leaves the issue in its current status (usually in progress) until someone moves it.
- `AgentTaskStarted` and `AgentTaskCompleted` are handled on different threads; in theory they can swap if they arrive
  within milliseconds and leave the issue in progress. Not seen in practice.
- Two events racing can make one listener hit a unique violation; Modulith redelivers and the next run skips it.
- Not done: limits on invitations, workspaces and agents per user; mobile navigation (the sidebar is hidden below `md`);
  unassigning issues of members who leave a workspace; per-user limits and a rate limit on other endpoints; E2E on CI and failure scenarios in E2E.
- Review focus for steps 2 and 8 (authentication and the daemon API): `DaemonSecurityConfig`, `DaemonController`,
  `TaskExecutionService` (ownership and membership checks), the claim SQL in `TaskRepositoryAdapter`.
- Review focus for step 13 (daemon protocol change): `IssueInfo` (new `key`, `label()`), its mapping in
  `DaemonController`, and how the daemon uses the server-sent key: `PromptBuilder` (heading through `oneLine`) and
  `WorkDirs.folderLabel` (only letters, digits and dashes reach the file system, otherwise the number).

## Pitfalls met in tests

- MockMvc cannot exercise Tomcat's real async dispatch of SSE; that was checked by hand with two browser tabs.
- Sonner toasts live in a global store; `test/setup.ts` dismisses them before each test.
- `vi.unstubAllGlobals()` in a test removes the stubbed `EventSource`; `setup.ts` stubs it again before each test.
- Scheduled SSE heartbeats can arrive in any open stream during a test; event assertions must ignore `heartbeat`.
- `JsonPath.read` inside `String.valueOf(...)` picks the `char[]` overload: assign to an `Object` first.
- The in-memory fakes in application tests keep object identity, so a service that changes an object before a check that
  can fail leaves it changed; validate first, then change.
