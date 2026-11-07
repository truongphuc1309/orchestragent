import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'

/** Repository root. */
export const ROOT = path.resolve(import.meta.dirname, '../..')

/**
 * Own ports and an own database, so the end-to-end run never touches a backend, a Vite server or data that a
 * developer has open.
 */
export const BACKEND_PORT = 18080
export const FRONTEND_PORT = 15173
export const BACKEND_URL = `http://localhost:${BACKEND_PORT}`
export const FRONTEND_URL = `http://localhost:${FRONTEND_PORT}`
export const DATABASE = 'orchestragent_e2e'

/** The fixed login code of the dev profile, for this run only. */
export const LOGIN_CODE = '123456'

export const POSTGRES_CONTAINER = process.env.E2E_POSTGRES_CONTAINER ?? 'orchestragent-postgres'

export const BACKEND_JAR = path.join(ROOT, 'backend/app/build/libs/app.jar')
export const DAEMON_LIB = path.join(ROOT, 'daemon/daemon-cli/build/install/orchestragent-daemon/lib')
export const DAEMON_MAIN = 'com.orchestragent.daemon.cli.OrchestragentDaemon'
export const VITE_BIN = path.join(ROOT, 'frontend/node_modules/vite/bin/vite.js')

/** Where logs of the processes started by the tests go (gitignored with the other test output). */
export const LOG_DIR = path.join(ROOT, 'e2e/test-results/logs')

/** Reads the repository's `.env` (KEY=value lines) so the tests use the same database login as `docker compose`. */
export function readDotEnv(): Record<string, string> {
  const file = path.join(ROOT, '.env')
  if (!existsSync(file)) {
    return {}
  }
  const values: Record<string, string> = {}
  for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const match = /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/.exec(line)
    if (match?.[1] !== undefined && match[2] !== undefined) {
      values[match[1]] = match[2]
    }
  }
  return values
}

/** The `java` to run: JAVA_HOME when set, else whatever is on the PATH. */
export function javaCommand(): string {
  const home = process.env.JAVA_HOME
  return home ? path.join(home, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java'
}
