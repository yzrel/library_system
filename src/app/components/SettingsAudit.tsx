import {useCallback, useEffect, useMemo, useState} from "react"; import type {FormEvent} from "react";
import {BookMarked, Clock3, MapPin, Search, Settings2} from "lucide-react"; import {toast} from "sonner";
import {api, displayDateTime, json} from "@/app/api"; import type {BookSubject} from "@/app/api"; import type {GenericRow} from "@/app/types";
import {Button} from "@/app/components/ui/Button"; import {Empty} from "@/app/components/ui/Empty"; import {Field} from "@/app/components/ui/Field"; import {Panel} from "@/app/components/ui/Panel";

type SettingsTab = "policy" | "subjects" | "audit";

const ACTION_LABELS: Record<string, string> = {
  LOGIN: "Signed in",
  REGISTER: "Registered",
  CREATE_BOOK: "Created book",
  UPDATE_BOOK: "Updated book",
  CREATE_COPY: "Added copy",
  UPDATE_COPY: "Updated copy",
  DELETE_COPY: "Deleted copy",
  BULK_CREATE_COPIES: "Added copies",
  ISBN_INTAKE: "ISBN intake",
  UPDATE_SETTINGS: "Updated settings",
  CREATE_SUBJECT: "Added subject",
  UPDATE_SUBJECT: "Updated subject",
  CHECKOUT: "Checked out",
  RETURN: "Returned",
  UPDATE_ACCOUNT: "Updated account",
  UPDATE_PROFILE: "Updated profile",
  CHANGE_PASSWORD: "Changed password",
};

function actionLabel(action?: string) {
  if (!action) return "Activity";
  return ACTION_LABELS[action] ?? action.replaceAll("_", " ").toLowerCase().replace(/^\w/, letter => letter.toUpperCase());
}

function recordLabel(row: GenericRow) {
  const type = String(row.entityType ?? "").replaceAll("_", " ");
  return row.entityId == null || row.entityId === "" ? type || "—" : `${type} #${row.entityId}`;
}

export function SettingsAudit() {
  const [tab, setTab] = useState<SettingsTab>("policy");
  return <div className="page-stack settings-page">
    <header className="settings-hero">
      <div>
        <p className="eyebrow">Administration</p>
        <h1>Settings and activity</h1>
        <p className="muted">Keep school identity, borrowing rules, catalog headings, and staff activity in one place.</p>
      </div>
    </header>
    <div className="tabs settings-tabs" role="tablist" aria-label="Settings sections">
      <button type="button" role="tab" aria-selected={tab==="policy"} className={tab==="policy"?"active":""} onClick={()=>setTab("policy")}><Settings2 size={16}/> Library policy</button>
      <button type="button" role="tab" aria-selected={tab==="subjects"} className={tab==="subjects"?"active":""} onClick={()=>setTab("subjects")}><BookMarked size={16}/> Catalog subjects</button>
      <button type="button" role="tab" aria-selected={tab==="audit"} className={tab==="audit"?"active":""} onClick={()=>setTab("audit")}><Clock3 size={16}/> Audit trail</button>
    </div>
    {tab==="policy"&&<PolicySettings/>}
    {tab==="subjects"&&<SubjectSettings/>}
    {tab==="audit"&&<AuditTrail/>}
  </div>;
}

function PolicySettings() {
  const [settings,setSettings]=useState<Record<string,string>>({});
  const [saving,setSaving]=useState(false);
  const load=async()=>{try{setSettings(await api<Record<string,string>>("/admin/settings"))}catch(e){toast.error((e as Error).message)}};
  useEffect(()=>{void load()},[]);
  const set=(key:string,value:string)=>setSettings(current=>({...current,[key]:value}));
  const save=async(e:FormEvent<HTMLFormElement>)=>{e.preventDefault();setSaving(true);try{await api("/admin/settings",json("PUT",settings));toast.success("Library policy saved")}catch(err){toast.error((err as Error).message)}finally{setSaving(false)}};
  return <form className="settings-policy" onSubmit={e=>void save(e)}>
    <Panel title="School identity">
      <p className="muted settings-help">Shown on borrower cards, book cards, and public library pages.</p>
      <div className="form-grid wide">
        <Field className="span-2" label="School name" value={settings.school_name??""} onChange={e=>set("school_name",e.target.value)}/>
        <Field label="Abbreviation" value={settings.school_abbreviation??""} onChange={e=>set("school_abbreviation",e.target.value)}/>
      </div>
    </Panel>
    <Panel title="Borrowing rules">
      <p className="muted settings-help">These limits apply when staff check out books to students and teachers.</p>
      <div className="form-grid wide">
        <Field label="Student loan days" type="number" min={1} value={settings.student_loan_days??""} onChange={e=>set("student_loan_days",e.target.value)}/>
        <Field label="Teacher loan days" type="number" min={1} value={settings.teacher_loan_days??""} onChange={e=>set("teacher_loan_days",e.target.value)}/>
        <Field label="Student loan limit" type="number" min={1} value={settings.student_loan_limit??""} onChange={e=>set("student_loan_limit",e.target.value)}/>
        <Field label="Teacher loan limit" type="number" min={1} value={settings.teacher_loan_limit??""} onChange={e=>set("teacher_loan_limit",e.target.value)}/>
      </div>
    </Panel>
    <div className="settings-save"><Button disabled={saving}>{saving?"Saving…":"Save policy"}</Button></div>
  </form>;
}

function SubjectSettings() {
  const [rows,setRows]=useState<BookSubject[]>([]);
  const [name,setName]=useState("");
  const [editId,setEditId]=useState<number|null>(null);
  const [showInactive,setShowInactive]=useState(false);
  const [busy,setBusy]=useState(false);
  const load=useCallback(async()=>{try{setRows(await api<BookSubject[]>("/admin/subjects"))}catch(e){toast.error((e as Error).message)}},[]);
  useEffect(()=>{void load()},[load]);
  const reset=()=>{setEditId(null);setName("")};
  const save=async(e:FormEvent)=>{e.preventDefault();setBusy(true);try{
    await api(editId?`/admin/subjects/${editId}`:"/admin/subjects",json(editId?"PATCH":"POST",{name,active:true}));
    toast.success(editId?"Subject updated":"Subject added");reset();await load();
  }catch(error){toast.error((error as Error).message)}finally{setBusy(false)}};
  const edit=(item:BookSubject)=>{setEditId(item.id);setName(item.name)};
  const toggleActive=async(item:BookSubject)=>{try{await api(`/admin/subjects/${item.id}`,json("PATCH",{active:!item.active}));toast.success(item.active?"Subject deactivated":"Subject activated");await load()}catch(e){toast.error((e as Error).message)}};
  const visible=rows.filter(item=>showInactive||item.active);
  return <>
    <Panel title={editId?"Edit catalog subject":"Add catalog subject"} action={editId&&<Button variant="ghost" onClick={reset}>Cancel edit</Button>}>
      <p className="muted settings-help">These headings appear in the Catalog dropdown when adding or scanning books. Use school terms such as Fiction, Science, or Filipino.</p>
      <form className="subject-form" onSubmit={save}>
        <Field label="Subject name" value={name} onChange={e=>setName(e.target.value)} required maxLength={80} placeholder="e.g. Research Reports"/>
        <div className="form-actions"><Button disabled={busy}>{busy?"Saving…":editId?"Save subject":"Add subject"}</Button></div>
      </form>
    </Panel>
    <Panel title="Catalog subjects" action={<label className="check-field"><input type="checkbox" checked={showInactive} onChange={e=>setShowInactive(e.target.checked)}/> Show inactive</label>}>
      {visible.length?<><div className="table-wrap subject-directory"><table><thead><tr><th>Subject</th><th>Status</th><th/></tr></thead><tbody>{visible.map(item=><tr key={item.id}>
        <td><strong>{item.name}</strong></td><td><span className={`badge ${item.active?"":"danger"}`}>{item.active?"ACTIVE":"INACTIVE"}</span></td>
        <td><div className="action-group"><Button variant="ghost" onClick={()=>edit(item)}>Edit</Button><Button variant={item.active?"danger":"secondary"} onClick={()=>void toggleActive(item)}>{item.active?"Deactivate":"Activate"}</Button></div></td>
      </tr>)}</tbody></table></div>
      <div className="subject-cards">{visible.map(item=><article key={item.id}><header><div><strong>{item.name}</strong></div><span className={`badge ${item.active?"":"danger"}`}>{item.active?"ACTIVE":"INACTIVE"}</span></header>
        <footer><Button variant="ghost" onClick={()=>edit(item)}>Edit</Button><Button variant={item.active?"danger":"secondary"} onClick={()=>void toggleActive(item)}>{item.active?"Deactivate":"Activate"}</Button></footer>
      </article>)}</div></>:<Empty message="No subjects yet. Add the first catalog subject."/>}
    </Panel>
  </>;
}

function AuditTrail() {
  const [audit,setAudit]=useState<GenericRow[]>([]);
  const [query,setQuery]=useState("");
  const [action,setAction]=useState("");
  const load=async()=>{try{setAudit(await api<GenericRow[]>("/admin/audit?limit=200"))}catch(e){toast.error((e as Error).message)}};
  useEffect(()=>{void load()},[]);
  const actions=useMemo(()=>[...new Set(audit.map(row=>String(row.action??"")).filter(Boolean))].sort(),[audit]);
  const visible=useMemo(()=>{
    const needle=query.trim().toLowerCase();
    return audit.filter(row=>{
      if(action&&row.action!==action)return false;
      if(!needle)return true;
      return [row.actorName,actionLabel(String(row.action??"")),row.entityType,row.entityId,row.details,row.location,row.ipAddress]
        .some(value=>String(value??"").toLowerCase().includes(needle));
    });
  },[audit,query,action]);
  return <Panel title="Audit trail" action={<span className="badge">{visible.length} shown</span>}>
    <p className="muted settings-help">Staff actions are listed newest first. Sign-in rows include the computer or school network where the login happened.</p>
    <div className="audit-toolbar">
      <form className="inline-search" onSubmit={e=>e.preventDefault()}><Search/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search person, action, or location" aria-label="Search audit trail"/></form>
      <label className="catalog-subject-filter"><Clock3 size={17} aria-hidden="true"/><select value={action} onChange={e=>setAction(e.target.value)} aria-label="Filter audit actions">
        <option value="">All actions</option>
        {actions.map(item=><option key={item} value={item}>{actionLabel(item)}</option>)}
      </select></label>
    </div>
    {visible.length?<><div className="table-wrap audit-directory"><table><thead><tr><th>When</th><th>Person</th><th>Action</th><th>Record</th><th>Location</th><th>Details</th></tr></thead>
      <tbody>{visible.map(row=><tr key={row.id as number}>
        <td>{displayDateTime(row.createdAt as string)}</td>
        <td><strong>{String(row.actorName??"System")}</strong></td>
        <td><span className="badge">{actionLabel(String(row.action??""))}</span></td>
        <td>{recordLabel(row)}</td>
        <td>{row.location||row.ipAddress?<span className="audit-location"><MapPin size={14} aria-hidden="true"/>{String(row.location||row.ipAddress)}</span>:"—"}</td>
        <td>{row.details||"—"}</td>
      </tr>)}</tbody></table></div>
      <div className="audit-cards">{visible.map(row=><article key={row.id as number}>
        <header><strong>{actionLabel(String(row.action??""))}</strong><span>{displayDateTime(row.createdAt as string)}</span></header>
        <p>{String(row.actorName??"System")} · {recordLabel(row)}</p>
        <footer>
          <span className="audit-location">{row.location||row.ipAddress?<><MapPin size={14} aria-hidden="true"/>{String(row.location||row.ipAddress)}</>:"No location recorded"}</span>
          {row.details&&<small>{String(row.details)}</small>}
        </footer>
      </article>)}</div></>:<Empty message={audit.length?"No activity matches this search.":"No audit entries yet."}/>}
  </Panel>;
}
