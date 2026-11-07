# AGENTS.md

Rules for any AI agent (and human) working in this repository. Read this file before changing code.
The full product plan is in `PLAN.md`.

## Project

Orchestragent is a web app where people and AI agents work on the same issues. A user assigns an issue
to an agent; a local **daemon** claims the task, runs Claude Code CLI on the user's machine, streams
progress back in real time, and moves the issue to review.

- Backend: Java 21, Spring Boot 4.0.x, Spring Modulith 2.0.x, Gradle multi-module, PostgreSQL + Flyway.
- Frontend: React + Vite + TypeScript (strict) + shadcn/ui, TanStack Query, Zustand, zod.
- Daemon: plain Java (picocli), pluggable providers loaded with `java.util.ServiceLoader`.
- Base package: `com.orchestragent`.

## General rules

1. All code, comments, identifiers, logs, and error messages are in **English**.
2. Never commit secrets. Configuration comes from environment variables (`.env`, gitignored); document
   every new variable in `.env.example` with an empty or placeholder value.
3. Each step ends with: build passes and the step's tests pass. **AI agents never run `git commit`,
   `git push`, or any other command that records history** (no amending, no tagging). The human reviews
   the working tree and commits. When a step is done, the agent leaves the changes uncommitted and may
   suggest a Conventional Commits message (for example `feat(issue): add board drag and drop`).
4. Changes to authentication, API tokens, or the daemon API need human review before merge.
5. Tests **never** call the real `claude` binary. Use the mock provider or a fake script. Tests with the
   real Claude CLI run only when `ORCHESTRAGENT_RUN_REAL_CLAUDE=1`.
6. Keep changes scoped to the current step; no unrelated refactoring.

## Repository layout

```
build-logic/           convention plugins: oa.java-library, oa.spring-library, oa.spring-app, oa.java-app
gradle/libs.versions.toml   single version catalog; add every dependency version here
backend/shared-kernel  pure Java: ID types, DomainEvent, DomainException, clock abstraction
backend/platform       cross-cutting infrastructure: security, web errors, Flyway runner, events, SSE hub
backend/<context>/     identity, workspace, issue, agent, inbox; each has 4 layer modules (below)
backend/app            Spring Boot main class; wires every context (modular monolith)
daemon/                daemon-protocol, daemon-provider-api, daemon-provider-mock,
                       daemon-provider-claude, daemon-cli
frontend/              Vite + React app (pnpm)
e2e/                   Playwright tests
```

Module build files apply exactly one convention plugin. Do not declare versions in module build files;
use the version catalog (`libs.*`). Versions of Spring-managed libraries come from the Spring Boot and
Spring Modulith BOMs.

## Clean Architecture layers (per bounded context)

| Module             | Contains                                                                                          | May depend on                                                                                     |
| ------------------ | ------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `*-domain`         | Entities, value objects, domain events, repository ports (interfaces). Plain Java: no Spring, no JPA | `shared-kernel`                                                                                   |
| `*-application`    | Use cases (command/query handlers), input DTOs, output ports, facades implementing the contract   | own domain, `shared-kernel`, **contracts of other contexts**. Only `spring-context` and `spring-tx` annotations (`@Service`, `@Transactional`) |
| `*-infrastructure` | `adapter/in/web` (REST controllers), `adapter/out/persistence` (JPA entities, Spring Data repos, mappers), `adapter/out/mail`, event listeners | own application and domain, `platform`                                                            |
| `*-contract`       | Public API of the context: facade interfaces (e.g. `WorkspaceAccess`), DTOs, integration event records (e.g. `IssueAssigneeChanged`) | `shared-kernel`                                                                                   |

## Rules that keep the path to microservices open

1. A context depends **only** on the `*-contract` modules of other contexts. Gradle declares only that
   dependency, and ArchUnit tests in `backend/app` reject wrong imports.
2. Each context owns a **separate Postgres schema** (`identity`, `workspace`, `issue`, `agent`, `inbox`)
   and a separate Flyway location (`db/migration/<context>`) with its own history table.
   No JOINs and no foreign keys across schemas; cross-context references are plain UUIDs.
   Foreign keys inside one schema are fine.
3. Synchronous calls between contexts go through contract facades. When a context is split out, the
   facade implementation is replaced with an HTTP client.
4. Asynchronous communication uses integration events through the Spring Modulith event publication
   registry (`spring-modulith-starter-jpa`, transactional outbox). Listeners use
   `@ApplicationModuleListener` and must be **idempotent**.
5. IDs are UUIDv7 generated in the application (`com.fasterxml.uuid:java-uuid-generator`).
6. Never edit a merged Flyway migration; add a new one.

## API conventions

- REST under `/api`. Workspace resources live under `/api/workspaces/{workspaceId}/...`. Every
  workspace query filters by `workspace_id` and checks membership through `WorkspaceAccess`.
- Errors use RFC 7807 `ProblemDetail`, produced by one `@RestControllerAdvice` in `platform`.
- The frontend parses **every** response with a zod schema; never cast with `as T`.
- Daemon API lives under `/api/daemon/**`, authenticated with API tokens (`oat_...`), never cookies.
  Every daemon endpoint checks that the runtime or task belongs to the token owner. Wire DTOs live in
  `daemon-protocol`.
- Never log login codes, tokens, or `Authorization` headers.

## UI style (Jira-like)

The UI follows the Jira / Atlassian Design System look: palette, density and shapes are specified in
`PLAN.md` ("Giao diện (UI)").

- All colors, radii and shadows are CSS variables (tokens) in `frontend/src/index.css`. Components and pages
  use token classes only (`bg-primary`, `text-muted-foreground`, `bg-selected`...). **No hex colors in
  components or pages.** To change the look, change tokens, not components.
- Base font size is 14px; buttons and inputs are 32px high (40px only on the login form).
- Issue status uses lozenges (`Badge` variants), and agents always use the purple "discovery" tone.
- Do not use Atlassian logos, names, fonts, or icons. Icons come from `lucide-react`.

## Testing

- Backend: JUnit Jupiter, AssertJ, Spring MockMvc, Testcontainers (Postgres), ArchUnit.
  Integration tests need Docker running.
- Frontend: Vitest + Testing Library. E2E: Playwright with the mock provider.
- Do not change an existing test just to make it pass; fix the code, or report the test as wrong.
- Only report something as tested if the command was actually run.

## Commands

```bash
docker compose up -d                 # Postgres + Mailpit
./gradlew build                      # compile + all backend and daemon tests
./gradlew :backend:app:bootRun --args='--spring.profiles.active=dev'
pnpm -C frontend typecheck && pnpm -C frontend lint && pnpm -C frontend test && pnpm -C frontend build
```
