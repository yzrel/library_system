import type {ReactNode} from "react";

export function Panel({title, action, children}: {title: string; action?: ReactNode; children: ReactNode}) {
  return <section className="panel"><div className="panel-head"><h2>{title}</h2>{action}</div>{children}</section>;
}
