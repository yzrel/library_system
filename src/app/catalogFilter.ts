import type {Book} from "@/app/api";

export const GENERAL_COLLECTION = "General Collection";

export function catalogSubject(book: Pick<Book, "subject">) {
  const value = book.subject?.trim();
  return value || GENERAL_COLLECTION;
}

export function catalogSubjects(books: Pick<Book, "subject">[]) {
  const seen = new Map<string, string>();
  for (const book of books) {
    const label = catalogSubject(book);
    const key = label.toLowerCase();
    if (!seen.has(key)) seen.set(key, label);
  }
  return [...seen.values()].sort((a, b) => a.localeCompare(b, undefined, {sensitivity: "base"}));
}

export function filterCatalogBooks<T extends Pick<Book, "subject">>(books: T[], subject: string) {
  if (!subject) return books;
  const key = subject.toLowerCase();
  return books.filter(book => catalogSubject(book).toLowerCase() === key);
}

export function catalogFilterOptions(managed: string[], books: Pick<Book, "subject">[]) {
  const options: string[] = [];
  const seen = new Set<string>();
  for (const name of managed) {
    const label = name.trim();
    if (!label) continue;
    const key = label.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    options.push(label);
  }
  for (const label of catalogSubjects(books)) {
    const key = label.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    options.push(label);
  }
  return options;
}

export function matchCatalogSubject(lookup: string | null | undefined, subjects: string[]) {
  const raw = lookup?.trim();
  if (!raw) return "";
  return subjects.find(name => name.toLowerCase() === raw.toLowerCase()) ?? "";
}
