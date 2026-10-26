import type {ButtonHTMLAttributes} from "react";

export function Button({children, variant = "primary", ...props}: ButtonHTMLAttributes<HTMLButtonElement> & {variant?: "primary"|"secondary"|"danger"|"ghost"}) {
  return <button {...props} className={`btn btn-${variant} ${props.className ?? ""}`}>{children}</button>;
}
