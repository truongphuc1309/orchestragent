import { randomBytes } from 'node:crypto'
import { existsSync } from 'node:fs'
import path from 'node:path'
import {
  BACKEND_JAR,
  BACKEND_PORT,
  BACKEND_URL,
  DAEMON_LIB,
  DATABASE,
  FRONTEND_PORT,
  FRONTEND_URL,
  LOGIN_CODE,
  POSTGRES_CONTAINER,
  ROOT,
  VITE_BIN,
  javaCommand,
  readDotEnv,
} from './env'
import { run, startProcess, stopProcess, waitForUrl, type Started } from './processes'

/**
 * Brings up everything the browser tests need, then returns the function that takes it down:
 *
 * 1. builds the backend jar and the daemon (Gradle skips the work when nothing changed; `E2E_SKIP_BUILD=1`
 *    skips the call),
 * 2. recreates an empty database `orchestragent_e2e` in the Postgres of `docker compose`,
 * 3. starts the backend (dev profile, fixed login code, no seed data) and the Vite dev server in front of it.
 *
 * The daemon is started by the test itself, after the workspace exists: a daemon registers in the workspaces
 * its owner has at that moment.
 */
export default async function globalSetup(): Promise<() => Promise<void>> {
  const dotenv = readDotEnv()
  if (process.env.E2E_SKIP_BUILD !== '1') {
    buildArtifacts()
  }
  for (const required of [BACKEND_JAR, path.join(DAEMON_LIB, 'daemon-cli.jar'), VITE_BIN]) {
    if (!existsSync(required)) {
      throw new Error(
        `${required} is missing. Run "./gradlew :backend:app:bootJar :daemon:daemon-cli:installDist" and ` +
          '"pnpm -C frontend install" first.',
      )
    }
  }

  recreateDatabase(dotenv)

  const started: Started[] = []
  const stop = async () => {
    for (const item of started.reverse()) {
      stopProcess(item)
    }
  }

  try {
    const backend = startProcess(
      'backend',
      javaCommand(),
      [
        '-jar',
        BACKEND_JAR,
        '--spring.profiles.active=dev',
        `--server.port=${BACKEND_PORT}`,
        // The tests create their own data.
        '--orchestragent.dev-seed.enabled=false',
      ],
      {
        cwd: ROOT,
        env: {
          DB_URL: databaseUrl(dotenv),
          // A secret for this run only; the one in .env (if any) is never used or printed.
          JWT_SECRET: randomBytes(32).toString('base64'),
          DEV_LOGIN_CODE: LOGIN_CODE,
        },
      },
    )
    started.push(backend)

    const frontend = startProcess('frontend', process.execPath, [VITE_BIN, '--strictPort'], {
      cwd: path.join(ROOT, 'frontend'),
      env: { FRONTEND_PORT: String(FRONTEND_PORT), API_PROXY_TARGET: BACKEND_URL },
    })
    started.push(frontend)

    await Promise.all([
      waitForUrl(`${BACKEND_URL}/api/health`, backend, 120_000),
      waitForUrl(FRONTEND_URL, frontend, 60_000),
    ])
  } catch (error) {
    await stop()
    throw error
  }

  return stop
}

function buildArtifacts(): void {
  // With shell: true the command line is one string, so a path with spaces needs its own quotes.
  const gradle = `"${path.join(ROOT, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')}"`
  run(gradle, [':backend:app:bootJar', ':daemon:daemon-cli:installDist', '-q'], { cwd: ROOT, shell: true })
}

/** JDBC URL of the .env database with the database name swapped for the end-to-end one. */
function databaseUrl(dotenv: Record<string, string>): string {
  const base = process.env.DB_URL ?? dotenv.DB_URL ?? 'jdbc:postgresql://localhost:5432/orchestragent'
  return base.replace(/\/[^/?]+(\?.*)?$/, `/${DATABASE}$1`)
}

/** Drops and creates the database inside the compose container, so every run starts from nothing. */
function recreateDatabase(dotenv: Record<string, string>): void {
  const user = process.env.DB_USER ?? dotenv.DB_USER
  if (!user) {
    throw new Error('DB_USER is not set. Copy .env.example to .env (the same file "docker compose" uses).')
  }
  try {
    run('docker', [
      'exec',
      POSTGRES_CONTAINER,
      'psql',
      '-U',
      user,
      '-d',
      'postgres',
      '-v',
      'ON_ERROR_STOP=1',
      '-c',
      `DROP DATABASE IF EXISTS ${DATABASE} WITH (FORCE)`,
      '-c',
      `CREATE DATABASE ${DATABASE}`,
    ])
  } catch (error) {
    throw new Error(
      `Could not recreate the database in the container "${POSTGRES_CONTAINER}". Is "docker compose up -d" running?\n` +
        String(error),
    )
  }
}
