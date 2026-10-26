import {useCallback, useEffect, useState} from "react"; import {FileDown, QrCode, Star} from "lucide-react"; import {toast} from "sonner";
import {api, session} from "@/app/api"; import type {AttendanceReport, FeedbackRating} from "@/app/api"; import {ATTENDANCE_PURPOSES} from "@/app/types";
import {Button} from "@/app/components/ui/Button"; import {Empty} from "@/app/components/ui/Empty"; import {Field} from "@/app/components/ui/Field"; import {Panel} from "@/app/components/ui/Panel";

export function AttendanceLogbook() {
  const today=new Date().toISOString().slice(0,10);
  const [from,setFrom]=useState(today);const [to,setTo]=useState(today);const [q,setQ]=useState("");
  const [data,setData]=useState<AttendanceReport|null>(null);const [loading,setLoading]=useState(true);
  const load=useCallback(async()=>{setLoading(true);try{setData(await api<AttendanceReport>(`/admin/attendance?from=${from}&to=${to}&q=${encodeURIComponent(q)}`))}catch(e){toast.error((e as Error).message)}finally{setLoading(false)}},[from,to,q]);
  useEffect(()=>{void load()},[]);
  const totalFeedback=data?Object.values(data.feedback).reduce((sum,value)=>sum+value,0):0;
  const percent=(rating:FeedbackRating)=>totalFeedback?Math.round((data!.feedback[rating]/totalFeedback)*100):0;
  const formatTime=(value:string)=>new Date(value).toLocaleString("en-PH",{dateStyle:"medium",timeStyle:"short"});
  return <div className="page-stack">
    <div className="page-actions"><form className="attendance-filters" onSubmit={e=>{e.preventDefault();void load()}}><Field label="From" type="date" value={from} onChange={e=>setFrom(e.target.value)} required/><Field label="To" type="date" value={to} onChange={e=>setTo(e.target.value)} required/><Field label="Search name or ID" value={q} onChange={e=>setQ(e.target.value)}/><Button>Apply filters</Button></form>
      <Button variant="secondary" onClick={()=>downloadAuthenticated(`/admin/attendance/export.csv?from=${from}&to=${to}&q=${encodeURIComponent(q)}`,"attendance-logbook.csv")}><FileDown size={17}/> Export CSV</Button></div>
    <div className="stats feedback-stats"><div className="stat"><QrCode/><div><strong>{data?.visits.length??"—"}</strong><span>Library visits</span></div></div><div className="stat"><Star/><div><strong>{data?.feedback.SATISFIED??"—"} <small>{percent("SATISFIED")}%</small></strong><span>Satisfied</span></div></div><div className="stat"><Star/><div><strong>{data?.feedback.NEUTRAL??"—"} <small>{percent("NEUTRAL")}%</small></strong><span>Neutral</span></div></div><div className="stat"><Star/><div><strong>{data?.feedback.NOT_SATISFIED??"—"} <small>{percent("NOT_SATISFIED")}%</small></strong><span>Not satisfied</span></div></div></div>
    <Panel title="Attendance logbook">{loading?<div className="loading">Loading attendance…</div>:!data?.visits.length?<Empty message="No attendance records for this period."/>:<>
      <div className="table-wrap attendance-directory"><table><thead><tr><th>Date and time</th><th>Name</th><th>Grade and section / department</th><th>Purpose</th><th>Method</th></tr></thead><tbody>{data.visits.map(row=><tr key={row.id}><td>{formatTime(row.checkedInAt)}</td><td><strong>{row.name}</strong><small>{row.role}</small></td><td>{row.gradeSection||"—"}</td><td>{ATTENDANCE_PURPOSES.find(x=>x.value===row.purpose)?.label}</td><td><span className="badge">{row.entryMethod}</span></td></tr>)}</tbody></table></div>
      <div className="attendance-cards">{data.visits.map(row=><article key={row.id}><div><strong>{row.name}</strong><span>{formatTime(row.checkedInAt)}</span></div><p>{row.gradeSection||row.role}</p><footer><span>{ATTENDANCE_PURPOSES.find(x=>x.value===row.purpose)?.label}</span><span className="badge">{row.entryMethod}</span></footer></article>)}</div>
    </>}</Panel>
  </div>;
}

async function downloadAuthenticated(path:string,name:string){try{const token=session.get();const r=await fetch(`/api${path}`,{headers:{Authorization:`Bearer ${token}`}});if(!r.ok)throw new Error("Download failed");saveBlob(await r.blob(),name)}catch(e){toast.error((e as Error).message)}}
function saveBlob(blob:Blob,name:string){const u=URL.createObjectURL(blob);const a=document.createElement("a");a.href=u;a.download=name;a.click();URL.revokeObjectURL(u)}
