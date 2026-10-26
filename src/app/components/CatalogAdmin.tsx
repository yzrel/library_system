import {useCallback, useEffect, useMemo, useRef, useState} from "react"; import type {FormEvent} from "react"; import {Filter, Pencil, Plus, Printer, ScanBarcode, Search, Trash2, X} from "lucide-react"; import {toast} from "sonner";
import {api, json} from "@/app/api"; import type {Book, BookSubject, Copy, IsbnLookupResult, Loan} from "@/app/api"; import {catalogFilterOptions, catalogSubject, filterCatalogBooks, matchCatalogSubject} from "@/app/catalogFilter"; import type {PrintPayload} from "@/app/types"; import {BookCover} from "@/app/components/BookCover";
import {Button} from "@/app/components/ui/Button"; import {Empty} from "@/app/components/ui/Empty"; import {Field, SelectField, TextAreaField} from "@/app/components/ui/Field"; import {Panel} from "@/app/components/ui/Panel";

type IntakeDraft = {
  existingBookId?: number;
  title: string; subtitle: string; author: string; isbn: string; publisher: string;
  publicationYear: string; edition: string; subject: string; description: string; coverUrl: string;
  accessionNumber: string; callNumber: string; volumeCopy: string; pages: string;
  dateReceived: string; sourceOfFund: string; cost: string; barcode: string; quantity: string;
};

const emptyIntake = (): IntakeDraft => ({
  title: "", subtitle: "", author: "", isbn: "", publisher: "", publicationYear: "",
  edition: "", subject: "", description: "", coverUrl: "", accessionNumber: "", callNumber: "",
  volumeCopy: "", pages: "", dateReceived: new Date().toISOString().slice(0,10), sourceOfFund: "",
  cost: "", barcode: "", quantity: "1",
});

type BatchCopyResponse = {copies: Copy[]; firstAccession: string; lastAccession: string};
const COPY_STATUSES = ["AVAILABLE","RESERVED","LOST","DAMAGED","WEEDED","ARCHIVED"] as const;

function accessionRange(start:string,quantity:number) {
  if(quantity<2||!start)return "";
  const match=start.match(/^(.*?)(\d+)$/);
  if(!match)return "";
  const width=match[2].length;
  const last=Number(match[2])+quantity-1;
  if(!Number.isSafeInteger(last))return "";
  return `${start} through ${match[1]}${String(last).padStart(width,"0")}`;
}

function formValue(form:FormData,name:string) {
  const value=String(form.get(name)??"").trim();
  return value||undefined;
}

function AddCopiesForm({book,suggestedAccession,onAdded}:{book:Book;suggestedAccession:string;onAdded:(result:BatchCopyResponse)=>void}) {
  const lastCopy=book.copies.at(-1);
  const [quantity,setQuantity]=useState(1);
  const [startingAccession,setStartingAccession]=useState("");
  const [saving,setSaving]=useState(false);
  const preview=accessionRange(startingAccession||suggestedAccession,quantity);

  const submit=async(e:FormEvent<HTMLFormElement>)=>{
    e.preventDefault();
    const form=new FormData(e.currentTarget);
    const common={
      callNumber:formValue(form,"callNumber"),
      volumeCopy:formValue(form,"volumeCopy"),
      pages:formValue(form,"pages"),
      dateReceived:formValue(form,"dateReceived"),
      sourceOfFund:formValue(form,"sourceOfFund"),
      cost:formValue(form,"cost")?Number(formValue(form,"cost")):undefined,
      remarks:formValue(form,"remarks"),
      barcode:quantity===1?formValue(form,"barcode"):undefined,
    };
    setSaving(true);
    try{
      let result:BatchCopyResponse;
      if(quantity===1){
        const copy=await api<Copy>(`/admin/catalog/books/${book.id}/copies`,json("POST",{
          ...common,accessionNumber:startingAccession.trim()||undefined,status:"AVAILABLE",
        }));
        result={copies:[copy],firstAccession:copy.accessionNumber,lastAccession:copy.accessionNumber};
      }else{
        result=await api<BatchCopyResponse>(`/admin/catalog/books/${book.id}/copies/batch`,json("POST",{
          ...common,quantity,startingAccessionNumber:startingAccession.trim()||undefined,
        }));
      }
      toast.success(quantity===1?`Added copy ${result.firstAccession}`:`Added ${quantity} copies (${result.firstAccession}–${result.lastAccession})`);
      setQuantity(1);setStartingAccession("");
      onAdded(result);
    }catch(err){toast.error((err as Error).message)}
    finally{setSaving(false)}
  };

  return <details className="add-copies">
    <summary>Add copies</summary>
    <form className="compact-form" onSubmit={e=>void submit(e)}>
      <div className="add-copies-primary">
        <Field label="Quantity" type="number" min={1} max={200} required value={quantity} onChange={e=>setQuantity(Math.max(1,Math.min(200,Number(e.target.value)||1)))}/>
        <Field label={quantity===1?"Accession (auto)":"Starting accession (auto)"} value={startingAccession} onChange={e=>setStartingAccession(e.target.value)} placeholder={suggestedAccession||"Assigned when saved"} autoComplete="off"/>
        <Field label="Call number" name="callNumber" defaultValue={lastCopy?.callNumber??""}/>
      </div>
      {preview&&<p className="accession-preview">Will create {preview}</p>}
      <details className="copy-more-details">
        <summary>More details</summary>
        <div className="add-copies-more">
          <Field label="Date received" name="dateReceived" type="date" defaultValue={new Date().toISOString().slice(0,10)}/>
          <Field label="Source of fund" name="sourceOfFund" defaultValue={lastCopy?.sourceOfFund??""}/>
          <Field label="Cost" name="cost" type="number" step="0.01" min="0" defaultValue={lastCopy?.cost??""}/>
          <Field label="Pages" name="pages" defaultValue={lastCopy?.pages??""}/>
          <Field label="Volume / copy" name="volumeCopy" defaultValue={lastCopy?.volumeCopy??""}/>
          {quantity===1&&<Field label="Library barcode (optional)" name="barcode"/>}
        </div>
      </details>
      <div className="form-actions"><Button disabled={saving}>{saving?"Saving…":quantity===1?"Add copy":`Add ${quantity} copies`}</Button></div>
    </form>
  </details>;
}

function BookEditor({book,subjects,onClose,onSaved}:{book:Book;subjects:string[];onClose:()=>void;onSaved:()=>void}) {
  const [title,setTitle]=useState(book.title);
  const [author,setAuthor]=useState(book.author??"");
  const [isbn,setIsbn]=useState(book.isbn??"");
  const [subject,setSubject]=useState(book.subject??"");
  const [publisher,setPublisher]=useState(book.publisher??"");
  const [publicationYear,setPublicationYear]=useState(book.publicationYear!=null?String(book.publicationYear):"");
  const [edition,setEdition]=useState(book.edition??"");
  const [coverUrl,setCoverUrl]=useState(book.coverUrl??"");
  const [description,setDescription]=useState(book.description??"");
  const [saving,setSaving]=useState(false);
  const save=async(e:FormEvent)=>{e.preventDefault();if(!title.trim()){toast.error("Title is required");return}
    setSaving(true);
    try{
      await api(`/admin/catalog/books/${book.id}`,json("PUT",{
        title:title.trim(),author:author||undefined,isbn:isbn||undefined,subject:subject||undefined,
        publisher:publisher||undefined,publicationYear:publicationYear?Number(publicationYear):undefined,
        edition:edition||undefined,coverUrl:coverUrl||undefined,description:description||undefined,
      }));
      toast.success("Book details updated");onSaved();
    }catch(err){toast.error((err as Error).message)}
    finally{setSaving(false)}
  };
  return <div className="modal-backdrop" onClick={e=>{if(e.target===e.currentTarget)onClose()}}>
    <div className="modal catalog-modal" role="dialog" aria-modal="true" aria-labelledby="edit-book-title">
      <button className="modal-x" onClick={onClose} aria-label="Close book editor"><X/></button>
      <div className="account-modal-head"><BookCover book={book}/><div><h2 id="edit-book-title">Edit book</h2><p>Update the bibliographic record. Physical copies stay attached to this title.</p></div></div>
      <form className="form-grid wide compact-form" onSubmit={e=>void save(e)}>
        <Field className="span-2" label="Title" required value={title} onChange={e=>setTitle(e.target.value)}/>
        <Field label="Author" value={author} onChange={e=>setAuthor(e.target.value)}/>
        <Field label="ISBN" value={isbn} onChange={e=>setIsbn(e.target.value)}/>
        <SelectField label="Subject" value={subject} onChange={e=>setSubject(e.target.value)}>
          <option value="">Select subject</option>
          {subjects.map(item=><option key={item} value={item}>{item}</option>)}
          {subject&&!subjects.some(item=>item.toLowerCase()===subject.toLowerCase())&&<option value={subject}>{subject}</option>}
        </SelectField>
        <Field label="Publisher" value={publisher} onChange={e=>setPublisher(e.target.value)}/>
        <Field label="Publication year" type="number" value={publicationYear} onChange={e=>setPublicationYear(e.target.value)}/>
        <Field label="Edition" value={edition} onChange={e=>setEdition(e.target.value)}/>
        <Field className="span-2" label="Cover image URL" type="url" value={coverUrl} onChange={e=>setCoverUrl(e.target.value)}/>
        <TextAreaField className="span-full" label="Short description" value={description} onChange={e=>setDescription(e.target.value)}/>
        <div className="form-actions"><Button type="button" variant="ghost" onClick={onClose}>Cancel</Button><Button disabled={saving}>{saving?"Saving…":"Save book"}</Button></div>
      </form>
    </div>
  </div>;
}

function CopyEditor({copy,onClose,onSaved}:{copy:Copy;onClose:()=>void;onSaved:()=>void}) {
  const onLoan=copy.status==="ON_LOAN";
  const [accessionNumber,setAccessionNumber]=useState(copy.accessionNumber);
  const [callNumber,setCallNumber]=useState(copy.callNumber??"");
  const [volumeCopy,setVolumeCopy]=useState(copy.volumeCopy??"");
  const [pages,setPages]=useState(copy.pages??"");
  const [dateReceived,setDateReceived]=useState(copy.dateReceived??"");
  const [sourceOfFund,setSourceOfFund]=useState(copy.sourceOfFund??"");
  const [cost,setCost]=useState(copy.cost!=null?String(copy.cost):"");
  const [barcode,setBarcode]=useState(copy.barcode??"");
  const [status,setStatus]=useState(copy.status);
  const [remarks,setRemarks]=useState(copy.remarks??"");
  const [saving,setSaving]=useState(false);
  const save=async(e:FormEvent)=>{e.preventDefault();if(!accessionNumber.trim()){toast.error("Accession number is required");return}
    setSaving(true);
    try{
      await api(`/admin/catalog/copies/${copy.id}`,json("PUT",{
        accessionNumber:accessionNumber.trim(),callNumber:callNumber||undefined,volumeCopy:volumeCopy||undefined,
        pages:pages||undefined,dateReceived:dateReceived||undefined,sourceOfFund:sourceOfFund||undefined,
        cost:cost?Number(cost):undefined,barcode:barcode||undefined,status,remarks:remarks||undefined,
      }));
      toast.success(`Copy ${accessionNumber.trim()} updated`);onSaved();
    }catch(err){toast.error((err as Error).message)}
    finally{setSaving(false)}
  };
  return <div className="modal-backdrop" onClick={e=>{if(e.target===e.currentTarget)onClose()}}>
    <div className="modal catalog-modal" role="dialog" aria-modal="true" aria-labelledby="edit-copy-title">
      <button className="modal-x" onClick={onClose} aria-label="Close copy editor"><X/></button>
      <div className="account-modal-head"><div><h2 id="edit-copy-title">Edit copy {copy.accessionNumber}</h2><p>{copy.title}{onLoan?" · This copy is on loan. Return it in Circulation before marking it available.":""}</p></div></div>
      <form className="form-grid wide compact-form" onSubmit={e=>void save(e)}>
        <Field label="Accession number" required value={accessionNumber} onChange={e=>setAccessionNumber(e.target.value)} autoComplete="off"/>
        <Field label="Call number" value={callNumber} onChange={e=>setCallNumber(e.target.value)}/>
        <SelectField label="Status" value={status} onChange={e=>setStatus(e.target.value)}>
          {onLoan&&<option value="ON_LOAN">ON LOAN</option>}
          {COPY_STATUSES.filter(item=>!(onLoan&&item==="AVAILABLE")).map(item=><option key={item} value={item}>{item.replaceAll("_"," ")}</option>)}
        </SelectField>
        <Field label="Volume / copy" value={volumeCopy} onChange={e=>setVolumeCopy(e.target.value)}/>
        <Field label="Pages" value={pages} onChange={e=>setPages(e.target.value)}/>
        <Field label="Date received" type="date" value={dateReceived} onChange={e=>setDateReceived(e.target.value)}/>
        <Field label="Source of fund" value={sourceOfFund} onChange={e=>setSourceOfFund(e.target.value)}/>
        <Field label="Cost" type="number" step="0.01" min="0" value={cost} onChange={e=>setCost(e.target.value)}/>
        <Field label="Library barcode" value={barcode} onChange={e=>setBarcode(e.target.value)}/>
        <TextAreaField className="span-full" label="Remarks" value={remarks} onChange={e=>setRemarks(e.target.value)}/>
        <div className="form-actions"><Button type="button" variant="ghost" onClick={onClose}>Cancel</Button><Button disabled={saving}>{saving?"Saving…":"Save copy"}</Button></div>
      </form>
    </div>
  </div>;
}

function intakeStatusMessage(result: IsbnLookupResult) {
  if (result.source === "LOCAL") return `Already in catalog — adding another physical copy of “${result.title}”. Accession is prefilled; edit if needed, then save.`;
  if (result.source === "OPEN_LIBRARY") return "Details found via Open Library. Accession is prefilled; review and save.";
  if (result.source === "GOOGLE_BOOKS") return "Details found via Google Books. Accession is prefilled; review and save.";
  return "No online details found. Fill in the title, review the prefilled accession number, then save.";
}

export function CatalogAdmin({onPrint}:{onPrint:(p:PrintPayload)=>void}) {
  const [books,setBooks]=useState<Book[]>([]); const [q,setQ]=useState(""); const [subject,setSubject]=useState(""); const [show,setShow]=useState(false);
  const [subjectOptions,setSubjectOptions]=useState<BookSubject[]>([]);
  const activeSubjectNames=useMemo(()=>subjectOptions.filter(item=>item.active).map(item=>item.name),[subjectOptions]);
  const subjects=useMemo(()=>catalogFilterOptions(activeSubjectNames,books),[activeSubjectNames,books]);
  const visibleBooks=useMemo(()=>filterCatalogBooks(books,subject),[books,subject]);
  const [showIntake,setShowIntake]=useState(false); const [selected,setSelected]=useState<number[]>([]);
  const [suggestedAccession,setSuggestedAccession]=useState(""); const [newCopies,setNewCopies]=useState<Copy[]>([]);
  const [editingBook,setEditingBook]=useState<Book|null>(null); const [editingCopy,setEditingCopy]=useState<Copy|null>(null);
  const [isbnQuery,setIsbnQuery]=useState(""); const [lookingUp,setLookingUp]=useState(false);
  const [savingIntake,setSavingIntake]=useState(false); const [lookupNote,setLookupNote]=useState("");
  const [draft,setDraft]=useState<IntakeDraft>(emptyIntake);
  const isbnInputRef=useRef<HTMLInputElement>(null);
  const accessionRef=useRef<HTMLInputElement>(null);

  const load=useCallback(()=>api<Book[]>(`/admin/catalog/books?q=${encodeURIComponent(q)}`).then(setBooks).catch(e=>toast.error(e.message)),[q]);
  const loadSubjects=useCallback(()=>api<BookSubject[]>("/admin/subjects").then(setSubjectOptions).catch(e=>toast.error(e.message)),[]);
  const loadNextAccession=useCallback(()=>api<{accessionNumber:string}>("/admin/catalog/next-accession").then(result=>setSuggestedAccession(result.accessionNumber)).catch(e=>toast.error(e.message)),[]);
  useEffect(()=>{void load();void loadSubjects();void loadNextAccession()},[]);
  useEffect(()=>{if(showIntake) isbnInputRef.current?.focus()},[showIntake]);

  const create=async(e:FormEvent<HTMLFormElement>)=>{e.preventDefault();const f=new FormData(e.currentTarget);try{await api("/admin/catalog/books",json("POST",Object.fromEntries(f)));toast.success("Book added");setShow(false);void load()}catch(err){toast.error((err as Error).message)}};
  const printCard=async(id:number)=>{try{const data=await api<{copy:Copy;transactions:Loan[]}>(`/admin/print/book-card/${id}`);onPrint({kind:"book",...data})}catch(e){toast.error((e as Error).message)}};
  const printSelected=async()=>{try{const cards=await Promise.all(selected.map(async id=>({kind:"book" as const,...await api<{copy:Copy;transactions:Loan[]}>(`/admin/print/book-card/${id}`)})));onPrint({kind:"batch",cards})}catch(e){toast.error((e as Error).message)}};
  const printNewCopies=()=>onPrint({kind:"batch",cards:newCopies.map(copy=>({kind:"book",copy,transactions:[]}))});
  const deleteCopy=async(copy:Copy)=>{
    if(!window.confirm(`Delete copy ${copy.accessionNumber}? This cannot be undone.`))return;
    try{
      await api(`/admin/catalog/copies/${copy.id}`,{method:"DELETE"});
      toast.success(`Deleted copy ${copy.accessionNumber}`);
      setSelected(ids=>ids.filter(id=>id!==copy.id));
      setNewCopies(items=>items.filter(item=>item.id!==copy.id));
      if(editingCopy?.id===copy.id)setEditingCopy(null);
      void load();
    }catch(e){toast.error((e as Error).message)}
  };
  const copiesAdded=(result:BatchCopyResponse)=>{
    setNewCopies(result.copies);setSelected(result.copies.map(copy=>copy.id));
    void load();void loadNextAccession();
  };

  const resetIntake=()=>{setDraft(emptyIntake());setIsbnQuery("");setLookupNote("");setTimeout(()=>isbnInputRef.current?.focus(),0)};
  const setDraftField=(key:keyof IntakeDraft,value:string)=>setDraft(prev=>({...prev,[key]:value}));

  const lookupIsbn=async(raw?:string)=>{
    const value=(raw??isbnQuery).trim();
    if(!value){toast.error("Scan or type an ISBN first");return}
    setLookingUp(true);
    try{
      const result=await api<IsbnLookupResult>(`/admin/catalog/isbn-lookup?isbn=${encodeURIComponent(value)}`);
      setIsbnQuery(result.isbn);
      setLookupNote(intakeStatusMessage(result));
      setDraft({
        ...emptyIntake(),
        existingBookId:result.existingBookId??undefined,
        title:result.title??"",
        subtitle:result.subtitle??"",
        author:result.author??"",
        isbn:result.isbn??value,
        publisher:result.publisher??"",
        publicationYear:result.publicationYear!=null?String(result.publicationYear):"",
        edition:result.edition??"",
        subject:result.source==="LOCAL"?result.subject??"":matchCatalogSubject(result.subject,activeSubjectNames),
        description:result.description??"",
        coverUrl:result.coverUrl??"",
        accessionNumber:result.suggestedAccessionNumber??"",
      });
      setTimeout(()=>accessionRef.current?.select(),0);
      if(result.source==="LOCAL") toast.success("Title already in catalog");
      else if(result.found) toast.success("Book details loaded");
      else toast.message("ISBN accepted — enter details manually");
    }catch(err){toast.error((err as Error).message)}
    finally{setLookingUp(false)}
  };

  const saveIntake=async(e:FormEvent<HTMLFormElement>)=>{
    e.preventDefault();
    if(!draft.title.trim()){toast.error("Title is required");return}
    setSavingIntake(true);
    try{
      const body={
        existingBookId:draft.existingBookId,
        title:draft.title.trim(),
        subtitle:draft.subtitle||undefined,
        author:draft.author||undefined,
        isbn:draft.isbn||undefined,
        publisher:draft.publisher||undefined,
        publicationYear:draft.publicationYear?Number(draft.publicationYear):undefined,
        edition:draft.edition||undefined,
        subject:draft.subject||undefined,
        description:draft.description||undefined,
        coverUrl:draft.coverUrl||undefined,
        accessionNumber:draft.accessionNumber.trim(),
        callNumber:draft.callNumber||undefined,
        volumeCopy:draft.volumeCopy||undefined,
        pages:draft.pages||undefined,
        dateReceived:draft.dateReceived||undefined,
        sourceOfFund:draft.sourceOfFund||undefined,
        cost:draft.cost?Number(draft.cost):undefined,
        barcode:Number(draft.quantity)<=1?draft.barcode||undefined:undefined,
        status:"AVAILABLE",
        quantity:Number(draft.quantity)||1,
      };
      const result=await api<Book&{createdBook:boolean;copy:Copy;createdCopies:Copy[];firstAccession:string;lastAccession:string}>("/admin/catalog/intake",json("POST",body));
      const quantity=result.createdCopies.length;
      const added=quantity===1?`copy ${result.firstAccession}`:`${quantity} copies (${result.firstAccession}–${result.lastAccession})`;
      toast.success(result.createdBook?`Added “${result.title}” and ${added}`:`Added ${added} to “${result.title}”`);
      copiesAdded({...result,copies:result.createdCopies});
      resetIntake();
    }catch(err){toast.error((err as Error).message)}
    finally{setSavingIntake(false)}
  };

  return <div className="page-stack"><div className="page-actions"><div className="catalog-tools"><form className="inline-search" onSubmit={e=>{e.preventDefault();void load()}}><Search/><input value={q} onChange={e=>setQ(e.target.value)} placeholder="Search catalog"/><Button>Search</Button></form>
      <label className="catalog-subject-filter"><Filter size={17} aria-hidden="true"/><select value={subject} onChange={e=>setSubject(e.target.value)} aria-label="Filter books by subject">
        <option value="">All subjects</option>
        {subjects.map(item=><option key={item} value={item}>{item}</option>)}
      </select></label></div>
    <div className="action-group">{newCopies.length>0&&<Button variant="secondary" onClick={printNewCopies}><Printer size={17}/> Print new cards ({newCopies.length})</Button>}
      {selected.length>0&&<Button variant="secondary" onClick={()=>void printSelected()}><Printer size={17}/> Print selected ({selected.length})</Button>}
      <Button variant="secondary" onClick={()=>{setShowIntake(v=>!v);setShow(false)}}><ScanBarcode size={17}/> Scan ISBN</Button>
      <Button onClick={()=>{setShow(v=>!v);setShowIntake(false)}}><Plus size={17}/> Add book</Button></div></div>
    {showIntake&&<Panel title="Scan ISBN to catalog" action={<Button variant="ghost" type="button" onClick={resetIntake}>Clear</Button>}>
      <p className="muted intake-hint">Scan with a USB barcode reader or type the ISBN, then press Enter. Accession number is prefilled automatically (editable) before saving.</p>
      <form className="inline-search intake-isbn-bar" onSubmit={e=>{e.preventDefault();void lookupIsbn()}}>
        <ScanBarcode size={18}/><input ref={isbnInputRef} value={isbnQuery} onChange={e=>setIsbnQuery(e.target.value)} placeholder="Scan or type ISBN…" inputMode="numeric" autoComplete="off" aria-label="ISBN barcode"/>
        <Button disabled={lookingUp}>{lookingUp?"Looking up…":"Look up"}</Button>
      </form>
      {lookupNote&&<p className="intake-status">{lookupNote}</p>}
      {(draft.isbn||draft.title)&&<form className="form-grid wide compact-form intake-form" onSubmit={e=>void saveIntake(e)}>
        <Field className="span-2" label="Title" required value={draft.title} onChange={e=>setDraftField("title",e.target.value)} disabled={!!draft.existingBookId}/>
        <Field label="Author" value={draft.author} onChange={e=>setDraftField("author",e.target.value)} disabled={!!draft.existingBookId}/>
        <Field label="ISBN" value={draft.isbn} onChange={e=>setDraftField("isbn",e.target.value)} disabled={!!draft.existingBookId}/>
        <Field label="Publisher" value={draft.publisher} onChange={e=>setDraftField("publisher",e.target.value)} disabled={!!draft.existingBookId}/>
        <Field label="Publication year" type="number" value={draft.publicationYear} onChange={e=>setDraftField("publicationYear",e.target.value)} disabled={!!draft.existingBookId}/>
        <SelectField label="Subject" value={draft.subject} onChange={e=>setDraftField("subject",e.target.value)} disabled={!!draft.existingBookId}>
          <option value="">Select subject</option>
          {activeSubjectNames.map(item=><option key={item} value={item}>{item}</option>)}
          {draft.subject&&!activeSubjectNames.some(item=>item.toLowerCase()===draft.subject.toLowerCase())&&<option value={draft.subject}>{draft.subject}</option>}
        </SelectField>
        <Field label="Edition" value={draft.edition} onChange={e=>setDraftField("edition",e.target.value)} disabled={!!draft.existingBookId}/>
        <Field className="span-2" label="Cover image URL" type="url" value={draft.coverUrl} onChange={e=>setDraftField("coverUrl",e.target.value)} disabled={!!draft.existingBookId}/>
        <TextAreaField className="span-full" label="Short description" value={draft.description} onChange={e=>setDraftField("description",e.target.value)} disabled={!!draft.existingBookId}/>
        <Field label="Quantity" type="number" min={1} max={200} required value={draft.quantity} onChange={e=>setDraftField("quantity",e.target.value)}/>
        <label className="field"><span>{Number(draft.quantity)>1?"Starting accession number (auto)":"Accession number (auto)"}</span><input ref={accessionRef} value={draft.accessionNumber} onChange={e=>setDraftField("accessionNumber",e.target.value)} autoComplete="off" placeholder="Auto-assigned if blank"/></label>
        <Field label="Call number" value={draft.callNumber} onChange={e=>setDraftField("callNumber",e.target.value)}/>
        <Field label="Volume / copy" value={draft.volumeCopy} onChange={e=>setDraftField("volumeCopy",e.target.value)}/>
        <Field label="Pages" value={draft.pages} onChange={e=>setDraftField("pages",e.target.value)}/>
        <Field label="Date received" type="date" value={draft.dateReceived} onChange={e=>setDraftField("dateReceived",e.target.value)}/>
        <Field label="Source of fund" value={draft.sourceOfFund} onChange={e=>setDraftField("sourceOfFund",e.target.value)}/>
        <Field label="Cost" type="number" step="0.01" value={draft.cost} onChange={e=>setDraftField("cost",e.target.value)}/>
        {Number(draft.quantity)<=1&&<Field label="Library barcode (optional)" value={draft.barcode} onChange={e=>setDraftField("barcode",e.target.value)}/>}
        {accessionRange(draft.accessionNumber,Number(draft.quantity))&&<p className="accession-preview span-full">Will create {accessionRange(draft.accessionNumber,Number(draft.quantity))}</p>}
        <div className="form-actions"><Button disabled={savingIntake}>{savingIntake?"Saving…":draft.existingBookId?"Add physical copy":"Add book and copy"}</Button></div>
      </form>}
    </Panel>}
    {show&&<Panel title="New bibliographic record"><form className="form-grid wide" onSubmit={create}>
      <Field className="span-2" label="Title" name="title" required/>
      <Field label="Author" name="author"/>
      <Field label="ISBN" name="isbn"/>
      <SelectField label="Subject" name="subject"><option value="">Select subject</option>{activeSubjectNames.map(item=><option key={item} value={item}>{item}</option>)}</SelectField>
      <Field label="Publisher" name="publisher"/>
      <Field label="Publication year" name="publicationYear" type="number"/>
      <Field label="Edition" name="edition"/>
      <Field className="span-2" label="Cover image URL" name="coverUrl" type="url" placeholder="https://…"/>
      <TextAreaField className="span-full" label="Short description" name="description"/>
      <div className="form-actions"><Button>Add book</Button></div>
    </form></Panel>}
    {books.length===0?<Empty message={q.trim()?"No books match this search. Try another title, author, or accession number.":"No catalog records yet. Add the first book."}/>:visibleBooks.length===0?<Empty message="No books match this subject. Choose another subject or show all."/>:<div className="catalog-list">{visibleBooks.map(b=><Panel key={b.id} title={b.title} action={<div className="catalog-panel-actions"><span className="badge">{b.availableCopies}/{b.totalCopies} available</span><button type="button" className="catalog-icon-btn" aria-label={`Edit book ${b.title}`} title="Edit book" onClick={()=>setEditingBook(b)}><Pencil size={16}/></button></div>}>
      <div className="catalog-record"><BookCover book={b}/><div><span className="subject">{catalogSubject(b)}</span><p className="muted">{b.author||"No author"} · {b.publisher||"No publisher"} {b.publicationYear||""}</p>{b.description&&<p className="book-description">{b.description}</p>}</div></div>
      <div className="copy-list">{b.copies.map(c=><div className="copy-row" key={c.id}><label className="select-row"><input type="checkbox" checked={selected.includes(c.id)} onChange={e=>setSelected(e.target.checked?[...selected,c.id]:selected.filter(id=>id!==c.id))}/><div><strong>{c.accessionNumber}</strong><span>{c.callNumber||"No call number"} · {c.status.replaceAll("_"," ")}</span></div></label><div className="copy-actions"><button type="button" className="catalog-icon-btn" aria-label={`Edit copy ${c.accessionNumber}`} title="Edit copy" onClick={()=>setEditingCopy(c)}><Pencil size={16}/></button><button type="button" className="catalog-icon-btn" aria-label={`Print book card for ${c.accessionNumber}`} title="Print book card" onClick={()=>void printCard(c.id)}><Printer size={16}/></button><button type="button" className="catalog-icon-btn catalog-icon-btn-danger" aria-label={`Delete copy ${c.accessionNumber}`} title="Delete copy" onClick={()=>void deleteCopy(c)}><Trash2 size={16}/></button></div></div>)}</div>
      <AddCopiesForm book={b} suggestedAccession={suggestedAccession} onAdded={copiesAdded}/>
    </Panel>)}</div>}
    {editingBook&&<BookEditor book={editingBook} subjects={activeSubjectNames} onClose={()=>setEditingBook(null)} onSaved={()=>{setEditingBook(null);void load()}}/>}
    {editingCopy&&<CopyEditor copy={editingCopy} onClose={()=>setEditingCopy(null)} onSaved={()=>{setEditingCopy(null);void load()}}/>}
    </div>;
}
