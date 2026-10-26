import {useCallback, useEffect, useState} from "react"; import type {FormEvent} from "react"; import {toast} from "sonner";
import {api, displayDate, json} from "@/app/api"; import type {Copy, Loan, User} from "@/app/api"; import {AutocompleteField} from "@/app/components/ui/AutocompleteField";
import {Button} from "@/app/components/ui/Button"; import {Empty} from "@/app/components/ui/Empty"; import {TextAreaField} from "@/app/components/ui/Field"; import {Panel} from "@/app/components/ui/Panel";

export function Circulation() {
  const [loans,setLoans]=useState<Loan[]>([]);const [copies,setCopies]=useState<Copy[]>([]);const [borrowers,setBorrowers]=useState<User[]>([]);
  const [formKey,setFormKey]=useState(0); const [loading,setLoading]=useState(true);
  const load=useCallback(async()=>{try{setLoading(true);const [l,c,b]=await Promise.all([api<Loan[]>("/admin/circulation"),api<Copy[]>("/admin/catalog/copies"),api<User[]>("/admin/borrowers")]);setLoans(l);setCopies(c);setBorrowers(b)}catch(e){toast.error((e as Error).message)}finally{setLoading(false)}},[]);
  useEffect(()=>{void load()},[]);
  const availableCopies=copies.filter(c=>c.status==="AVAILABLE").map(c=>({
    value:String(c.id),
    label:`${c.accessionNumber} — ${c.title}`,
    keywords:[c.accessionNumber,c.title,c.author,c.barcode,c.callNumber].filter(Boolean).join(" "),
  }));
  const activeBorrowers=borrowers.filter(b=>b.borrowerStatus==="ACTIVE"&&b.borrowerId!=null).map(b=>({
    value:String(b.borrowerId),
    label:`${b.firstName} ${b.lastName} — ${b.gradeSection||b.department||b.cardNumber||b.schoolId}`,
    keywords:[b.cardNumber,b.schoolId,b.firstName,b.lastName,`${b.firstName} ${b.lastName}`,b.gradeSection,b.department].filter(Boolean).join(" "),
  }));
  const activeCount=loans.filter(l=>!l.returnedAt).length;
  const gradeSectionOf=(loan:Loan)=>{
    const fromLoan=loan.gradeSection?.trim();
    if(fromLoan)return fromLoan;
    const borrower=borrowers.find(b=>b.borrowerId===loan.borrowerId);
    return borrower?.gradeSection?.trim()||borrower?.department?.trim()||"—";
  };
  const checkout=async(e:FormEvent<HTMLFormElement>)=>{e.preventDefault();const f=new FormData(e.currentTarget);const copyId=Number(f.get("copyId"));const borrowerId=Number(f.get("borrowerId"));if(!copyId||!borrowerId){toast.error("Choose a copy and borrower from the suggestions");return}try{await api("/admin/circulation/checkout",json("POST",{copyId,borrowerId,notes:f.get("notes")}));toast.success("Book checked out");setFormKey(k=>k+1);void load()}catch(err){toast.error((err as Error).message)}};
  const returned=async(id:number)=>{try{await api(`/admin/circulation/${id}/return`,json("POST"));toast.success("Book returned");void load()}catch(e){toast.error((e as Error).message)}};
  return <div className="page-stack"><Panel title="Check out a book"><form key={formKey} className="form-grid wide" onSubmit={checkout}>
    <AutocompleteField label="Available copy" name="copyId" required options={availableCopies} placeholder="Search accession or title"/>
    <AutocompleteField label="Borrower" name="borrowerId" required options={activeBorrowers} placeholder="Search name or card number"/>
    <TextAreaField className="span-full" label="Notes" name="notes" rows={3}/>
    <div className="form-actions"><Button>Check out</Button></div></form></Panel>
    <Panel title={`Circulation records (${loans.length})`} action={activeCount>0?<span className="badge">{activeCount} on loan</span>:undefined}>
      {loading?<div className="loading">Loading circulation…</div>:loans.length?<><div className="table-wrap circulation-directory"><table><thead><tr>
        <th>Due</th><th>Name</th><th>Grade and section</th><th>Title of books</th><th>Date borrowed</th><th>Date returned</th><th/>
      </tr></thead><tbody>{loans.map(l=><tr key={l.id}>
        <td>{displayDate(l.dueAt)}{l.overdue&&!l.returnedAt&&<small className="overdue-note">Overdue</small>}</td>
        <td><strong>{l.borrowerName}</strong><small>{l.cardNumber}</small></td>
        <td>{gradeSectionOf(l)}{l.distributionId&&<small>Class set · S.Y. {l.distributionSchoolYear}</small>}</td>
        <td><strong>{l.title}</strong><small>{l.accessionNumber}</small></td>
        <td>{displayDate(l.borrowedAt)}</td>
        <td>{l.returnedAt?displayDate(l.returnedAt):"—"}</td>
        <td>{!l.returnedAt&&<Button onClick={()=>void returned(l.id)}>Return</Button>}</td>
      </tr>)}</tbody></table></div>
      <div className="circulation-cards">{loans.map(l=><article key={l.id}>
        <div><strong>{l.borrowerName}</strong><span>Due {displayDate(l.dueAt)}</span></div>
        <p>{gradeSectionOf(l)}{l.distributionId&&<small>Class set · S.Y. {l.distributionSchoolYear}</small>}</p>
        <p className="circulation-card-title">{l.title}<small>{l.accessionNumber}</small></p>
        <footer>
          <span>Borrowed {displayDate(l.borrowedAt)}</span>
          <span>{l.returnedAt?`Returned ${displayDate(l.returnedAt)}`:l.overdue?"Overdue":"On loan"}</span>
          {!l.returnedAt&&<Button onClick={()=>void returned(l.id)}>Return</Button>}
        </footer>
      </article>)}</div></>
      :<Empty message="There are no circulation records yet."/>}
    </Panel></div>;
}
