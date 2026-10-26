import {useEffect, useId, useRef, useState} from "react";
import {Search} from "lucide-react";

export type AutocompleteOption = {value: string; label: string; keywords?: string};
export function AutocompleteField({label, name, options, placeholder, required, onValueChange, className = ""}: {
  label: string; name: string; options: AutocompleteOption[]; placeholder?: string; required?: boolean;
  onValueChange?: (value: string) => void; className?: string;
}) {
  const listId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const [query, setQuery] = useState("");
  const [value, setValue] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const normalized = query.trim().toLowerCase();
  const matches = (normalized
    ? options.filter((option) => {
        const haystack = `${option.label} ${option.keywords ?? ""}`.toLowerCase();
        return haystack.includes(normalized);
      })
    : options).slice(0, 12);

  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  const choose = (option: AutocompleteOption) => {
    setValue(option.value);
    setQuery(option.label);
    onValueChange?.(option.value);
    setOpen(false);
  };

  return <div className={`field autocomplete ${className}`} ref={rootRef}>
    <span>{label}</span>
    <div className="autocomplete-control">
      <Search size={16} aria-hidden="true"/>
      <input
        type="search"
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={open}
        aria-controls={listId}
        aria-activedescendant={open && matches[active] ? `${listId}-${matches[active].value}` : undefined}
        value={query}
        placeholder={placeholder}
        autoComplete="off"
        required={required && !value}
        onFocus={() => { setOpen(true); setActive(0); }}
        onChange={(event) => {
          const next = event.target.value;
          setQuery(next);
          setValue("");
          onValueChange?.("");
          setOpen(true);
          setActive(0);
        }}
        onKeyDown={(event) => {
          if (!open && (event.key === "ArrowDown" || event.key === "ArrowUp")) {
            setOpen(true);
            return;
          }
          if (event.key === "ArrowDown") {
            event.preventDefault();
            setActive((index) => Math.min(index + 1, Math.max(matches.length - 1, 0)));
          } else if (event.key === "ArrowUp") {
            event.preventDefault();
            setActive((index) => Math.max(index - 1, 0));
          } else if (event.key === "Enter" && open && matches[active]) {
            event.preventDefault();
            choose(matches[active]);
          } else if (event.key === "Escape") {
            setOpen(false);
          }
        }}
      />
      <input type="hidden" name={name} value={value} required={required}/>
    </div>
    {open && <ul className="autocomplete-menu" id={listId} role="listbox">
      {matches.length === 0
        ? <li className="autocomplete-empty" role="presentation">No matches</li>
        : matches.map((option, index) => <li key={option.value}>
            <button
              type="button"
              id={`${listId}-${option.value}`}
              role="option"
              aria-selected={index === active}
              className={index === active ? "active" : undefined}
              onMouseEnter={() => setActive(index)}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => choose(option)}
            >{option.label}</button>
          </li>)}
    </ul>}
  </div>;
}
