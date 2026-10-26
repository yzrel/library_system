import {useCallback, useEffect, useState} from "react"; import type {FormEvent} from "react"; import {ArrowRight, CheckCircle2, Plus, Printer, Search, X} from "lucide-react"; import {toast} from "sonner";
import {api, displayDate, distributionCreateLines, distributionSelectionReady, json, schoolYearDefaults} from "@/app/api"; import type {Book, ClassSection, Copy, DistributionDetail, DistributionPreview, DistributionSummary, Loan, User} from "@/app/api"; import type {PrintPayload} from "@/app/types";
import {AutocompleteField} from "@/app/components/ui/AutocompleteField"; import {Button} from "@/app/components/ui/Button"; import {Empty} from "@/app/components/ui/Empty"; import {Field, SelectField, TextAreaField} from "@/app/components/ui/Field"; import {Panel} from "@/app/components/ui/Panel";

type DistributionFormLine = {key: number; bookId: string; quantity: number};
export function Distribution({onPrint}:{onPrint:(p:PrintPayload)=>void}) {
  const defaults=schoolYearDefaults();
  const [teachers,setTeachers]=useState<User[]>([]);const [books,setBooks]=useState<Book[]>([]);
  const [classes,setClasses]=useState<ClassSection[]>([]);const [classSectionId,setClassSectionId]=useState("");
  const [rows,setRows]=useState<DistributionSummary[]>([]);const [loading,setLoading]=useState(true);
  const [tab,setTab]=useState<"active"|"history">("active");const [q,setQ]=useState("");
  const [teacherId,setTeacherId]=useState("");const [gradeSection,setGradeSection]=useState("");
  const [schoolYear,setSchoolYear]=useState(defaults.schoolYear);const [expectedReturnDate,setExpectedReturnDate]=useState(defaults.expectedReturnDate);
  const [notes,setNotes]=useState("");const [lines,setLines]=useState<DistributionFormLine[]>([{key:1,bookId:"",quantity:1}]);
  const [lineKey,setLineKey]=useState(2);const [formVersion,setFormVersion]=useState(0);const [preview,setPreview]=useState<DistributionPreview|null>(null);
  const [selectedCopies,setSelectedCopies]=useState<Record<number,number[]>>({});const [submitting,setSubmitting]=useState(false);
  const [detail,setDetail]=useState<DistributionDetail|null>(null);const [returnIds,setReturnIds]=useState<number[]>([]);
  const load=useCallback(async()=>{setLoading(true);try{const [t,b,d,c]=await Promise.all([
    api<User[]>("/admin/borrowers"),api<Book[]>("/admin/catalog/books"),api<DistributionSummary[]>("/admin/distributions"),api<ClassSection[]>("/admin/classes")
  ]);setTeachers(t.filter(user=>user.role==="TEACHER"&&user.borrowerStatus==="ACTIVE"));setBooks(b);setRows(d);setClasses(c)}
  catch(e){toast.error((e as Error).message)}finally{setLoading(false)}},[]);
  useEffect(()=>{void load()},[load]);
  const teacherOptions=teachers.map(t=>({value:String(t.borrowerId),label:`${t.firstName} ${t.lastName} — ${t.gradeSection||t.department||t.cardNumber}`,keywords:[t.cardNumber,t.schoolId,t.firstName,t.lastName,t.gradeSection,t.department].filter(Boolean).join(" ")}));
  const bookOptions=books.map(b=>({value:String(b.id),label:`${b.title} — ${b.author}`,keywords:[b.title,b.author,b.isbn].filter(Boolean).join(" ")}));
  const updateLine=(key:number,change:Partial<DistributionFormLine>)=>{setLines(current=>current.map(line=>line.key===key?{...line,...change}:line));setPreview(null)};
  const removeLine=(key:number)=>{setLines(current=>current.filter(line=>line.key!==key));setPreview(null)};
  const requestPreview=async(e:FormEvent)=>{
    e.preventDefault();
    if(!teacherId||lines.some(line=>!line.bookId)){toast.error("Choose a teacher and a title for every line");return}
    setSubmitting(true);
    try{
      const result=await api<DistributionPreview>("/admin/distributions/preview",json("POST",{
        teacherBorrowerId:Number(teacherId),gradeSection,schoolYear,expectedReturnDate,
        lines:lines.map(line=>({bookId:Number(line.bookId),quantity:Number(line.quantity)}))
      }));
      setPreview(result);setSelectedCopies(Object.fromEntries(result.lines.map(line=>[line.bookId,line.proposedCopies.map(copy=>copy.id)])));
      if(result.lines.some(line=>line.shortfall>0))toast.error("Some titles do not have enough available copies");
      else toast.success("Copies are ready for review");
    }catch(err){toast.error((err as Error).message)}finally{setSubmitting(false)}
  };
  const toggleCopy=(bookId:number,copyId:number,quantity:number)=>{
    setSelectedCopies(current=>{const selected=current[bookId]??[];if(selected.includes(copyId))return {...current,[bookId]:selected.filter(id=>id!==copyId)};
      if(selected.length>=quantity){toast.error(`Select exactly ${quantity} copies for this title`);return current}
      return {...current,[bookId]:[...selected,copyId]};});
  };
  const ready=distributionSelectionReady(preview,selectedCopies);
  const resetForm=()=>{setTeacherId("");setClassSectionId("");setGradeSection("");setSchoolYear(defaults.schoolYear);setExpectedReturnDate(defaults.expectedReturnDate);setNotes("");setLines([{key:lineKey,bookId:"",quantity:1}]);setLineKey(k=>k+1);setFormVersion(version=>version+1);setPreview(null);setSelectedCopies({})};
  const confirmDistribution=async()=>{
    if(!preview||!ready)return;setSubmitting(true);
    try{await api("/admin/distributions",json("POST",{teacherBorrowerId:Number(teacherId),gradeSection,schoolYear,expectedReturnDate,notes,
      lines:distributionCreateLines(preview,selectedCopies)}));
      toast.success("Class books distributed");resetForm();await load();
    }catch(e){toast.error((e as Error).message)}finally{setSubmitting(false)}
  };
  const openDetail=async(id:number)=>{try{const value=await api<DistributionDetail>(`/admin/distributions/${id}`);setDetail(value);setReturnIds([])}catch(e){toast.error((e as Error).message)}};
  const returnSelected=async(all=false)=>{
    if(!detail||(!all&&!returnIds.length))return;
    if(!window.confirm(`Return ${all?"all outstanding":returnIds.length} ${all?"copies":"selected copies"}?`))return;
    try{const value=await api<DistributionDetail>(`/admin/distributions/${detail.id}/${all?"return-all":"returns"}`,json("POST",all?undefined:{loanIds:returnIds}));
      setDetail(value);setReturnIds([]);toast.success("Copies returned");await load();
    }catch(e){toast.error((e as Error).message)}
  };
  const printBorrower=async()=>{if(!detail)return;try{const data=await api<{borrower:User;transactions:Loan[]}>(`/admin/print/borrower-card/${detail.teacherBorrowerId}`);onPrint({kind:"borrower",...data})}catch(e){toast.error((e as Error).message)}};
  const printBook=async(copyId:number)=>{try{const data=await api<{copy:Copy;transactions:Loan[]}>(`/admin/print/book-card/${copyId}`);onPrint({kind:"book",...data})}catch(e){toast.error((e as Error).message)}};
  const visible=rows.filter(row=>tab==="active"?row.status!=="RETURNED":row.status==="RETURNED").filter(row=>`${row.teacherName} ${row.gradeSection} ${row.schoolYear}`.toLowerCase().includes(q.toLowerCase()));
  return <div className="page-stack">
    <Panel title="Create class distribution" action={preview&&<Button variant="ghost" onClick={()=>setPreview(null)}>Edit request</Button>}>
      {!preview?<form key={formVersion} className="distribution-form" onSubmit={requestPreview}>
        <div className="form-grid wide">
          <AutocompleteField label="Teacher custodian" name="teacherBorrowerId" required options={teacherOptions} placeholder="Search teacher" onValueChange={value=>{setTeacherId(value);const assigned=classes.find(item=>String(item.adviserBorrowerId)===value&&item.active);setClassSectionId(assigned?String(assigned.id):"");setGradeSection(assigned?.label??"");if(assigned)setSchoolYear(assigned.schoolYear)}}/>
          <SelectField label="Advisory grade and section" value={classSectionId} onChange={e=>{setClassSectionId(e.target.value);const selected=classes.find(item=>String(item.id)===e.target.value);setGradeSection(selected?.label??"");if(selected)setSchoolYear(selected.schoolYear)}} required><option value="">Assign the teacher an advisory class first</option>{classes.filter(item=>item.active&&String(item.adviserBorrowerId)===teacherId).map(item=><option key={item.id} value={item.id}>{item.label}</option>)}</SelectField>
          <Field label="School year" value={schoolYear} onChange={e=>setSchoolYear(e.target.value)} required pattern="\d{4}-\d{4}"/>
          <Field label="Expected return date" type="date" value={expectedReturnDate} onChange={e=>setExpectedReturnDate(e.target.value)} required/>
        </div>
        <div className="distribution-lines"><h3>Titles and quantities</h3>{lines.map((line,index)=><div className="distribution-line" key={line.key}>
          <AutocompleteField label={`Book title ${index+1}`} name={`book-${line.key}`} required options={bookOptions} placeholder="Search title, author, ISBN, or call number" onValueChange={bookId=>updateLine(line.key,{bookId})}/>
          <Field label="Quantity" type="number" min={1} max={500} value={line.quantity} onChange={e=>updateLine(line.key,{quantity:Number(e.target.value)})} required/>
          {lines.length>1&&<Button type="button" variant="ghost" aria-label={`Remove title ${index+1}`} onClick={()=>removeLine(line.key)}><X size={18}/></Button>}
        </div>)}
          <Button type="button" variant="secondary" onClick={()=>{setLines(current=>[...current,{key:lineKey,bookId:"",quantity:1}]);setLineKey(k=>k+1)}}><Plus size={17}/> Add title</Button>
        </div>
        <TextAreaField label="Notes" value={notes} onChange={e=>setNotes(e.target.value)} maxLength={1000} rows={3}/>
        <div className="distribution-actions"><Button disabled={submitting}>{submitting?"Preparing…":"Review selected copies"} <ArrowRight size={17}/></Button></div>
      </form>:<div className="distribution-review">
        <div className="review-summary"><div><span>Teacher</span><strong>{preview.teacher.firstName} {preview.teacher.lastName}</strong></div><div><span>Class</span><strong>{preview.gradeSection}</strong></div><div><span>School year</span><strong>{preview.schoolYear}</strong></div><div><span>Return by</span><strong>{displayDate(preview.expectedReturnDate)}</strong></div></div>
        {preview.lines.map(line=><section className={`copy-review ${line.shortfall?"has-shortfall":""}`} key={line.bookId}>
          <header><div><h3>{line.title}</h3><p>{line.author}</p></div><span className="badge">{selectedCopies[line.bookId]?.length??0} / {line.quantity} selected</span></header>
          {line.shortfall>0&&<p className="shortfall">Short by {line.shortfall}. Add available copies to the catalog before confirming.</p>}
          <div className="copy-options">{line.availableCopies.map(copy=><label key={copy.id} className={(selectedCopies[line.bookId]??[]).includes(copy.id)?"selected":""}>
            <input type="checkbox" checked={(selectedCopies[line.bookId]??[]).includes(copy.id)} onChange={()=>toggleCopy(line.bookId,copy.id,line.quantity)}/>
            <span><strong>{copy.accessionNumber}</strong><small>{copy.callNumber||copy.barcode}</small></span>
          </label>)}</div>
        </section>)}
        <div className="distribution-actions"><Button variant="secondary" onClick={()=>setPreview(null)}>Back</Button><Button disabled={!ready||submitting} onClick={()=>void confirmDistribution()}><CheckCircle2 size={17}/>{submitting?"Confirming…":"Confirm distribution"}</Button></div>
      </div>}
    </Panel>
    <Panel title="Class distributions" action={<div className="inline-search"><Search/><input value={q} onChange={e=>setQ(e.target.value)} placeholder="Teacher, class, or school year"/></div>}>
      <div className="tabs distribution-tabs"><button className={tab==="active"?"active":""} onClick={()=>setTab("active")}>Active ({rows.filter(r=>r.status!=="RETURNED").length})</button><button className={tab==="history"?"active":""} onClick={()=>setTab("history")}>History ({rows.filter(r=>r.status==="RETURNED").length})</button></div>
      {loading?<div className="loading">Loading distributions…</div>:visible.length?<><div className="table-wrap distribution-directory"><table><thead><tr><th>Teacher / class</th><th>School year</th><th>Copies</th><th>Expected return</th><th>Status</th><th/></tr></thead><tbody>{visible.map(row=><tr key={row.id}>
        <td><strong>{row.teacherName}</strong><small>{row.gradeSection}</small></td><td>{row.schoolYear}</td><td>{row.activeCopies} out / {row.totalCopies}<small>{row.overdueCopies>0&&`${row.overdueCopies} overdue`}</small></td><td>{displayDate(row.expectedReturnDate)}</td><td><span className={`badge ${row.overdueCopies?"danger":""}`}>{row.status.replaceAll("_"," ")}</span></td><td><Button variant="ghost" onClick={()=>void openDetail(row.id)}>View</Button></td>
      </tr>)}</tbody></table></div><div className="distribution-cards">{visible.map(row=><article key={row.id}><header><div><strong>{row.teacherName}</strong><span>{row.gradeSection}</span></div><span className="badge">{row.status.replaceAll("_"," ")}</span></header><p>{row.activeCopies} of {row.totalCopies} copies outstanding</p><footer><span>S.Y. {row.schoolYear} · Due {displayDate(row.expectedReturnDate)}</span><Button variant="ghost" onClick={()=>void openDetail(row.id)}>View</Button></footer></article>)}</div></>:<Empty message={`No ${tab} class distributions found.`}/>}
    </Panel>
    {detail&&<div className="modal-backdrop" role="presentation" onMouseDown={e=>{if(e.target===e.currentTarget)setDetail(null)}}><section className="modal distribution-detail" role="dialog" aria-modal="true" aria-labelledby="distribution-detail-title">
      <button className="modal-x" onClick={()=>setDetail(null)} aria-label="Close"><X/></button>
      <h2 id="distribution-detail-title">{detail.gradeSection} · {detail.schoolYear}</h2><p className="detail-custodian">{detail.teacherName} · {detail.teacherCardNumber}</p>
      <div className="review-summary"><div><span>Status</span><strong>{detail.status.replaceAll("_"," ")}</strong></div><div><span>Distributed</span><strong>{displayDate(detail.distributedAt)}</strong></div><div><span>Return by</span><strong>{displayDate(detail.expectedReturnDate)}</strong></div><div><span>Outstanding</span><strong>{detail.activeCopies} / {detail.totalCopies}</strong></div></div>
      <div className="detail-actions"><Button variant="secondary" onClick={()=>void printBorrower()}><Printer size={16}/> Teacher card</Button>{detail.activeCopies>0&&<Button variant="danger" onClick={()=>void returnSelected(true)}>Return all</Button>}</div>
      <div className="distribution-loans">{detail.loans.map(loan=><article key={loan.id} className={loan.returnedAt?"returned":""}>
        <input type="checkbox" aria-label={`Select ${loan.accessionNumber}`} disabled={!!loan.returnedAt} checked={returnIds.includes(loan.id)} onChange={e=>setReturnIds(e.target.checked?[...returnIds,loan.id]:returnIds.filter(id=>id!==loan.id))}/>
        <div><strong>{loan.title}</strong><span>{loan.accessionNumber}</span><small>{loan.returnedAt?`Returned ${displayDate(loan.returnedAt)}`:loan.overdue?"Overdue":"On loan"}</small></div>
        <Button variant="ghost" onClick={()=>void printBook(loan.copyId)}><Printer size={16}/> Book card</Button>
      </article>)}</div>
      {returnIds.length>0&&<div className="distribution-actions"><Button onClick={()=>void returnSelected(false)}>Return selected ({returnIds.length})</Button></div>}
    </section></div>}
  </div>;
}
