import {lazy, Suspense, useEffect, useState} from "react";
import {BarChart3, BookCopy, BookOpen, CheckCircle2, ClipboardList, GraduationCap, Library, LogOut, Menu, QrCode, RotateCcw, Settings, ShieldCheck, Users, X} from "lucide-react";
import {toast} from "sonner";
import {api, session, SESSION_EXPIRED, SESSION_EXPIRED_EVENT} from "@/app/api";
import type {User} from "@/app/api";
import {SCHOOL, homeView} from "@/app/types";
import type {PrintPayload, View} from "@/app/types";
import {MyLibrary} from "@/app/components/MyLibrary";
import {PrintPreview} from "@/app/components/PrintPreview";
import {PublicCatalog} from "@/app/components/PublicCatalog";
import {PublicHome} from "@/app/components/PublicHome";

const Accounts = lazy(() => import("@/app/components/Accounts").then(module => ({default: module.Accounts})));
const AttendanceKiosk = lazy(() => import("@/app/components/AttendanceKiosk").then(module => ({default: module.AttendanceKiosk})));
const AttendanceLogbook = lazy(() => import("@/app/components/AttendanceLogbook").then(module => ({default: module.AttendanceLogbook})));
const Borrowers = lazy(() => import("@/app/components/Borrowers").then(module => ({default: module.Borrowers})));
const CatalogAdmin = lazy(() => import("@/app/components/CatalogAdmin").then(module => ({default: module.CatalogAdmin})));
const Circulation = lazy(() => import("@/app/components/Circulation").then(module => ({default: module.Circulation})));
const Classes = lazy(() => import("@/app/components/Classes").then(module => ({default: module.Classes})));
const Dashboard = lazy(() => import("@/app/components/Dashboard").then(module => ({default: module.Dashboard})));
const Distribution = lazy(() => import("@/app/components/Distribution").then(module => ({default: module.Distribution})));
const Reports = lazy(() => import("@/app/components/Reports").then(module => ({default: module.Reports})));
const Requests = lazy(() => import("@/app/components/Requests").then(module => ({default: module.Requests})));
const SettingsAudit = lazy(() => import("@/app/components/SettingsAudit").then(module => ({default: module.SettingsAudit})));

const splash = <div className="splash"><Library size={58}/><strong>Opening VFGNHS Library…</strong></div>;

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(!!session.get());
  const [authOpen, setAuthOpen] = useState(false);
  const [view, setView] = useState<View>("dashboard");
  const [drawer, setDrawer] = useState(false);
  const [print, setPrint] = useState<PrintPayload | null>(null);

  useEffect(() => {
    if (!session.get()) return;
    api<User>("/auth/me").then(next => { setUser(next); setView(homeView(next.role)); }).catch(() => session.clear()).finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const expire = () => {
      toast.error(SESSION_EXPIRED, {id: SESSION_EXPIRED_EVENT});
      setUser(null); setView(homeView("ADMIN")); setPrint(null); setAuthOpen(true);
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, expire);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, expire);
  }, []);

  if (window.location.pathname === "/attendance") return <Suspense fallback={splash}><AttendanceKiosk/></Suspense>;
  if (loading) return splash;
  const signedIn = (next: User) => { setUser(next); setView(homeView(next.role)); };
  if (!user) return <><PublicHome onAuth={() => setAuthOpen(true)} authOpen={authOpen} setAuthOpen={setAuthOpen} onLogin={signedIn} onPrint={setPrint}/>{print&&<PrintPreview payload={print} onClose={()=>setPrint(null)}/>}</>;

  const admin = user.role === "ADMIN";
  const nav: {id: View; label: string; icon: typeof Library}[] = admin ? [
    {id:"dashboard",label:"Dashboard",icon:BarChart3}, {id:"catalog",label:"Catalog",icon:BookCopy},
    {id:"circulation",label:"Circulation",icon:RotateCcw}, {id:"distribution",label:"Distribution",icon:GraduationCap}, {id:"borrowers",label:"Borrowers",icon:Users},
    {id:"attendance",label:"Attendance",icon:QrCode}, {id:"reports",label:"Reports",icon:ClipboardList}, {id:"accounts",label:"Accounts",icon:ShieldCheck},
    {id:"classes",label:"Classes",icon:GraduationCap},
    {id:"requests",label:"Requests",icon:CheckCircle2}, {id:"settings",label:"Settings & Audit",icon:Settings},
  ] : [{id:"catalog",label:"Public Catalog",icon:BookOpen},{id:"my-library",label:"My Library",icon:Library}];

  const navigate = (next: View) => { setView(next); setDrawer(false); };
  const logout = () => { session.clear(); setUser(null); setView(homeView("ADMIN")); };

  return <div className="app-shell">
    <aside className={`sidebar ${drawer ? "open" : ""}`}>
      <div className="brand"><div className="brand-mark">V</div><div><strong>VFGNHS</strong><span>Library System</span></div>
        <button className="drawer-close" onClick={() => setDrawer(false)} aria-label="Close navigation"><X/></button></div>
      <div className="sidebar-shelf" aria-hidden="true"><i/><i/><i/><i/><i/></div>
      <nav>{nav.map(item => <button key={item.id} className={view===item.id ? "active":""} onClick={() => navigate(item.id)}>
        <item.icon size={19}/><span>{item.label}</span></button>)}</nav>
      <div className="sidebar-user"><small>{user.role}</small><strong>{user.firstName} {user.lastName}</strong>
        <button onClick={logout}><LogOut size={16}/> Sign out</button></div>
    </aside>
    {drawer && <button className="scrim" onClick={() => setDrawer(false)} aria-label="Close menu"/>}
    <div className="main">
      <header className="topbar"><button className="menu" onClick={() => setDrawer(true)} aria-label="Open navigation"><Menu/></button>
        <div><strong>{nav.find(n => n.id===view)?.label ?? "Library"}</strong><span>{SCHOOL}</span></div>
        <div className="topbar-welcome">Hello, {user.firstName}!</div><div className="avatar" aria-label={`${user.firstName} ${user.lastName}`}>{user.firstName[0]}{user.lastName[0]}</div></header>
      <main>
        <Suspense fallback={<div className="loading">Loading page…</div>}>
        {view==="dashboard" && admin && <Dashboard/>}
        {view==="catalog" && (admin ? <CatalogAdmin onPrint={setPrint}/> : <PublicCatalog compact user={user}/>)}
        {view==="circulation" && admin && <Circulation/>}
        {view==="distribution" && admin && <Distribution onPrint={setPrint}/>}
        {view==="borrowers" && admin && <Borrowers onPrint={setPrint}/>}
        {view==="attendance" && admin && <AttendanceLogbook/>}
        {view==="reports" && admin && <Reports/>}
        {view==="accounts" && admin && <Accounts onPrint={setPrint}/>}
        {view==="classes" && admin && <Classes/>}
        {view==="requests" && admin && <Requests/>}
        {view==="settings" && admin && <SettingsAudit/>}
        {view==="my-library" && !admin && <MyLibrary user={user} onUser={setUser} onPrint={setPrint}/>}
        </Suspense>
      </main>
    </div>
    {print && <PrintPreview payload={print} onClose={() => setPrint(null)}/>}
  </div>;
}
