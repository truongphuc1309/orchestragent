# Orchestragent

People and AI agents working on the same issues. Assign an issue to an agent like you would to a
teammate: a local daemon picks up the task, runs Claude Code CLI on your machine, streams progress in
real time, and moves the issue to review for a human to approve.

> Status: step 10 of `PLAN.md` (the Claude Code provider) is implemented on top of steps 1 to 9: the daemon runs agents
> with Claude Code or with the mock provider. Usage and inbox (step 11) and the end-to-end tests (step 12) are still to
> come.

## Requirements

- JDK 21 (Gradle can also download one automatically through the foojay toolchain resolver, but a JVM
  17+ is still needed to start Gradle itself)
- Docker (Docker Desktop on Windows/macOS)
- Node.js 22+ and pnpm 10 (for `frontend/`). If `pnpm` is not installed, Corepack (bundled with Node) can
  run the pinned version without a global install: `corepack pnpm@10.18.0 -C frontend dev`.

## Getting started

```bash
cp .env.example .env        # then fill in JWT_SECRET (openssl rand -base64 32)
docker compose up -d        # Postgres on 5432, Mailpit SMTP on 1025, Mailpit UI on http://localhost:8025
./gradlew build
```

On Windows PowerShell use `Copy-Item .env.example .env` and `.\gradlew.bat build`.

Run the backend (needs Postgres running and `JWT_SECRET` set in `.env`; the app refuses to start with a
secret shorter than 32 bytes):

```bash
./gradlew :backend:app:bootRun --args='--spring.profiles.active=dev'
curl http://localhost:8080/api/health
```

The integration tests start their own Postgres with Testcontainers, so Docker must be running.

### Logging in (step 2)

Login is passwordless: `POST /api/auth/code {email}` emails a 6-digit code (open Mailpit at
http://localhost:8025 in development), `POST /api/auth/verify {email, code}` checks it and sets the
HttpOnly `oa_session` cookie. Codes expire after 10 minutes, allow 5 wrong guesses, can be requested once
per minute per address, and are stored only as a keyed hash.

Browser requests are protected by CSRF tokens: read the `XSRF-TOKEN` cookie and send its value in the
`X-XSRF-TOKEN` header on every write. Requests that use an `Authorization: Bearer` header do not need it.
The daemon API (`/api/daemon/**`) accepts only API tokens (`oat_...`) created via `POST /api/me/tokens`.

With the `dev` profile and `DEV_LOGIN_CODE` set, that fixed code logs in any email address. Other profiles
ignore `DEV_LOGIN_CODE` completely.

### Workspaces and members (step 4)

A user can belong to several workspaces. The creator is the owner; roles are `owner`, `admin` and `member`:
owners manage anyone, admins manage members only, members manage nobody (except leaving), and a workspace always
keeps at least one owner. Admins and owners invite people by email; the email contains a link
(`APP_BASE_URL/invitations/{id}`), and the invitation can only be accepted by someone signed in with the
invited address. Anyone who is not a member of a workspace gets `404` from its endpoints, so its existence is not
revealed. The full rules are in `PLAN.md` (step 4).

REST: `GET|POST /api/workspaces`, `GET /api/workspaces/{id}`, `GET /api/workspaces/{id}/members`,
`PATCH|DELETE /api/workspaces/{id}/members/{userId}`, `GET|POST /api/workspaces/{id}/invitations`,
`DELETE /api/workspaces/{id}/invitations/{invitationId}`, `GET /api/invitations`,
`POST /api/invitations/{id}/accept`. Each context owns its Postgres schema (`workspace` here) and no foreign key
crosses schemas; a test enforces this.

### Issues, board and comments (step 5)

Every workspace member can create, edit, assign, move and comment on issues; only the creator, admins and owners
delete one. Issue numbers (`ORC-12`; the prefix comes from the workspace slug) count up per workspace. Status is one of
`backlog, todo, in_progress, in_review, done, blocked, cancelled` (any status may follow any other); priority is one of
`none, low, medium, high, urgent`. Issues can only be assigned to members until agents arrive in step 7.

REST under `/api/workspaces/{id}/issues`: `GET` (filters `status`, `priority`, `assignee`, `unassigned`, `limit`; returns
`{items, total}`, newest first, at most 1000), `POST`, `GET /{issueId}`, `GET /number/{n}`, `PATCH /{issueId}`,
`PUT /{issueId}/assignee`, `POST /{issueId}/move` (`{status, aboveId, belowId}`: the server computes the position between
the two neighbours and renumbers a column when they get too close; a stale drop answers `409 issue.stale_move`),
`DELETE /{issueId}`, and `GET|POST /{issueId}/comments`. Other contexts hear about changes through the integration
events `IssueCreated`, `IssueUpdated`, `IssueAssigneeChanged`, `IssueDeleted` and `CommentAdded` (module `issue-contract`).

The web app has a six-column board (drag with mouse, touch or keyboard), a list view, filters that are remembered per
workspace, a create dialog and an issue page with inline editing, a properties panel and a comment timeline.

### Live updates (step 6)

`GET /api/workspaces/{id}/events` is a Server-Sent Events stream, open to members only (anyone else gets `404`, like
every workspace endpoint). The issue context's listener turns its integration events into `issue.created`,
`issue.updated`, `issue.deleted` and `comment.created` (each carries only `issueId` and what changed), plus a
`heartbeat` event every 25 seconds. The events carry no data on purpose: the web app marks the affected queries stale
and fetches them again, so a repeated or reordered event cannot show something older than the server has. Delivery
runs through `@ApplicationModuleListener` (after the commit, on its own thread); handled events are deleted from
`event_publication` (`spring.modulith.events.completion-mode: delete`).

The browser side (`shared/api/eventStream.ts`) reconnects with a growing delay when the browser gives up, treats 60
seconds without any event or heartbeat as a dead connection, and fetches the whole workspace again after a break.

### Agents and runtimes (step 7)

A **runtime** is one AI tool (provider, for example `claude`) on one computer (daemon) that registered with a
workspace; it is online while its daemon sends heartbeats, and a job that runs every 30 seconds marks runtimes
silent for over 90 seconds offline. An **agent** is an AI teammate: a name, markdown instructions, an optional
model, and the runtime that does its work. Agents are archived, never deleted: an archived agent cannot be given
work or edited (restore it first), but issues it already has keep its name.

Rules: every member sees agents and runtimes. An agent runs work on the computer of whoever owns its runtime, so
a person can only point an agent at a runtime of their own; the creator, admins and owners edit, archive and
restore it (`AgentPolicy`, mirrored by `permissions.ts`). Active agents of a workspace have distinct names
(ignoring case, also enforced by a partial unique index).

REST under `/api/workspaces/{id}`: `GET /runtimes`, and `GET|POST /agents` (`?includeArchived=true`),
`GET|PUT /agents/{agentId}`, `POST /agents/{agentId}/archive`, `POST /agents/{agentId}/restore`. Registering,
heartbeats and deregistering are use cases of `RuntimeService`; the daemon API that calls them (with API tokens)
arrives in step 8, so for now runtimes can only be created through the service (tests do this).

Issues can be assigned to agents: the issue context asks the agent context through its contract
(`agent-contract`: `AgentDirectory`), and the assignee picker of the web app lists members and agents (agents with
the purple spark). The web app has Agents and Runtimes pages; the Runtimes page also explains how to connect a
computer.

### Agent tasks, the daemon API and the review gate (step 8)

A **task** is one run of an agent on an issue. Tasks are made from what happens on issues (the rules are in
`TaskPlanner`, one test per row of the table in `PLAN.md`): assigning an issue to an agent queues a task unless the
issue is done or cancelled; a person's comment queues one unless one is already waiting; sending work back from
review queues one (trigger `changes_requested`); comments of agents and of the system never do, so agents cannot set
each other off in a loop; unassigning, reassigning or deleting cancels the agent's tasks, and so does archiving the
agent. An issue never has two waiting tasks (a partial unique index), and every event makes at most one task (the task
remembers its source event), because events are delivered at least once.

A task goes `queued` → `dispatched` (a daemon claimed it and has 60 seconds to start it) → `running` →
`completed`, `failed` or `cancelled`. A job (every 30 seconds) puts unstarted claims back in the queue, and fails
running tasks of runtimes that went offline (`runtime_offline`). Claiming is one SQL statement with
`FOR UPDATE SKIP LOCKED`: two daemons polling at once never get the same task, and one issue never runs twice at once.
The calls a daemon repeats when its network drops (start, complete, fail, messages) are safe to repeat, and reports
for a task that was cancelled are accepted and ignored; the daemon learns about the cancellation from `status`.

**Daemon API** (`/api/daemon/**`, API tokens only, wire format in `daemon-protocol`): `GET /workspaces`,
`POST /register`, `/heartbeat`, `/deregister`, `POST /tasks/claim`, `POST /tasks/{id}/start`, `/messages`,
`/complete`, `/fail`, `GET /tasks/{id}/status`. Every call acts for the owner of the token and only reaches what they
own; someone else's runtime or task is "not found". A person who left the workspace gets no more work. A claim hands
over the agent (name, instructions, model), the issue, its comments and, when the agent worked on the issue before,
the session and folder to resume.

**Review gate**: when a task completes, the agent's result becomes a comment of the agent and the issue moves to
"in review"; when it fails, a system comment says why and the issue moves to "blocked"; when it starts, the issue moves
to "in progress". An agent can never mark an issue done: a member calls `POST /issues/{id}/approve` (done) or
`POST /issues/{id}/request-changes {comment}` (back to "to do", the comment is required and starts a new run). Both need
the issue to be in review.

Web API under `/api/workspaces/{id}/tasks`: `GET ?issueId=`, `GET /{taskId}/messages?after=`, `POST /{taskId}/cancel`,
`POST /{taskId}/retry`. The issue page has an "Agent runs" panel (state, duration, tokens, cost, the live log, Cancel
and Retry) and, for an issue in review, the review bar. The live updates of step 6 carry `task.updated` and
`task.message`.

### The daemon (step 9)

The daemon is a plain Java command-line program (`daemon/daemon-cli`, picocli) that runs agents on your computer.
Build and install it, create an API token in the web app (Settings > API tokens), then log in and start it:

```bash
./gradlew :daemon:daemon-cli:installDist
daemon/daemon-cli/build/install/orchestragent-daemon/bin/orchestragent-daemon login --server http://localhost:8080 --token
daemon/daemon-cli/build/install/orchestragent-daemon/bin/orchestragent-daemon doctor
daemon/daemon-cli/build/install/orchestragent-daemon/bin/orchestragent-daemon start --providers mock
```

On Windows run `orchestragent-daemon.bat` from the same folder. In development give the backend's address
(`http://localhost:8080`), not the Vite dev server's: Vite listens on IPv6 only (`[::1]:5173`), and Java connects to
`localhost` over IPv4.

- `login --server URL --token oat_...` checks the token with the server (`--no-verify` skips that) and saves
  `~/.orchestragent/config.json` (server, token and a daemon ID made once), readable only by you (`rw-------`, or an
  access list with only your account on Windows). `--token` without a value asks for it, so it stays out of the shell
  history. Settings in the file that the daemon does not know are kept.
- `doctor` lists the providers and whether each tool is installed, shows the login (never the token) and checks that
  the server accepts it.
- `start [--providers claude,mock] [--max-tasks 2] [--task-timeout 30] [--device-name NAME]` runs in the foreground.
  Without `--providers` it uses every installed tool except the mock. It registers the providers in every workspace
  of the token's owner (one runtime per provider), sends a heartbeat every 15 seconds and claims tasks every
  3 seconds while it has free slots. Each task runs on its own thread: the daemon prepares its folder, writes the
  prompt (agent instructions, the issue, its comments, what the run is for, and how to report), starts the task, runs
  the provider, sends the log in batches every second and checks every 5 seconds whether the task was cancelled.
  The result is reported with retries and backoff; all log lines are sent before it. Ctrl+C stops claiming, waits up to
  30 seconds for running tasks, stops the rest (reported as failed, `daemon_stopped`) and takes the runtimes offline.
- Task folders live under `~/.orchestragent/workspaces/<workspaceId>/<issueNumber>-<last 8 characters of the task ID>/`. A run that
  continues earlier work gets the earlier folder back, and resumes the earlier session only there (AI tools keep
  sessions per folder). A folder sent by the server is used only when it is inside the task's workspace folder.
- `ORCHESTRAGENT_HOME` moves `~/.orchestragent` elsewhere (tests and demos).

Providers implement `AgentProvider` (`daemon/daemon-provider-api`): `id()`, `detect()`, `run(request, listener,
cancel)`, and are found with `java.util.ServiceLoader`, so a new tool is a new module with a
`META-INF/services/com.orchestragent.daemon.provider.AgentProvider` file. The **mock provider** never calls a model:
it pauses between steps (`ORCHESTRAGENT_MOCK_STEP_MS`, default 1000), sends four or five log lines, writes
`mock-notes.md` in the task folder and answers with a text that names the issue. Put `[mock:fail]`, `[mock:hang]` or
`[mock:crash]` in the issue title, description or a comment to make it fail, hang until cancelled or timed out, or
throw; the last marker wins, so a later comment with `[mock:ok]` makes it succeed again.

### The Claude Code provider (step 10)

The `claude` provider (`daemon/daemon-provider-claude`) runs the Claude Code CLI that is installed and signed in on
the computer. `doctor` shows whether it was found (`claude --version`, looked up on `PATH`; on Windows `claude.exe`, or
`claude.cmd` from npm, which is started through `cmd.exe`). A run is

```
claude -p --output-format stream-json --verbose --permission-mode <mode> [--model M] [--resume SESSION]
       [--allowedTools ...] --disallowedTools AskUserQuestion [...]
```

in the task folder, with the prompt on standard input (so no issue text is ever a command-line argument). Each line of
output is JSON: the session ID comes from the `init` message, assistant text and tool calls and tool results become the
live log, and the `result` message gives the final answer (the agent's comment), the tokens (input counts prompt-cache
reads and writes) and the cost. Tool calls show their main argument (`Bash npm test`, `Write notes/BYE.md`; paths in
the task folder are relative). A run stops when it takes longer than `--task-timeout` (30 minutes by default) or prints
nothing for 10 minutes, and when the task is cancelled; stopping kills the whole process tree. When a run ends, any
process it started that is still running (a dev server left in the background, say) is killed too. A later run on the same
issue resumes the session in the same folder; when Claude Code says the session no longer exists, the run starts once
more without it. Variables that Claude Code sets for its own child processes (`CLAUDECODE`, ...) are not passed on, so
the daemon can be started from a Claude Code terminal.

What Claude may do is set in the `"claude"` section of `~/.orchestragent/config.json` (`login` keeps it):

```json
"claude": {
  "permissionMode": "acceptEdits",
  "allowedTools": ["Bash(npm test:*)", "Bash(git diff:*)"],
  "disallowedTools": [],
  "idleTimeoutMinutes": 10,
  "command": "claude"
}
```

The default `acceptEdits` lets the agent read and edit files in its task folder; commands run only when `allowedTools`
allows them. `bypassPermissions` lets it do anything the account can do; the daemon uses it only when you set it, and
prints a warning. A wrong setting stops `start` and `doctor` with a message. `AskUserQuestion` is always disallowed,
because nobody can answer a question during a run.

Tests never run the real CLI: the parser reads JSONL fixtures (one of them recorded from Claude Code 2.1.311) and the
process handling runs a fake CLI (a small Java program). One smoke test runs the real one and costs money:
`ORCHESTRAGENT_RUN_REAL_CLAUDE=1 ./gradlew :daemon:daemon-provider-claude:test --tests '*RealClaudeSmokeTest'`.

### Frontend (step 3)

```bash
pnpm -C frontend install
pnpm -C frontend dev        # http://localhost:5173, proxies /api to the backend on :8080
pnpm -C frontend typecheck && pnpm -C frontend lint && pnpm -C frontend test && pnpm -C frontend build
```

Start the backend and `docker compose up -d` first. Open http://localhost:5173, enter an email, read the
code in Mailpit (http://localhost:8025) and type it in.

The look follows Jira (Atlassian Design System): the palette, typography and shapes are defined once as CSS
variables ("tokens") in `frontend/src/index.css` and described in `PLAN.md` ("Giao diện (UI)"). Components use
token classes only; a test fails if a component contains a raw colour or a default Tailwind palette class.

Layout of `frontend/src`: `app/` (router, providers), `features/<feature>/` (`api.ts`, `schemas.ts`,
`components/`, `pages/`), `shared/api/` (fetch client with CSRF header and zod parsing), `shared/ui/`
(shadcn/ui components). Every API response is parsed with a zod schema; type assertions (`as T`) and `any`
are lint errors outside the generated `shared/ui` files.

## Environment variables

| Variable         | Purpose                                                             |
| ---------------- | ------------------------------------------------------------------- |
| `DB_URL`         | JDBC URL of the Postgres database                                   |
| `DB_USER`        | Database user (also used by Docker Compose to create the user)      |
| `DB_PASSWORD`    | Database password                                                   |
| `JWT_SECRET`     | HS256 signing secret, at least 32 bytes                             |
| `MAIL_HOST`      | SMTP host (Mailpit in development)                                  |
| `MAIL_PORT`      | SMTP port                                                           |
| `APP_BASE_URL`   | Public URL of the web app, used in emails                           |
| `DEV_LOGIN_CODE` | Fixed login code, accepted only with the `dev` profile              |

## Project layout

```
build-logic/   Gradle convention plugins (oa.java-library, oa.spring-library, oa.spring-app, oa.java-app)
backend/       Spring Boot modular monolith: shared-kernel, platform, identity, workspace, issue, agent, inbox, app
daemon/        Java daemon that runs agents locally (protocol, provider SPI, mock and Claude providers, CLI)
frontend/      React + Vite + TypeScript + shadcn/ui
e2e/           Playwright tests (step 12)
```

Architecture and coding rules: see [AGENTS.md](AGENTS.md). Full plan: see [PLAN.md](PLAN.md).

## Technology decisions

- Spring Boot 4.0.9 and Spring Modulith 2.0.9 (latest 4.0.x / 2.0.x patches at bootstrap time).
- Gradle 9.8.2 with a single version catalog in `gradle/libs.versions.toml`.
