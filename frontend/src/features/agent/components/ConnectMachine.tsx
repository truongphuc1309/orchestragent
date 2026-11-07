import { Link } from 'react-router'

/**
 * How to make a computer show up in the list above. The daemon itself comes in a later release of the
 * project; the steps already say what it will need, so people can create their token ahead of time.
 */
export function ConnectMachine({ workspaceSlug }: { workspaceSlug: string }) {
  const server = window.location.origin
  return (
    <section aria-labelledby="connect-heading" className="flex flex-col gap-3 rounded-md border bg-muted p-4">
      <h2 id="connect-heading" className="text-base font-semibold">
        Connect a computer
      </h2>
      <ol className="m-0 flex list-decimal flex-col gap-2 pl-5">
        <li>
          Create an API token on the{' '}
          <Link to={`/w/${workspaceSlug}/settings/tokens`}>API tokens page</Link>. It is shown only once, so copy it.
        </li>
        <li>
          Install the Orchestragent daemon on the computer that should run agents. The daemon is not released yet; the
          other steps will work as soon as it is.
        </li>
        <li>
          Sign in and start it. It registers the AI tools it finds (such as Claude Code), and they appear in the list
          above within a few seconds:
          <pre className="mt-2 mb-0 overflow-x-auto rounded-md border bg-background p-3 text-xs">
            {`login --server ${server} --token <your token>\nstart`}
          </pre>
        </li>
      </ol>
      <p className="m-0 text-xs text-muted-foreground">
        A computer shows as offline when it has not sent a heartbeat for 90 seconds. Agents that use it cannot do any
        work until it is back.
      </p>
    </section>
  )
}
