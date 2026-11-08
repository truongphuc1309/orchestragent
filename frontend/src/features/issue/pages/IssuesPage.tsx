import { IssuesView } from '../components/IssuesView'

/** The issues of every project of the workspace, as a board or a list. */
export function IssuesPage() {
  return <IssuesView heading={<h1 className="text-2xl leading-7 font-medium">Issues</h1>} />
}
