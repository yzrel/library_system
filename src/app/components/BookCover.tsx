import {useState} from "react";
import {BookOpen} from "lucide-react";
import type {Book} from "@/app/api";

export function BookCover({book, featured=false}: {book: Pick<Book,"title"|"author"|"coverUrl">; featured?: boolean}) {
  const [failed,setFailed]=useState(false);
  const initials=book.title.split(/\s+/).filter(Boolean).slice(0,3).map(word=>word[0]).join("").toUpperCase();
  const tone=[...book.title].reduce((sum,char)=>sum+char.charCodeAt(0),0)%5;
  return <div className={`book-cover ${featured?"featured":""} cover-tone-${tone}`}>
    {book.coverUrl&&!failed
      ? <img src={book.coverUrl} alt={`Cover of ${book.title}`} loading="lazy" onError={()=>setFailed(true)}/>
      : <div className="cover-fallback" role="img" aria-label={`Illustrated cover for ${book.title}`}>
          <span className="cover-kicker">VFGNHS Library</span><BookOpen aria-hidden="true"/>
          <strong>{initials||"BOOK"}</strong><small>{book.title}</small>
        </div>}
  </div>;
}
