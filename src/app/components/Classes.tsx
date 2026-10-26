import {useCallback, useEffect, useState} from "react"; import type {FormEvent} from "react"; import {toast} from "sonner";
import {api, json, schoolYearDefaults} from "@/app/api"; import type {ClassSection, User} from "@/app/api";
import {Button} from "@/app/components/ui/Button"; import {Empty} from "@/app/components/ui/Empty"; import {Field, SelectField} from "@/app/components/ui/Field"; import {Panel} from "@/app/components/ui/Panel";

export function Classes() {
  const defaults=schoolYearDefaults();
  const [rows,setRows]=useState<ClassSection[]>([]);const [teachers,setTeachers]=useState<User[]>([]);
  const [year,setYear]=useState(defaults.schoolYear);const [showInactive,setShowInactive]=useState(false);
  const [editId,setEditId]=useState<number|null>(null);const [gradeLevel,setGradeLevel]=useState("");
  const [sectionName,setSectionName]=useState("");const [schoolYear,setSchoolYear]=useState(defaults.schoolYear);
  const [busy,setBusy]=useState(false);
  const load=useCallback(async()=>{try{const [classes,accounts]=await Promise.all([api<ClassSection[]>("/admin/classes"),api<User[]>("/admin/accounts")]);setRows(classes);setTeachers(accounts.filter(user=>user.role==="TEACHER"&&user.status==="ACTIVE"&&user.borrowerId!=null))}catch(e){toast.error((e as Error).message)}},[]);
  useEffect(()=>{void load()},[load]);
  const reset=()=>{setEditId(null);setGradeLevel("");setSectionName("");setSchoolYear(year)};
  const save=async(e:FormEvent)=>{e.preventDefault();setBusy(true);try{
    await api(editId?`/admin/classes/${editId}`:"/admin/classes",json(editId?"PATCH":"POST",{gradeLevel,sectionName,schoolYear,active:true}));
    toast.success(editId?"Class updated":"Class added");reset();await load();
  }catch(error){toast.error((error as Error).message)}finally{setBusy(false)}};
  const edit=(item:ClassSection)=>{setEditId(item.id);setGradeLevel(item.gradeLevel);setSectionName(item.sectionName);setSchoolYear(item.schoolYear);window.scrollTo({top:0,behavior:"smooth"})};
  const toggleActive=async(item:ClassSection)=>{if(item.active&&item.adviserBorrowerId){toast.error("Remove the teacher adviser before deactivating this class");return}try{await api(`/admin/classes/${item.id}`,json("PATCH",{gradeLevel:item.gradeLevel,sectionName:item.sectionName,schoolYear:item.schoolYear,active:!item.active}));toast.success(item.active?"Class deactivated":"Class activated");await load()}catch(e){toast.error((e as Error).message)}};
  const assign=async(item:ClassSection,value:string)=>{try{await api(`/admin/classes/${item.id}/adviser`,json("PUT",{teacherBorrowerId:value?Number(value):null}));toast.success(value?"Adviser assigned":"Adviser removed");await load()}catch(e){toast.error((e as Error).message)}};
  const years=Array.from(new Set([defaults.schoolYear,...rows.map(item=>item.schoolYear)])).sort().reverse();
  const visible=rows.filter(item=>item.schoolYear===year&&(showInactive||item.active));
  const assignedTeacherIds=new Set(rows.map(item=>item.adviserBorrowerId).filter((id):id is number=>id!=null));
  return <div className="page-stack">
    <Panel title={editId?"Edit class":"Add grade and section"} action={editId&&<Button variant="ghost" onClick={reset}>Cancel edit</Button>}>
      <form className="class-form" onSubmit={save}>
        <SelectField label="Grade level" value={gradeLevel} onChange={e=>setGradeLevel(e.target.value)} required><option value="">Select grade</option>{["Grade 7","Grade 8","Grade 9","Grade 10","Grade 11","Grade 12"].map(grade=><option key={grade}>{grade}</option>)}</SelectField>
        <Field label="Section name" value={sectionName} onChange={e=>setSectionName(e.target.value)} required maxLength={80} placeholder="e.g. Rizal"/>
        <Field label="School year" value={schoolYear} onChange={e=>setSchoolYear(e.target.value)} required pattern="\d{4}-\d{4}"/>
        <div className="form-actions"><Button disabled={busy}>{busy?"Saving…":editId?"Save class":"Add class"}</Button></div>
      </form>
    </Panel>
    <Panel title="Class directory" action={<div className="class-filters"><SelectField label="School year" value={year} onChange={e=>{setYear(e.target.value);if(!editId)setSchoolYear(e.target.value)}}>{years.map(value=><option key={value}>{value}</option>)}</SelectField><label className="check-field"><input type="checkbox" checked={showInactive} onChange={e=>setShowInactive(e.target.checked)}/> Show inactive</label></div>}>
      {visible.length?<><div className="table-wrap class-directory"><table><thead><tr><th>Grade and section</th><th>School year</th><th>Teacher adviser</th><th>Status</th><th/></tr></thead><tbody>{visible.map(item=><tr key={item.id}>
        <td><strong>{item.label}</strong></td><td>{item.schoolYear}</td><td><select aria-label={`Adviser for ${item.label}`} value={item.adviserBorrowerId??""} disabled={!item.active} onChange={e=>void assign(item,e.target.value)}><option value="">No adviser</option>{teachers.filter(teacher=>!assignedTeacherIds.has(teacher.borrowerId!)||teacher.borrowerId===item.adviserBorrowerId).map(teacher=><option key={teacher.borrowerId} value={teacher.borrowerId}>{teacher.firstName} {teacher.lastName}</option>)}</select></td><td><span className={`badge ${item.active?"":"danger"}`}>{item.active?"ACTIVE":"INACTIVE"}</span></td><td><div className="action-group"><Button variant="ghost" onClick={()=>edit(item)}>Edit</Button><Button variant={item.active?"danger":"secondary"} onClick={()=>void toggleActive(item)}>{item.active?"Deactivate":"Activate"}</Button></div></td>
      </tr>)}</tbody></table></div><div className="class-cards">{visible.map(item=><article key={item.id}><header><div><strong>{item.label}</strong><span>S.Y. {item.schoolYear}</span></div><span className={`badge ${item.active?"":"danger"}`}>{item.active?"ACTIVE":"INACTIVE"}</span></header><SelectField label="Teacher adviser" value={item.adviserBorrowerId??""} disabled={!item.active} onChange={e=>void assign(item,e.target.value)}><option value="">No adviser</option>{teachers.filter(teacher=>!assignedTeacherIds.has(teacher.borrowerId!)||teacher.borrowerId===item.adviserBorrowerId).map(teacher=><option key={teacher.borrowerId} value={teacher.borrowerId}>{teacher.firstName} {teacher.lastName}</option>)}</SelectField><footer><Button variant="ghost" onClick={()=>edit(item)}>Edit</Button><Button variant={item.active?"danger":"secondary"} onClick={()=>void toggleActive(item)}>{item.active?"Deactivate":"Activate"}</Button></footer></article>)}</div></>:<Empty message="No classes found for this school year."/>}
    </Panel>
  </div>;
}
