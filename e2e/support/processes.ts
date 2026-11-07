import { spawn, spawnSync, type ChildProcess } from 'node:child_process'
import { closeSync, mkdirSync, openSync } from 'node:fs'
import path from 'node:path'
import { LOG_DIR } from './env'

export interface Started {
  name: string
  child: ChildProcess
  logFile: string
}

/** Starts a program with its output going to a log file (a pipe nobody reads would eventually block it). */
export function startProcess(
  name: string,
  command: string,
  args: string[],
  options: { cwd?: string; env?: NodeJS.ProcessEnv } = {},
): Started {
  mkdirSync(LOG_DIR, { recursive: true })
  const logFile = path.join(LOG_DIR, `${name}.log`)
  const log = openSync(logFile, 'w')
  const child = spawn(command, args, {
    cwd: options.cwd,
    env: { ...process.env, ...options.env },
    stdio: ['ignore', log, log],
    // Own process group on POSIX, so the whole tree can be stopped; Windows uses taskkill /T below.
    detached: process.platform !== 'win32',
    windowsHide: true,
  })
  closeSync(log)
  child.on('error', (error) => {
    console.error(`[e2e] could not start ${name}: ${error.message}`)
  })
  return { name, child, logFile }
}

/** Stops the process and everything it started. */
export function stopProcess(started: Started | undefined): void {
  const pid = started?.child.pid
  if (pid === undefined || started?.child.exitCode !== null) {
    return
  }
  if (process.platform === 'win32') {
    spawnSync('taskkill', ['/pid', String(pid), '/T', '/F'], { stdio: 'ignore' })
  } else {
    try {
      process.kill(-pid, 'SIGTERM')
    } catch {
      // Already gone.
    }
  }
}

/** Runs a program to the end and returns its output; throws with the output when it fails. */
export function run(command: string, args: string[], options: { cwd?: string; env?: NodeJS.ProcessEnv; shell?: boolean } = {}): string {
  const result = spawnSync(command, args, {
    cwd: options.cwd,
    env: { ...process.env, ...options.env },
    encoding: 'utf8',
    windowsHide: true,
    shell: options.shell,
  })
  if (result.error) {
    throw new Error(`${command} could not run: ${result.error.message}`)
  }
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(' ')} failed (exit ${result.status}):\n${result.stdout}\n${result.stderr}`)
  }
  return result.stdout
}

/** Waits until the URL answers with a 2xx, or fails after the timeout (naming the log to read). */
export async function waitForUrl(url: string, started: Started, timeoutMs: number): Promise<void> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (started.child.exitCode !== null) {
      throw new Error(`${started.name} stopped with exit code ${started.child.exitCode}; see ${started.logFile}`)
    }
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(2_000) })
      if (response.ok) {
        return
      }
    } catch {
      // Not up yet.
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error(`${started.name} did not answer ${url} within ${timeoutMs / 1000}s; see ${started.logFile}`)
}
