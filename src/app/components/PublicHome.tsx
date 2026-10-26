import {ArrowRight, BookHeart, BookOpen, GraduationCap, Sparkles} from "lucide-react";
import type {User} from "@/app/api"; import {SCHOOL} from "@/app/types"; import type {PrintPayload} from "@/app/types";
import {AuthModal} from "@/app/components/AuthModal"; import {PublicCatalog} from "@/app/components/PublicCatalog"; import {Button} from "@/app/components/ui/Button";

export function PublicHome({onAuth, authOpen, setAuthOpen, onLogin, onPrint}: {onAuth:()=>void;authOpen:boolean;setAuthOpen:(v:boolean)=>void;onLogin:(u:User)=>void;onPrint:(p:PrintPayload)=>void}) {
  return <div className="public">
    <header className="public-nav"><div className="brand dark"><div className="brand-mark">V</div><div><strong>VFGNHS Library</strong><span>{SCHOOL}</span></div></div>
      <div className="public-nav-actions"><a href="#catalog">Browse books</a><a href="/attendance">Attendance</a><Button onClick={onAuth}>Sign in / Register</Button></div></header>
    <section className="hero"><div className="hero-copy"><span className="eyebrow"><Sparkles size={14}/> Your next story starts here</span>
      <h1>Read. Discover.<br/><em>Grow every day.</em></h1>
      <p>Explore the VFGNHS collection, find books you will love, and keep your library journey in one happy place.</p>
      <div className="hero-actions"><a className="btn btn-primary" href="#catalog">Explore books <ArrowRight size={17}/></a><Button variant="secondary" onClick={onAuth}>Create your account</Button></div>
      <div className="hero-note"><span>Free for VFGNHS learners</span><span>Easy online catalog</span></div></div>
      <div className="hero-art"><img src="/images/vfgnhs-library-hero.jpg?v=navy" alt="VFGNHS students enjoying books in a bright school library"/><div className="floating-card"><BookHeart/><span><strong>Find a book to love</strong><small>Stories, lessons and new worlds await.</small></span></div></div></section>
    <section className="library-perks" aria-label="Library benefits"><div><BookOpen/><span><strong>Explore freely</strong><small>Search every shelf from anywhere</small></span></div><div><GraduationCap/><span><strong>Learn your way</strong><small>Books for every subject and interest</small></span></div><div><BookHeart/><span><strong>Keep growing</strong><small>Track your own reading journey</small></span></div></section>
    <PublicCatalog/>
    <footer><div className="footer-brand"><span className="brand-mark">V</span><span><strong>VFGNHS Library</strong><small>A welcoming space to read, learn and belong.</small></span></div><span>© {new Date().getFullYear()} {SCHOOL}</span></footer>
    {authOpen && <AuthModal onClose={() => setAuthOpen(false)} onLogin={onLogin} onPrint={onPrint}/>}
  </div>;
}
