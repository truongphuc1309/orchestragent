import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/shared/lib/utils"
import { Slot } from "radix-ui"

/*
 * Jira "lozenge": a small UPPERCASE label with a soft background and darker text of the same tone.
 * Status mapping (PLAN.md): backlog/todo/cancelled -> neutral, in_progress -> info, in_review -> discovery,
 * done -> success, blocked -> danger. Agents use discovery (purple).
 */
const badgeVariants = cva(
  "group/badge inline-flex h-4 w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-md border border-transparent px-1 text-[11px] leading-none font-bold tracking-wide whitespace-nowrap uppercase transition-colors focus-visible:ring-2 focus-visible:ring-ring [&>svg]:pointer-events-none [&>svg]:size-3",
  {
    variants: {
      variant: {
        default: "bg-lozenge-neutral text-lozenge-neutral-foreground",
        neutral: "bg-lozenge-neutral text-lozenge-neutral-foreground",
        info: "bg-lozenge-info text-lozenge-info-foreground",
        success: "bg-lozenge-success text-lozenge-success-foreground",
        warning: "bg-lozenge-warning text-lozenge-warning-foreground",
        danger: "bg-lozenge-danger text-lozenge-danger-foreground",
        discovery: "bg-lozenge-discovery text-lozenge-discovery-foreground",
        outline: "border-border text-foreground",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

function Badge({
  className,
  variant = "default",
  asChild = false,
  ...props
}: React.ComponentProps<"span"> &
  VariantProps<typeof badgeVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot.Root : "span"

  return (
    <Comp
      data-slot="badge"
      data-variant={variant}
      className={cn(badgeVariants({ variant }), className)}
      {...props}
    />
  )
}

export { Badge, badgeVariants }
