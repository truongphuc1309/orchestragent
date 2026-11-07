import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { FakeEventSource } from '@/test/fakeEventSource'
import { connectEventStream, HEARTBEAT_EVENT } from './eventStream'

const URL = '/api/workspaces/w1/events'

function connect(overrides: Partial<Parameters<typeof connectEventStream>[0]> = {}) {
  const onEvent = vi.fn()
  const onResync = vi.fn()
  const stop = connectEventStream({
    url: URL,
    eventNames: ['issue.created', 'comment.created'],
    onEvent,
    onResync,
    createSource: (url) => new FakeEventSource(url),
    ...overrides,
  })
  return { onEvent, onResync, stop }
}

describe('connectEventStream', () => {
  beforeEach(() => {
    FakeEventSource.reset()
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('connects to the URL right away', () => {
    connect()

    expect(FakeEventSource.instances).toHaveLength(1)
    expect(FakeEventSource.instances[0]?.url).toBe(URL)
  })

  it('hands over the named events with their data parsed', () => {
    const { onEvent } = connect()
    const source = FakeEventSource.instances[0]
    source?.open()

    source?.emit('issue.created', { issueId: 'a', number: 3 })
    source?.emit('comment.created', { issueId: 'a', commentId: 'c' })

    expect(onEvent).toHaveBeenNthCalledWith(1, 'issue.created', { issueId: 'a', number: 3 })
    expect(onEvent).toHaveBeenNthCalledWith(2, 'comment.created', { issueId: 'a', commentId: 'c' })
  })

  it('drops an event whose data is not JSON and keeps going', () => {
    const { onEvent } = connect()
    const source = FakeEventSource.instances[0]

    source?.emit('issue.created', 'not json {')
    source?.emit('issue.created', { issueId: 'ok' })

    expect(onEvent).toHaveBeenCalledTimes(1)
    expect(onEvent).toHaveBeenCalledWith('issue.created', { issueId: 'ok' })
  })

  it('does not ask for fresh data on the first connection', () => {
    const { onResync } = connect()

    FakeEventSource.instances[0]?.open()

    expect(onResync).not.toHaveBeenCalled()
  })

  it('leaves reconnecting to the browser while it is still trying', () => {
    const { onResync } = connect()
    const source = FakeEventSource.instances[0]
    source?.open()

    source?.fail(false)
    vi.advanceTimersByTime(60_000)

    expect(FakeEventSource.instances).toHaveLength(1)
    // When the browser gets the connection back, events may have been missed.
    source?.open()
    expect(onResync).toHaveBeenCalledTimes(1)
  })

  it('reconnects itself when the browser gives up, and then asks for fresh data', () => {
    const { onResync } = connect()
    FakeEventSource.instances[0]?.open()

    FakeEventSource.instances[0]?.fail(true)
    expect(FakeEventSource.instances[0]?.closed).toBe(true)
    expect(FakeEventSource.instances).toHaveLength(1)
    vi.advanceTimersByTime(1000)

    expect(FakeEventSource.instances).toHaveLength(2)
    FakeEventSource.instances[1]?.open()
    expect(onResync).toHaveBeenCalledTimes(1)
  })

  it('waits longer after every failed attempt, up to a limit, and starts over once connected', () => {
    connect({ initialDelayMs: 1000, maxDelayMs: 4000 })
    FakeEventSource.instances[0]?.fail(true)

    vi.advanceTimersByTime(999)
    expect(FakeEventSource.instances).toHaveLength(1)
    vi.advanceTimersByTime(1)
    expect(FakeEventSource.instances).toHaveLength(2)

    FakeEventSource.instances[1]?.fail(true)
    vi.advanceTimersByTime(1999)
    expect(FakeEventSource.instances).toHaveLength(2)
    vi.advanceTimersByTime(1)
    expect(FakeEventSource.instances).toHaveLength(3)

    FakeEventSource.instances[2]?.fail(true)
    vi.advanceTimersByTime(4000)
    expect(FakeEventSource.instances).toHaveLength(4)

    FakeEventSource.instances[3]?.fail(true)
    vi.advanceTimersByTime(3999)
    expect(FakeEventSource.instances).toHaveLength(4)
    vi.advanceTimersByTime(1)
    expect(FakeEventSource.instances).toHaveLength(5)

    // A successful connection resets the delay to the start.
    FakeEventSource.instances[4]?.open()
    FakeEventSource.instances[4]?.fail(true)
    vi.advanceTimersByTime(1000)
    expect(FakeEventSource.instances).toHaveLength(6)
  })

  it('stops for good: closes the connection and never reconnects', () => {
    const { stop, onEvent } = connect()
    const source = FakeEventSource.instances[0]
    source?.open()

    source?.fail(true)
    stop()
    vi.advanceTimersByTime(120_000)

    expect(FakeEventSource.instances).toHaveLength(1)
    expect(source?.closed).toBe(true)
    expect(onEvent).not.toHaveBeenCalled()
  })

  describe('a connection that died without telling the browser', () => {
    it('is replaced after a long silence, and fresh data is asked for once the new one is open', () => {
      const { onResync } = connect({ idleTimeoutMs: 60_000 })
      const first = FakeEventSource.instances[0]
      first?.open()

      vi.advanceTimersByTime(59_999)
      expect(first?.closed).toBe(false)
      vi.advanceTimersByTime(1)
      expect(first?.closed).toBe(true)
      expect(FakeEventSource.instances).toHaveLength(1)

      vi.advanceTimersByTime(1000)
      expect(FakeEventSource.instances).toHaveLength(2)
      FakeEventSource.instances[1]?.open()
      expect(onResync).toHaveBeenCalledTimes(1)
    })

    it('is kept by heartbeats, which are not passed on', () => {
      const { onEvent } = connect({ idleTimeoutMs: 60_000 })
      const source = FakeEventSource.instances[0]
      source?.open()

      for (let i = 0; i < 10; i++) {
        vi.advanceTimersByTime(25_000)
        source?.emit(HEARTBEAT_EVENT, {})
      }

      expect(source?.closed).toBe(false)
      expect(FakeEventSource.instances).toHaveLength(1)
      expect(onEvent).not.toHaveBeenCalled()
    })

    it('is kept by real events too', () => {
      connect({ idleTimeoutMs: 60_000 })
      const source = FakeEventSource.instances[0]
      source?.open()

      vi.advanceTimersByTime(50_000)
      source?.emit('issue.created', { issueId: 'a' })
      vi.advanceTimersByTime(50_000)

      expect(source?.closed).toBe(false)
    })

    it('is not watched any more after stopping', () => {
      const { stop } = connect({ idleTimeoutMs: 60_000 })
      FakeEventSource.instances[0]?.open()

      stop()
      vi.advanceTimersByTime(600_000)

      expect(FakeEventSource.instances).toHaveLength(1)
    })
  })

  it('closes an open connection when stopped', () => {
    const { stop } = connect()
    const source = FakeEventSource.instances[0]
    source?.open()

    stop()

    expect(source?.closed).toBe(true)
  })
})
