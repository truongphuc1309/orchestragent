import * as React from "react"
import { cn } from "@/shared/lib/utils"

function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        "flex field-sizing-content min-h-16 w-full rounded-md border border-input bg-background px-2 py-1.5 text-sm transition-colors outline-none placeholder:text-muted-foreground hover:bg-muted focus-visible:border-ring focus-visible:bg-background focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:bg-muted disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-1 aria-invalid:ring-destructive",
        className
      )}
      {...props}
    />
  )
}

export { Textarea }
