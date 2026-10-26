import {useState} from "react";
import type {InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes} from "react";
import {Eye, EyeOff} from "lucide-react";

type FieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, "className"> & {label: string; hint?: string; className?: string};
export function Field({label, hint, type, className = "", ...props}: FieldProps) {
  const [visible, setVisible] = useState(false);
  if (type !== "password") {
    return <label className={`field ${className}`}><span>{label}</span><input type={type} {...props}/>{hint&&<small>{hint}</small>}</label>;
  }
  return <label className={`field ${className}`}><span>{label}</span>
    <div className="password-field">
      <input {...props} type={visible ? "text" : "password"}/>
      <button type="button" className="password-toggle" onClick={() => setVisible((v) => !v)}
        onMouseDown={(e) => e.preventDefault()} aria-label={visible ? "Hide password" : "Show password"}
        aria-pressed={visible}>{visible ? <EyeOff size={18} aria-hidden="true"/> : <Eye size={18} aria-hidden="true"/>}</button>
    </div>
    {hint&&<small>{hint}</small>}
  </label>;
}
type SelectFieldProps = Omit<SelectHTMLAttributes<HTMLSelectElement>, "className"> & {label: string; children: ReactNode; className?: string};
export function SelectField({label, children, className = "", ...props}: SelectFieldProps) {
  return <label className={`field ${className}`}><span>{label}</span><select {...props}>{children}</select></label>;
}
type TextAreaFieldProps = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "className"> & {label: string; className?: string};
export function TextAreaField({label, className = "", rows = 4, ...props}: TextAreaFieldProps) {
  return <label className={`field ${className}`}><span>{label}</span><textarea rows={rows} {...props}/></label>;
}
