import {useCallback, useEffect, useState} from "react"; import {BookOpen, Search} from "lucide-react"; import {toast} from "sonner";
import {api, json} from "@/app/api"; import type {Book, User} from "@/app/api"; import {BookCover} from "@/app/components/BookCover";
import {Button} from "@/app/components/ui/Button"; import {Empty} from "@/app/components/ui/Empty";

export function PublicCatalog({compact=false,user}:{compact?:boolean;user?:User}) {
  const [q,setQ]=useState(""); const [books,setBooks]=useState<Book[]>([]); const [loading,setLoading]=useState(true);
  const [reserving,setReserving]=useState<number|null>(null);
  const load=useCallback(async()=>{setLoading(true);try{setBooks(await api<Book[]>(`/opac/books?q=${encodeURIComponent(q)}`));}catch(e){toast.error((e as Error).message)}finally{setLoading(false)}},[q]);
  useEffect(()=>{void load()},[]);
  const reserve=async(bookId:number)=>{try{setReserving(bookId);await api("/portal/reservations",json("POST",{bookId}));toast.success("Reservation requested")}catch(e){toast.error((e as Error).message)}finally{setReserving(null)}};
  return <section id={compact?undefined:"catalog"} className={compact?"catalog-page":"opac-section"}><div className="section-title"><span className="eyebrow"><BookOpen size={14}/> Online Public Access Catalog</span>
    <h2>Find your next great read</h2><p>Search by title, author, subject, call number, ISBN, or accession number.</p></div>
    <form className="searchbar" onSubmit={e=>{e.preventDefault();void load()}}><Search/><input value={q} onChange={e=>setQ(e.target.value)} placeholder="Search the library catalog…"/><Button>Search</Button></form>
    {loading?<div className="loading">Searching collection…</div>:books.length===0?<Empty message="No books found. Try another search."/>:
      <div className="book-grid">{books.map(book=><article className="book-card" key={book.id}>
        <BookCover book={book}/><div className="book-info"><span className="subject">{book.subject||"General Collection"}</span>
        <h3>{book.title}</h3><p>{book.author||"Author not recorded"}</p>
        <div className="availability"><span className={book.availableCopies?"available":"unavailable"}>{book.availableCopies} available</span><small>{book.totalCopies} total copies</small></div>
        {user&&<Button variant="secondary" disabled={reserving===book.id} onClick={()=>void reserve(book.id)}>{reserving===book.id?"Requesting…":"Reserve"}</Button>}</div>
      </article>)}</div>}
  </section>;
}
