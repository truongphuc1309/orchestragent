import { cn } from '@/shared/lib/utils'

/** The product mark: a blue rounded square with a ring and a dot (an orchestra of agents around a task). */
export function LogoMark({ className }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn('inline-flex size-8 items-center justify-center rounded-md bg-primary text-primary-foreground', className)}
    >
      <svg viewBox="0 0 32 32" className="size-6" fill="none">
        <circle cx="16" cy="17" r="6" stroke="currentColor" strokeWidth="2.5" />
        <circle cx="16" cy="6.5" r="2.5" fill="currentColor" />
      </svg>
    </span>
  )
}

/** Mark plus wordmark. */
export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <LogoMark />
      <span className="text-base font-semibold tracking-tight text-foreground">Orchestragent</span>
    </span>
  )
}
