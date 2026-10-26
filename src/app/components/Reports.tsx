import {useEffect, useState} from "react"; import {BookCopy, FileDown, History, RotateCcw} from "lucide-react"; import {toast} from "sonner";
import {api, session} from "@/app/api"; import type {GenericRow} from "@/app/types"; import {TopList} from "@/app/components/Dashboard"; import {Panel} from "@/app/components/ui/Panel";

export function Reports() {
  const [data,setData]=useState<GenericRow|null>(null);useEffect(()=>{api<GenericRow>("/admin/reports/dashboard").then(setData).catch(e=>toast.error(e.message))},[]);
  return <div className="page-stack"><div className="stats"><div className="stat"><BookCopy/><div><strong>{data?.copies?.toString()??"—"}</strong><span>Inventory copies</span></div></div><div className="stat"><RotateCcw/><div><strong>{data?.totalCirculations?.toString()??"—"}</strong><span>Total circulations</span></div></div><div className="stat"><History/><div><strong>{data?.overdue?.toString()??"—"}</strong><span>Overdue now</span></div></div></div>
    <Panel title="Evaluation-ready reports"><div className="report-links"><a href="/api/admin/reports/circulation.csv" onClick={e=>{e.preventDefault();downloadAuthenticated("/admin/reports/circulation.csv","circulation.csv")}}><FileDown/> Circulation history CSV</a>
      <button onClick={()=>downloadJson("/admin/reports/accession","accession-register.json")}><FileDown/> Accession register</button>
      <button onClick={()=>downloadJson("/admin/reports/circulation","circulation-history.json")}><FileDown/> Full circulation data</button></div></Panel>
    <div className="two-col"><TopList title="Most borrowed titles" rows={(data?.topTitles as unknown as GenericRow[])??[]}/><TopList title="Frequent borrowers" rows={(data?.topBorrowers as unknown as GenericRow[])??[]}/></div></div>;
}
async function downloadAuthenticated(path:string,name:string){try{const token=session.get();const r=await fetch(`/api${path}`,{headers:{Authorization:`Bearer ${token}`}});if(!r.ok)throw new Error("Download failed");saveBlob(await r.blob(),name)}catch(e){toast.error((e as Error).message)}}
async function downloadJson(path:string,name:string){try{const data=await api<unknown>(path);saveBlob(new Blob([JSON.stringify(data,null,2)],{type:"application/json"}),name)}catch(e){toast.error((e as Error).message)}}
function saveBlob(blob:Blob,name:string){const u=URL.createObjectURL(blob);const a=document.createElement("a");a.href=u;a.download=name;a.click();URL.revokeObjectURL(u)}
