import type { ReactNode } from 'react'
import { Logo } from './Logo'

/** The 56px top navigation bar: product logo on the left, page actions (such as the user menu) on the right. */
export function TopBar({ actions }: { actions?: ReactNode }) {
  return (
    <header className="sticky top-0 z-40 flex h-14 items-center justify-between border-b bg-background px-4">
      <Logo />
      <div className="flex items-center gap-2">{actions}</div>
    </header>
  )
}
