import {useEffect, useState} from "react"; import {toast} from "sonner"; import {api, displayDate, json} from "@/app/api"; import type {GenericRow} from "@/app/types";
import {Empty} from "@/app/components/ui/Empty"; import {Panel} from "@/app/components/ui/Panel";

export function Requests() {
  const [res,setRes]=useState<GenericRow[]>([]);const [acq,setAcq]=useState<GenericRow[]>([]);
  const load=async()=>{try{const [r,a]=await Promise.all([api<GenericRow[]>("/admin/requests/reservations"),api<GenericRow[]>("/admin/requests/acquisitions")]);setRes(r);setAcq(a)}catch(e){toast.error((e as Error).message)}};useEffect(()=>{void load()},[]);
  const update=async(type:"reservations"|"acquisitions",id:number,status:string)=>{try{await api(`/admin/requests/${type}/${id}`,json("PATCH",{status}));void load()}catch(e){toast.error((e as Error).message)}};
  return <div className="two-col"><Panel title="Book reservations">{res.length?<RequestList rows={res} onUpdate={(id,s)=>update("reservations",id,s)}/>:<Empty message="No reservation requests."/>}</Panel>
    <Panel title="Acquisition requests">{acq.length?<RequestList rows={acq} onUpdate={(id,s)=>update("acquisitions",id,s)}/>:<Empty message="No acquisition requests."/>}</Panel></div>;
}
export function RequestList({rows,onUpdate,readOnly=false}:{rows:GenericRow[];onUpdate:(id:number,s:string)=>void;readOnly?:boolean}){return <div className="request-list">{rows.map(r=><div key={r.id as number}><div><strong>{r.title}</strong><span>{r.borrower||r.requester} · {displayDate(r.requestedAt as string)}</span></div>{readOnly?<span className="badge">{String(r.status).replaceAll("_"," ")}</span>:<select value={r.status as string} onChange={e=>onUpdate(r.id as number,e.target.value)}>{["PENDING","APPROVED","REJECTED","FULFILLED","CANCELLED"].map(s=><option key={s}>{s}</option>)}</select>}</div>)}</div>}
