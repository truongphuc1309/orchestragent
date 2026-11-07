import { useEffect, useState } from 'react'

/**
 * The current time in milliseconds, refreshed every second while {@code ticking} is true (for a clock that
 * counts up while something runs). When it is false the time is read once and stays, so finished things do not
 * cause a render every second.
 */
export function useNow(ticking: boolean): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!ticking) {
      return
    }
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [ticking])
  return now
}
