import {useEffect, useState} from "react"; import {BarChart3, BookCopy, BookOpen, History, RotateCcw, Sparkles, Users} from "lucide-react"; import {toast} from "sonner";
import {api} from "@/app/api"; import type {GenericRow} from "@/app/types"; import {Empty} from "@/app/components/ui/Empty"; import {Panel} from "@/app/components/ui/Panel";

export function Dashboard() {
  const [data,setData]=useState<GenericRow|null>(null);
  useEffect(()=>{api<GenericRow>("/admin/reports/dashboard").then(setData).catch(e=>toast.error(e.message))},[]);
  const stats=[["Titles","titles",BookOpen],["Physical copies","copies",BookCopy],["Active loans","activeLoans",RotateCcw],["Overdue","overdue",History],["Borrowers","borrowers",Users],["Total circulations","totalCirculations",BarChart3]] as const;
  return <div className="page-stack"><div className="welcome library-welcome"><div><span className="eyebrow"><Sparkles size={14}/> Library overview</span><h1>Good day, Librarian!</h1><p>Here is what is happening in your library today.</p><div className="date-chip">{new Date().toLocaleDateString("en-PH",{weekday:"long",month:"long",day:"numeric"})}</div></div><img src="/images/vfgnhs-library-hero.jpg?v=navy" alt="Students reading together in the school library"/></div>
    <div className="stats">{stats.map(([label,key,Icon])=><div className="stat" key={key}><Icon/><div><strong>{data?.[key]?.toString()??"—"}</strong><span>{label}</span></div></div>)}</div>
    <div className="two-col"><TopList title="Most borrowed titles" rows={(data?.topTitles as unknown as GenericRow[])??[]}/><TopList title="Frequent borrowers" rows={(data?.topBorrowers as unknown as GenericRow[])??[]}/></div></div>;
}
export function TopList({title,rows}:{title:string;rows:GenericRow[]}) {return <Panel title={title}>{rows.length?<ol className="rank-list">{rows.map((r,i)=><li key={i}><span>{r.name}</span><strong>{r.count}</strong></li>)}</ol>:<Empty message="No circulation data yet."/>}</Panel>}
