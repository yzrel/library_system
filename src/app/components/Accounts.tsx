import {useEffect, useState} from "react"; import type {FormEvent} from "react"; import {QrCode, ShieldCheck, X} from "lucide-react"; import {toast} from "sonner";
import {api, availableAdvisoryClasses, json, schoolYearDefaults} from "@/app/api"; import type {ClassSection, Role, User} from "@/app/api"; import type {PrintPayload} from "@/app/types";
import {Button} from "@/app/components/ui/Button"; import {Field, SelectField} from "@/app/components/ui/Field"; import {Panel} from "@/app/components/ui/Panel";

export function Accounts({onPrint}:{onPrint:(p:PrintPayload)=>void}) {
  const [rows,setRows]=useState<User[]>([]);const [selected,setSelected]=useState<User|null>(null);const [loading,setLoading]=useState(true);
  const load=()=>{setLoading(true);return api<User[]>("/admin/accounts").then(setRows).catch(e=>toast.error(e.message)).finally(()=>setLoading(false))};useEffect(()=>{void load()},[]);
  const open=(user:User)=>setSelected(user);
  return <><Panel title="Account management"><p className="account-help">Double-click an account to edit its credentials, role, status, or password.</p>
    {loading&&<div className="loading">Loading accounts…</div>}
    <div className="table-wrap account-directory"><table><thead><tr><th>Account</th><th>Username / email</th><th className="school-id-col">School ID</th><th>Role</th><th>Status</th><th/></tr></thead>
      <tbody>{rows.map(user=><tr key={user.id} tabIndex={0} onDoubleClick={()=>open(user)} onKeyDown={e=>{if(e.key==="Enter")open(user)}}>
        <td><div className="account-person"><div className="account-avatar">{user.firstName[0]}{user.lastName[0]}</div><div><strong>{user.firstName} {user.lastName}</strong><span>{user.cardNumber||"Administrator"}</span></div></div></td>
        <td>{user.email}</td><td className="school-id-col">{user.schoolId}</td><td><span className="badge">{user.role}</span></td><td><span className={`badge ${user.status==="ACTIVE"?"":"danger"}`}>{user.status}</span></td>
        <td><div className="account-actions">{user.borrowerId&&<Button variant="ghost" onClick={()=>onPrint({kind:"qr",borrower:user})}><QrCode size={15}/> QR</Button>}<Button variant="ghost" onClick={()=>open(user)}>Edit</Button></div></td>
      </tr>)}</tbody></table></div>
    <div className="account-cards">{rows.map(user=><article key={user.id} tabIndex={0} onDoubleClick={()=>open(user)} onKeyDown={e=>{if(e.key==="Enter")open(user)}}>
      <div className="account-person"><div className="account-avatar">{user.firstName[0]}{user.lastName[0]}</div><div><strong>{user.firstName} {user.lastName}</strong><span>{user.cardNumber||"Administrator"} · {user.schoolId}</span></div></div>
      <div className="account-card-login"><span>Username / email</span><strong>{user.email}</strong></div>
      <div className="account-card-meta"><span className="badge">{user.role}</span><span className={`badge ${user.status==="ACTIVE"?"":"danger"}`}>{user.status}</span></div>
      <div className="account-actions">{user.borrowerId&&<Button variant="ghost" onClick={()=>onPrint({kind:"qr",borrower:user})}><QrCode size={16}/> Print QR</Button>}<Button variant="ghost" onClick={()=>open(user)}>Edit account</Button></div>
    </article>)}</div>
  </Panel>
  {selected&&<div className="modal-backdrop"><div className="modal account-modal" role="dialog" aria-modal="true" aria-labelledby="account-editor-title">
    <button className="modal-x" onClick={()=>setSelected(null)} aria-label="Close account editor"><X/></button>
    <div className="account-modal-head"><div className="account-avatar">{selected.firstName[0]}{selected.lastName[0]}</div><div><h2 id="account-editor-title">Edit {selected.firstName} {selected.lastName}</h2><p>Update login details and access. Leave the password blank to keep it unchanged.</p></div></div>
    <AccountEditor user={selected} onSaved={()=>{void load();setSelected(null)}}/>
  </div></div>}</>;
}

export function AccountEditor({user,onSaved}:{user:User;onSaved:()=>void}) {
  const [login,setLogin]=useState(user.email);const [role,setRole]=useState<Role>(user.role);
  const [status,setStatus]=useState(user.status);const [password,setPassword]=useState("");
  const [classes,setClasses]=useState<ClassSection[]>([]);const [advisoryClassId,setAdvisoryClassId]=useState("");
  const [studentClassId,setStudentClassId]=useState(user.classSectionId?String(user.classSectionId):"");
  const [busy,setBusy]=useState(false);
  useEffect(()=>{api<ClassSection[]>("/admin/classes").then(items=>{setClasses(items);const assigned=items.find(item=>item.adviserBorrowerId===user.borrowerId);setAdvisoryClassId(assigned?String(assigned.id):"")}).catch(e=>toast.error(e.message))},[user.borrowerId]);
  const save=async(e:FormEvent<HTMLFormElement>)=>{e.preventDefault();setBusy(true);try{
    await api(`/admin/accounts/${user.id}`,json("PATCH",{email:login,role,status,
      ...(role==="STUDENT"?{classSectionId:Number(studentClassId)}:{}),...(password?{password}:{})}));
    const current=classes.find(item=>item.adviserBorrowerId===user.borrowerId);
    if(role==="TEACHER"&&advisoryClassId) {
      await api(`/admin/classes/${advisoryClassId}/adviser`,json("PUT",{teacherBorrowerId:user.borrowerId}));
    } else if(current) {
      await api(`/admin/classes/${current.id}/adviser`,json("PUT",{teacherBorrowerId:null}));
    }
    setPassword("");toast.success(`Credentials updated for ${user.firstName} ${user.lastName}`);onSaved();
  }catch(error){toast.error((error as Error).message)}finally{setBusy(false)}};
  return <form className="account-editor" onSubmit={save}>
    <Field label="Username / email" value={login} onChange={e=>setLogin(e.target.value)} minLength={3} maxLength={254} autoComplete="off" required/>
    <SelectField label="Role" value={role} onChange={e=>setRole(e.target.value as Role)}>{(["STUDENT","TEACHER","ADMIN"] as Role[]).map(x=><option key={x}>{x}</option>)}</SelectField>
    <SelectField label="Status" value={status} onChange={e=>setStatus(e.target.value)}>{["ACTIVE","SUSPENDED","DISABLED"].map(x=><option key={x}>{x}</option>)}</SelectField>
    <Field label="New password" value={password} onChange={e=>setPassword(e.target.value)} type="password" minLength={8} maxLength={72} autoComplete="new-password" placeholder="Leave blank to keep current"/>
    {role==="STUDENT"&&<SelectField className="account-advisory" label="Grade and section" value={studentClassId} onChange={e=>setStudentClassId(e.target.value)} required><option value="">Select class</option>{classes.filter(item=>item.active&&item.schoolYear===schoolYearDefaults().schoolYear).map(item=><option key={item.id} value={item.id}>{item.label} · S.Y. {item.schoolYear}</option>)}</SelectField>}
    {role==="TEACHER"&&<SelectField className="account-advisory" label="Advisory class (optional)" value={advisoryClassId} onChange={e=>setAdvisoryClassId(e.target.value)}><option value="">No advisory class</option>{availableAdvisoryClasses(classes,user.borrowerId).map(item=><option key={item.id} value={item.id}>{item.label} · S.Y. {item.schoolYear}</option>)}</SelectField>}
    <div className="form-actions"><Button type="submit" disabled={busy}><ShieldCheck size={16}/>{busy?"Saving…":"Save changes"}</Button></div>
  </form>;
}
