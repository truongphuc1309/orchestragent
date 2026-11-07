import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { BACKEND_URL, DAEMON_LIB, DAEMON_MAIN, javaCommand } from './env'
import { run, startProcess, stopProcess, type Started } from './processes'

export interface RunningDaemon {
  stop: () => void
}

/**
 * Logs the daemon in with an API token and starts it with the mock provider, like a person would with
 * `orchestragent-daemon login` and `start --providers mock`. Its home folder is a throwaway one, so the
 * developer's own `~/.orchestragent` is never touched.
 */
export function startMockDaemon(token: string): RunningDaemon {
  const home = mkdtempSync(path.join(tmpdir(), 'orchestragent-e2e-'))
  const env = {
    ORCHESTRAGENT_HOME: home,
    // Short pauses between the steps of a mock run, so the test does not wait for nothing.
    ORCHESTRAGENT_MOCK_STEP_MS: '200',
  }
  const java = [javaCommand(), ['-cp', path.join(DAEMON_LIB, '*'), DAEMON_MAIN]] as const

  run(java[0], [...java[1], 'login', '--server', BACKEND_URL, '--token', token], { env })
  const daemon: Started = startProcess(
    'daemon',
    java[0],
    [...java[1], 'start', '--providers', 'mock', '--device-name', 'e2e-computer'],
    { env },
  )

  return {
    stop: () => {
      stopProcess(daemon)
      rmSync(home, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 })
    },
  }
}
