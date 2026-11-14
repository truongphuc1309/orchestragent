import { IssuesView } from '../components/IssuesView'

/** The issues of every project of the workspace, as a board or a list. */
export function IssuesPage() {
  return (
    // As tall as the page area, so the board below the filters fills what is left of the screen.
    <div className="flex h-full flex-col">
      <IssuesView heading={<h1 className="text-2xl leading-7 font-medium">My Issues</h1>} />
    </div>
  )
}
