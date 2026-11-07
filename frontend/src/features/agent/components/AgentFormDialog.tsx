import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { useMe } from '@/features/auth/hooks'
import type { Workspace } from '@/features/workspace/schemas'
import { messageOf } from '@/shared/api/errors'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'
import { useCreateAgent, useRuntimes, useUpdateAgent } from '../hooks'
import { agentFormSchema, type Agent, type AgentForm, type Runtime } from '../schemas'

interface Props {
  workspace: Workspace
  /** The agent to edit, or null to create a new one. */
  agent: Agent | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function AgentFormDialog({ workspace, agent, open, onOpenChange }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{agent ? 'Edit agent' : 'Create agent'}</DialogTitle>
          <DialogDescription>
            An agent is a teammate that works on issues assigned to it, on one of your computers.
          </DialogDescription>
        </DialogHeader>
        {/* The content unmounts when the dialog closes, so every opening starts from the agent as it is now. */}
        <AgentFormView workspace={workspace} agent={agent} onClose={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  )
}

function runtimeLabel(runtime: Pick<Runtime, 'deviceName' | 'provider' | 'status'>): string {
  return `${runtime.deviceName} · ${runtime.provider}${runtime.status === 'offline' ? ' (offline)' : ''}`
}

function AgentFormView({ workspace, agent, onClose }: { workspace: Workspace; agent: Agent | null; onClose: () => void }) {
  const me = useMe()
  const runtimes = useRuntimes(workspace.id)
  const create = useCreateAgent(workspace.id)
  const update = useUpdateAgent(workspace.id)
  const mutation = agent ? update : create

  // A person can only point an agent at their own computers. When an admin edits somebody else's agent, its
  // current runtime stays selectable so that saving without touching it keeps it.
  const own = (runtimes.data ?? []).filter((runtime) => runtime.ownerId === me.data?.id)
  const options = [...own.map((runtime) => ({ id: runtime.id, label: runtimeLabel(runtime) }))]
  if (agent?.runtime && !options.some((option) => option.id === agent.runtime?.id)) {
    options.unshift({ id: agent.runtime.id, label: runtimeLabel(agent.runtime) })
  }
  const firstOnline = own.find((runtime) => runtime.status === 'online') ?? own[0]

  const form = useForm<AgentForm>({
    resolver: zodResolver(agentFormSchema),
    defaultValues: {
      name: agent?.name ?? '',
      instructions: agent?.instructions ?? '',
      runtimeId: agent?.runtime?.id ?? firstOnline?.id ?? '',
      model: agent?.model ?? '',
    },
  })
  const nameError = form.formState.errors.name
  const instructionsError = form.formState.errors.instructions
  const runtimeError = form.formState.errors.runtimeId
  const noRuntime = runtimes.isSuccess && options.length === 0

  const submit = form.handleSubmit((values) => {
    const done = (saved: Agent) => {
      toast.success(`${saved.name} was ${agent ? 'saved' : 'created'}`)
      onClose()
    }
    if (agent) {
      update.mutate({ agentId: agent.id, form: values }, { onSuccess: done })
    } else {
      create.mutate(values, { onSuccess: done })
    }
  })

  // The runtime list may arrive after the form opened: choose a default then, if nothing is chosen yet.
  const defaultRuntimeId = firstOnline?.id
  useEffect(() => {
    if (!agent && defaultRuntimeId !== undefined && form.getValues('runtimeId') === '') {
      form.setValue('runtimeId', defaultRuntimeId)
    }
  }, [agent, defaultRuntimeId, form])

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="agent-name">Name</Label>
        <Input id="agent-name" autoComplete="off" autoFocus aria-invalid={nameError !== undefined} {...form.register('name')} />
        {nameError && (
          <p role="alert" className="text-sm text-destructive">
            {nameError.message}
          </p>
        )}
      </div>

      <div className="grid grid-cols-2 gap-3">
        <Controller
          control={form.control}
          name="runtimeId"
          render={({ field }) => (
            <div className="flex flex-col gap-1.5">
              <Label>Runs on</Label>
              <Select value={field.value === '' ? undefined : field.value} onValueChange={field.onChange}>
                <SelectTrigger aria-label="Runtime" aria-invalid={runtimeError !== undefined} className="w-full">
                  <SelectValue placeholder="Choose a runtime" />
                </SelectTrigger>
                <SelectContent>
                  {options.map((option) => (
                    <SelectItem key={option.id} value={option.id}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {runtimeError && (
                <p role="alert" className="text-sm text-destructive">
                  {runtimeError.message}
                </p>
              )}
            </div>
          )}
        />
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="agent-model">Model (optional)</Label>
          <Input id="agent-model" autoComplete="off" placeholder="Tool default" {...form.register('model')} />
        </div>
      </div>
      {noRuntime && (
        <p role="status" className="text-sm text-muted-foreground">
          You have no runtime yet. <Link to={`/w/${workspace.slug}/runtimes`}>Connect a computer</Link> first; an agent runs
          on one of your own.
        </p>
      )}

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="agent-instructions">Instructions</Label>
        <Textarea
          id="agent-instructions"
          rows={8}
          aria-invalid={instructionsError !== undefined}
          placeholder="Markdown. Tell the agent how you want it to work."
          {...form.register('instructions')}
        />
        {instructionsError && (
          <p role="alert" className="text-sm text-destructive">
            {instructionsError.message}
          </p>
        )}
      </div>

      {mutation.error && (
        <p role="alert" className="text-sm text-destructive">
          {messageOf(mutation.error, 'Could not save the agent. Please try again.')}
        </p>
      )}
      <DialogFooter>
        <Button type="button" variant="ghost" onClick={onClose}>
          Cancel
        </Button>
        <Button type="submit" disabled={mutation.isPending || noRuntime}>
          {mutation.isPending ? 'Saving…' : agent ? 'Save' : 'Create'}
        </Button>
      </DialogFooter>
    </form>
  )
}
