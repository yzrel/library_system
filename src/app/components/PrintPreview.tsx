import {Printer, X} from "lucide-react"; import {QRCodeSVG} from "qrcode.react"; import {displayDate} from "@/app/api"; import type {Copy, Loan, User} from "@/app/api";
import type {PrintPayload, QrPrintPayload, SinglePrintPayload} from "@/app/types"; import {Button} from "@/app/components/ui/Button";

export function PrintPreview({payload,onClose}:{payload:PrintPayload;onClose:()=>void}) {
  const source = payload.kind === "batch" ? payload.cards : [payload];
  const cards: {item: SinglePrintPayload|QrPrintPayload;rows:Loan[]}[] = [];
  source.forEach((item) => {
    if(item.kind==="qr"){cards.push({item,rows:[]});return}
    const chunks: Loan[][] = [];
    for (let i=0;i<Math.max(item.transactions.length,1);i+=11) chunks.push(item.transactions.slice(i,i+11));
    chunks.forEach(rows=>cards.push({item,rows}));
  });
  const layout = source.every(item=>item.kind==="book") ? "book" : source.every(item=>item.kind==="borrower") ? "borrower" : source.every(item=>item.kind==="qr") ? "qr" : "mixed";
  const perPage = layout === "book" ? 3 : layout === "qr" ? 2 : 2;
  const pages = Array.from({length: Math.ceil(cards.length/perPage)}, (_,i)=>cards.slice(i*perPage,(i+1)*perPage));
  return <div className="print-overlay"><div className="print-toolbar"><Button variant="secondary" onClick={onClose}><X size={16}/> Close</Button><Button onClick={()=>window.print()}><Printer size={16}/> Print</Button></div>
    {pages.map((page,pageIndex)=><div className={`print-sheet ${layout}`} key={pageIndex}>{page.map(({item,rows},index)=>item.kind==="book"?
      <BookCard key={index} copy={item.copy} rows={rows}/>:item.kind==="borrower"?<BorrowerCard key={index} borrower={item.borrower} rows={rows}/>:<AttendanceQrCard key={index} borrower={item.borrower}/>)}</div>)}</div>;
}
function blankRows(rows:Loan[]){return [...rows,...Array(Math.max(0,11-rows.length)).fill(null)] as (Loan|null)[]}
export function BookCard({copy,rows}:{copy:Copy;rows:Loan[]}){return <div className="physical-card"><header>VFGNHS LIBRARY<br/><strong>BOOK CARD</strong></header><div className="card-meta split"><span>Call No.: <b>{copy.callNumber}</b></span><span>Acc No.: <b>{copy.accessionNumber}</b></span></div><div className="card-title">{copy.title}</div><div className="card-author">Author: {copy.author}</div><table><thead><tr><th>Date</th><th>Borrower's Name</th><th>Date Returned</th></tr></thead><tbody>{blankRows(rows).map((r,i)=><tr key={i}><td>{r?displayDate(r.borrowedAt):""}</td><td>{r?.borrowerName}</td><td>{r?displayDate(r.returnedAt):""}</td></tr>)}</tbody></table></div>}
export function BorrowerCard({borrower,rows}:{borrower:User;rows:Loan[]}){return <div className="physical-card"><header>VFGNHS LIBRARY<br/><strong>BORROWERS' CARD</strong></header><div className="borrower-lines"><span>{borrower.firstName} {borrower.lastName}<small>Name</small></span><span>{borrower.gradeSection||borrower.department}<small>Grade and Section</small></span></div><table><thead><tr><th>Date Borrowed</th><th>Title of the Book / Acc. No.</th><th>Date Returned</th></tr></thead><tbody>{blankRows(rows).map((r,i)=><tr key={i}><td>{r?displayDate(r.borrowedAt):""}</td><td>{r?`${r.title} / ${r.accessionNumber}`:""}</td><td>{r?displayDate(r.returnedAt):""}</td></tr>)}</tbody></table></div>}
export function AttendanceQrCard({borrower}:{borrower:User}){return <article className="qr-card"><header><span className="brand-mark">V</span><div><strong>VFGNHS LIBRARY</strong><small>Attendance QR Card</small></div></header><div className="qr-code-wrap"><QRCodeSVG value={borrower.cardNumber||""} size={220} level="M" marginSize={1}/></div><div className="qr-details"><h2>{borrower.firstName} {borrower.lastName}</h2><p>{borrower.gradeSection||borrower.department||borrower.role}</p><code>{borrower.cardNumber}</code><small>Present this code when entering the library.</small></div></article>}
