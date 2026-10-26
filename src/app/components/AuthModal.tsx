import {useEffect, useState} from "react"; import type {FormEvent} from "react"; import {Library, X} from "lucide-react"; import {toast} from "sonner";
import {api, json, PASSWORD_HINT, PASSWORD_PATTERN, session} from "@/app/api"; import type {ClassSection, Role, User} from "@/app/api"; import type {PrintPayload} from "@/app/types";
import {Button} from "@/app/components/ui/Button"; import {Field, SelectField} from "@/app/components/ui/Field";

export function AuthModal({onClose,onLogin,onPrint}: {onClose:()=>void;onLogin:(u:User)=>void;onPrint:(p:PrintPayload)=>void}) {
  const [mode,setMode]=useState<"login"|"register">("login"); const [busy,setBusy]=useState(false);
  const [registerRole,setRegisterRole]=useState<Role>("STUDENT");const [classOptions,setClassOptions]=useState<ClassSection[]>([]);
  useEffect(()=>{api<ClassSection[]>("/classes").then(setClassOptions).catch(()=>setClassOptions([]))},[]);
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setBusy(true); const form = new FormData(event.currentTarget);
    try {
      const body = Object.fromEntries(form.entries());
      const result = await api<{token:string;user:User}>(`/auth/${mode}`,json("POST",body));
      session.set(result.token);
      if (mode==="register") onPrint({kind:"qr",borrower:result.user});
      onLogin(result.user); onClose(); toast.success(mode==="register"?"Account created — your attendance QR is ready to print":"Welcome to the VFGNHS Library");
    } catch(e) { toast.error((e as Error).message); } finally { setBusy(false); }
  };
  return <div className="modal-backdrop"><div className="modal auth-modal" role="dialog" aria-modal="true" aria-labelledby="auth-title"><button className="modal-x" onClick={onClose} aria-label="Close sign in"><X/></button>
    <div className="modal-brand"><Library/><h2 id="auth-title">{mode==="login"?"Welcome back":"Create library account"}</h2>
      <p>{mode==="login"?"Sign in to manage your borrowing.":"Student and teacher accounts activate immediately."}</p></div>
    <div className="tabs"><button className={mode==="login"?"active":""} onClick={()=>setMode("login")}>Sign in</button>
      <button className={mode==="register"?"active":""} onClick={()=>setMode("register")}>Register</button></div>
    <form onSubmit={submit} className="form-grid">
      {mode==="register" && <><Field label="First name" name="firstName" required/><Field label="Last name" name="lastName" required/>
        <SelectField label="Account type" name="role" required value={registerRole} onChange={e=>setRegisterRole(e.target.value as Role)}><option value="STUDENT">Student</option><option value="TEACHER">Teacher</option></SelectField>
        <Field label="School ID / LRN / Employee ID" name="schoolId" required/>
        {registerRole==="STUDENT"
          ?<SelectField label="Grade and section" name="classSectionId" required><option value="">Select your class</option>{classOptions.map(item=><option key={item.id} value={item.id}>{item.label}</option>)}</SelectField>
          :<Field label="Department" name="department" required/>}</>}
      <Field label={mode==="login"?"Username or email":"Email"} name="email" type={mode==="login"?"text":"email"} autoComplete={mode==="login"?"username":"email"} required/>
      <Field label="Password" name="password" type="password" minLength={8} maxLength={72} pattern={PASSWORD_PATTERN} title={PASSWORD_HINT} hint={PASSWORD_HINT} required/>
      <div className="form-actions"><Button disabled={busy} type="submit">{busy?"Please wait…":mode==="login"?"Sign in":"Create account"}</Button></div>
    </form></div></div>;
}
