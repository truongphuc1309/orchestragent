import { useState } from 'react'
import { Link } from 'react-router'
import { useWorkspace } from '@/features/workspace/workspaceContext'
import { formatCost, formatTokens } from '@/shared/lib/format'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { DailyChart } from '../components/DailyChart'
import { METRIC_LABEL, formatDay, runsNote, type Metric } from '../metrics'
import { useUsage } from '../hooks'
import { PERIODS, type Period, type Totals, type Usage } from '../schemas'

const METRICS: Metric[] = ['cost', 'runs', 'tokens']

function isMetric(value: string): value is Metric {
  return METRICS.some((metric) => metric === value)
}

function StatTile({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="flex flex-col gap-1 rounded-md border p-4">
      <span className="text-xs font-semibold text-muted-foreground uppercase">{label}</span>
      <span className="text-2xl leading-7 font-medium tabular-nums">{value}</span>
      {note !== undefined && <span className="text-xs text-muted-foreground">{note}</span>}
    </div>
  )
}

function Header({ children }: { children: string }) {
  return (
    <th scope="col" className="px-3 py-2 font-semibold">
      {children}
    </th>
  )
}

function NumberHeader({ children }: { children: string }) {
  return (
    <th scope="col" className="px-3 py-2 text-right font-semibold">
      {children}
    </th>
  )
}

function TotalsCells({ totals }: { totals: Totals }) {
  return (
    <>
      <td className="px-3 py-2 text-right tabular-nums">{totals.runs}</td>
      <td className="px-3 py-2 text-right tabular-nums">{totals.failed}</td>
      <td className="px-3 py-2 text-right tabular-nums">{formatTokens(totals.inputTokens)}</td>
      <td className="px-3 py-2 text-right tabular-nums">{formatTokens(totals.outputTokens)}</td>
      <td className="px-3 py-2 text-right tabular-nums">{formatCost(totals.costUsd)}</td>
    </>
  )
}

function TotalsHeaders() {
  return (
    <>
      <NumberHeader>Runs</NumberHeader>
      <NumberHeader>Failed</NumberHeader>
      <NumberHeader>Input tokens</NumberHeader>
      <NumberHeader>Output tokens</NumberHeader>
      <NumberHeader>Cost</NumberHeader>
    </>
  )
}

function UsageReport({ usage, metric, onMetric }: { usage: Usage; metric: Metric; onMetric: (metric: Metric) => void }) {
  const workspace = useWorkspace()
  const { totals } = usage

  return (
    <>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatTile label="Runs" value={String(totals.runs)} note={runsNote(totals)} />
        <StatTile label="Input tokens" value={formatTokens(totals.inputTokens)} note="including the prompt cache" />
        <StatTile label="Output tokens" value={formatTokens(totals.outputTokens)} />
        <StatTile label="Cost" value={formatCost(totals.costUsd)} note="as the AI tools report it" />
      </div>

      <section className="flex flex-col gap-3 rounded-md border p-4">
        <div className="flex items-center justify-between gap-4">
          <h2 className="m-0 text-base font-semibold">Per day</h2>
          <Tabs value={metric} onValueChange={(value) => isMetric(value) && onMetric(value)}>
            <TabsList variant="line" aria-label="Measure">
              {METRICS.map((m) => (
                <TabsTrigger key={m} value={m}>
                  {METRIC_LABEL[m]}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        </div>
        <DailyChart days={usage.byDay} metric={metric} />
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="m-0 text-base font-semibold">Agents</h2>
        {usage.byAgent.length === 0 ? (
          <p className="text-sm text-muted-foreground">No agent ran in this period.</p>
        ) : (
          <div className="relative overflow-x-auto rounded-md border">
            <table className="w-full text-left text-sm" aria-label="Usage per agent">
              <thead className="border-b bg-muted text-xs text-muted-foreground uppercase">
                <tr>
                  <Header>Agent</Header>
                  <TotalsHeaders />
                </tr>
              </thead>
              <tbody className="divide-y">
                {usage.byAgent.map((agent) => (
                  <tr key={agent.agentId}>
                    <td className="px-3 py-2">
                      <span className="flex items-center gap-2">
                        {agent.agentName}
                        {agent.archived && <Badge variant="neutral">Archived</Badge>}
                      </span>
                    </td>
                    <TotalsCells totals={agent.totals} />
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="m-0 text-base font-semibold">Most expensive issues</h2>
        {usage.topIssues.length === 0 ? (
          <p className="text-sm text-muted-foreground">No issue had a run in this period.</p>
        ) : (
          <div className="relative overflow-x-auto rounded-md border">
            <table className="w-full text-left text-sm" aria-label="Usage per issue">
              <thead className="border-b bg-muted text-xs text-muted-foreground uppercase">
                <tr>
                  <Header>Issue</Header>
                  <TotalsHeaders />
                </tr>
              </thead>
              <tbody className="divide-y">
                {usage.topIssues.map((issue) => (
                  <tr key={issue.issueId}>
                    <td className="px-3 py-2">
                      {issue.key === null ? (
                        <span className="text-muted-foreground">Deleted issue</span>
                      ) : (
                        <Link to={`/w/${workspace.slug}/issues/${issue.key}`}>
                          <span className="text-muted-foreground">{issue.key}</span>{' '}
                          {issue.title}
                        </Link>
                      )}
                    </td>
                    <TotalsCells totals={issue.totals} />
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  )
}

/** What the agents of the workspace cost: in total, per day, per agent and per issue, over a chosen period. */
export function UsagePage() {
  const workspace = useWorkspace()
  const [period, setPeriod] = useState<Period>(30)
  const [metric, setMetric] = useState<Metric>('cost')
  const usage = useUsage(workspace.id, period)
  const choosePeriod = (value: string) => {
    const chosen = PERIODS.find((days) => String(days) === value)
    if (chosen !== undefined) {
      setPeriod(chosen)
    }
  }

  return (
    <div className="flex max-w-5xl flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl leading-7 font-medium">Usage</h1>
          <p className="text-muted-foreground">
            Runs of agents in {workspace.name} that ended
            {usage.data === undefined
              ? ' in the period'
              : ` from ${formatDay(usage.data.firstDay)} to ${formatDay(usage.data.lastDay)}`}
            .
          </p>
        </div>
        <Tabs value={String(period)} onValueChange={(value) => choosePeriod(value)}>
          <TabsList aria-label="Period">
            {PERIODS.map((days) => (
              <TabsTrigger key={days} value={String(days)}>
                {days} days
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
      </div>

      {usage.isPending ? (
        <div className="flex flex-col gap-2" aria-busy="true">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-48 w-full" />
          <span className="sr-only">Loading usage…</span>
        </div>
      ) : usage.isError ? (
        <div role="alert" className="flex items-center gap-3 text-sm text-destructive">
          Could not load the usage.
          <Button variant="secondary" size="sm" onClick={() => void usage.refetch()}>
            Try again
          </Button>
        </div>
      ) : (
        <UsageReport usage={usage.data} metric={metric} onMetric={setMetric} />
      )}
    </div>
  )
}
