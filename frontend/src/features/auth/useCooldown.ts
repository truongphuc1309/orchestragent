import { useCallback, useEffect, useState } from 'react'

/** A countdown in whole seconds, used to disable "resend" until the server would accept a new request. */
export function useCooldown() {
  const [remaining, setRemaining] = useState(0)

  useEffect(() => {
    if (remaining <= 0) {
      return
    }
    const timer = setTimeout(() => setRemaining((seconds) => seconds - 1), 1000)
    return () => clearTimeout(timer)
  }, [remaining])

  const start = useCallback((seconds: number) => setRemaining(Math.max(0, Math.ceil(seconds))), [])

  return { remaining, start }
}
