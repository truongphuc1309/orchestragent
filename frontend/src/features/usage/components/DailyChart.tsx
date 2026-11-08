import { formatCost, formatTokens } from '@/shared/lib/format'
import { cn } from '@/shared/lib/utils'
import { METRIC_LABEL, formatDay, formatMetric, metricValue, type Metric } from '../metrics'
import type { Usage } from '../schemas'

/** One line of what a day holds, for the tooltip and for screen readers. */
function describe(day: Usage['byDay'][number]): string {
  const t = day.totals
  return `${formatDay(day.day)}: ${formatMetric(t.runs, 'runs')}, ${formatTokens(t.inputTokens + t.outputTokens)} tokens, ${formatCost(t.costUsd)}`
}

/**
 * One measure per day of the period as thin bars on one axis (never two measures on two scales: the metric
 * switch above picks which). Bars grow from the baseline with rounded tops; hovering or focusing a day shows all
 * its numbers. A table with the same numbers follows the chart.
 */
export function DailyChart({ days, metric }: { days: Usage['byDay']; metric: Metric }) {
  const values = days.map((day) => metricValue(day.totals, metric))
  const max = Math.max(0, ...values)
  const first = days[0]
  const middle = days[Math.floor(days.length / 2)]
  const last = days[days.length - 1]

  return (
    <figure className="m-0 flex flex-col gap-2">
      <figcaption className="text-sm font-medium">{METRIC_LABEL[metric]} per day (UTC)</figcaption>
      <div className="flex gap-2">
        <div aria-hidden="true" className="flex w-14 flex-col justify-between text-right text-xs text-muted-foreground">
          <span>{formatMetric(max, metric).replace(/ (runs?|tokens)$/, '')}</span>
          <span>0</span>
        </div>
        <ol
          aria-label={`${METRIC_LABEL[metric]} per day`}
          className="m-0 flex h-40 flex-1 list-none items-stretch gap-0.5 border-b p-0"
        >
          {days.map((day, index) => {
            const value = values[index] ?? 0
            const height = max === 0 ? 0 : (value / max) * 100
            return (
              <li key={day.day} className="group relative flex flex-1 items-end">
                {/* The whole column is the target: easier to hit than a short bar. */}
                <button
                  type="button"
                  aria-label={describe(day)}
                  className="flex h-full w-full cursor-default items-end rounded-sm bg-transparent p-0 hover:bg-accent focus-visible:bg-accent focus-visible:outline-none"
                >
                  <span
                    data-testid="bar"
                    className={cn('w-full rounded-t bg-primary', value > 0 && 'min-h-0.5')}
                    style={{ height: `${height}%` }}
                  />
                </button>
                <span
                  role="tooltip"
                  className={cn(
                    'pointer-events-none absolute bottom-full z-10 mb-1 hidden rounded-md border bg-popover px-2 py-1 text-xs whitespace-nowrap text-popover-foreground shadow-md group-focus-within:block group-hover:block',
                    // Kept inside the chart: days near an edge open their tooltip towards the middle.
                    index < days.length / 3 ? 'left-0' : index >= (days.length * 2) / 3 ? 'right-0' : 'left-1/2 -translate-x-1/2',
                  )}
                >
                  {describe(day)}
                </span>
              </li>
            )
          })}
        </ol>
      </div>
      {first !== undefined && last !== undefined && (
        <div aria-hidden="true" className="ml-16 flex justify-between text-xs text-muted-foreground">
          <span>{formatDay(first.day)}</span>
          {middle !== undefined && days.length > 2 && <span>{formatDay(middle.day)}</span>}
          <span>{formatDay(last.day)}</span>
        </div>
      )}
      <details className="text-sm">
        <summary className="cursor-pointer text-muted-foreground">Show as a table</summary>
        <div className="relative mt-2 max-h-64 overflow-auto rounded-md border">
          <table className="w-full text-left text-sm">
            <thead className="border-b bg-muted text-xs text-muted-foreground uppercase">
              <tr>
                <th scope="col" className="px-3 py-2 font-semibold">
                  Day
                </th>
                <th scope="col" className="px-3 py-2 text-right font-semibold">
                  Runs
                </th>
                <th scope="col" className="px-3 py-2 text-right font-semibold">
                  Tokens
                </th>
                <th scope="col" className="px-3 py-2 text-right font-semibold">
                  Cost
                </th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {days.map((day) => (
                <tr key={day.day}>
                  <td className="px-3 py-1.5">{formatDay(day.day)}</td>
                  <td className="px-3 py-1.5 text-right tabular-nums">{day.totals.runs}</td>
                  <td className="px-3 py-1.5 text-right tabular-nums">
                    {formatTokens(day.totals.inputTokens + day.totals.outputTokens)}
                  </td>
                  <td className="px-3 py-1.5 text-right tabular-nums">{formatCost(day.totals.costUsd)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  )
}
