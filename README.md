# Orchestragent

People and AI agents working on the same issues. Assign an issue to an agent like you would to a
teammate: a local daemon picks up the task, runs Claude Code CLI on your machine, streams progress in
real time, and moves the issue to review for a human to approve.

> Status: step 7 of `PLAN.md` (agents and runtimes, server side and web app) is implemented on top of steps 1 to 6. Steps 2 to 7
> are awaiting human review. The inbox context is still empty and there is no daemon yet.

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
