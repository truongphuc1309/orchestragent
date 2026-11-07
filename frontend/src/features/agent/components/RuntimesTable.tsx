import { formatAgo, formatDateTime } from '@/shared/lib/format'
import type { Runtime } from '../schemas'
import { RuntimeStatusLozenge } from './RuntimeStatusLozenge'

/** The computers (and AI tools on them) that can run agents, with whether they are reachable right now. */
export function RuntimesTable({ runtimes, myId }: { runtimes: Runtime[]; myId: string | undefined }) {
  return (
    <div className="overflow-x-auto rounded-md border">
      <table className="w-full text-left text-sm">
        <thead className="border-b bg-muted text-xs text-muted-foreground uppercase">
          <tr>
            <th scope="col" className="px-3 py-2 font-semibold">
              Device
            </th>
            <th scope="col" className="px-3 py-2 font-semibold">
              Provider
            </th>
            <th scope="col" className="px-3 py-2 font-semibold">
              Owner
            </th>
            <th scope="col" className="px-3 py-2 font-semibold">
              Status
            </th>
            <th scope="col" className="px-3 py-2 font-semibold">
              Last heartbeat
            </th>
          </tr>
        </thead>
        <tbody className="divide-y">
          {runtimes.map((runtime) => (
            <tr key={runtime.id}>
              <td className="px-3 py-2 font-medium">{runtime.deviceName}</td>
              <td className="px-3 py-2">
                {runtime.provider}
                {runtime.providerVersion !== '' && (
                  <span className="ml-1 text-muted-foreground">{runtime.providerVersion}</span>
                )}
              </td>
              <td className="px-3 py-2">
                {runtime.ownerName}
                {runtime.ownerId === myId && <span className="ml-1 text-muted-foreground">(you)</span>}
              </td>
              <td className="px-3 py-2">
                <RuntimeStatusLozenge status={runtime.status} />
              </td>
              <td className="px-3 py-2 whitespace-nowrap text-muted-foreground">
                <time dateTime={runtime.lastHeartbeatAt} title={formatDateTime(runtime.lastHeartbeatAt)}>
                  {formatAgo(runtime.lastHeartbeatAt)}
                </time>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
