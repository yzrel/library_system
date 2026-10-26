export type Role = "STUDENT" | "TEACHER" | "ADMIN";

export interface User {
  id: number;
  borrowerId?: number;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  status: string;
  schoolId: string;
  cardNumber?: string;
  gradeSection?: string;
  department?: string;
  classSectionId?: number;
  classSchoolYear?: string;
  phone?: string;
  borrowerStatus?: string;
}

export interface Copy {
  id: number; bookId: number; title: string; author?: string;
  accessionNumber: string; callNumber?: string; volumeCopy?: string;
  pages?: string; dateReceived?: string; sourceOfFund?: string; cost?: number;
  status: string; remarks?: string; barcode?: string;
}

export interface Book {
  id: number; title: string; subtitle?: string; author?: string; isbn?: string;
  publisher?: string; publicationYear?: number; edition?: string; subject?: string;
  description?: string; coverUrl?: string; totalCopies: number; availableCopies: number; copies: Copy[];
}

export interface BookSubject {
  id: number;
  name: string;
  sortOrder: number;
  active: boolean;
}

export type IsbnLookupSource = "LOCAL" | "OPEN_LIBRARY" | "GOOGLE_BOOKS" | "NONE";

export interface IsbnLookupResult {
  isbn: string;
  source: IsbnLookupSource;
  found: boolean;
  existingBookId?: number | null;
  suggestedAccessionNumber?: string | null;
  title?: string | null;
  subtitle?: string | null;
  author?: string | null;
  publisher?: string | null;
  publicationYear?: number | null;
  edition?: string | null;
  subject?: string | null;
  description?: string | null;
  coverUrl?: string | null;
  existingBook?: Book;
}

export interface Reservation {
  id: number; bookId: number; title: string; status: string; requestedAt: string;
}

export const PASSWORD_HINT = "At least 8 characters with a letter and a number";
export const PASSWORD_PATTERN = "(?=.*[A-Za-z])(?=.*\\d).{8,72}";

export interface Loan {
  id: number; copyId: number; bookId: number; title: string; author?: string;
  accessionNumber: string; callNumber?: string; borrowerId: number; borrowerName: string;
  cardNumber: string; gradeSection?: string; borrowedAt: string; dueAt: string; returnedAt?: string;
  notes?: string; overdue: boolean; distributionId?: number;
  distributionGradeSection?: string; distributionSchoolYear?: string;
}

export type DistributionStatus = "ACTIVE" | "PARTIALLY_RETURNED" | "RETURNED";

export interface DistributionSummary {
  id: number; teacherBorrowerId: number; teacherName: string; teacherCardNumber: string;
  gradeSection: string; schoolYear: string; distributedAt: string; expectedReturnDate: string;
  status: DistributionStatus; notes?: string; totalCopies: number; activeCopies: number;
  returnedCopies: number; overdueCopies: number;
}

export interface DistributionDetail extends DistributionSummary { loans: Loan[]; }

export interface DistributionPreviewLine {
  bookId: number; title: string; author?: string; quantity: number;
  proposedCopies: Copy[]; availableCopies: Copy[]; shortfall: number;
}

export interface DistributionPreview {
  teacher: User; gradeSection: string; schoolYear: string;
  expectedReturnDate: string; lines: DistributionPreviewLine[];
}

export interface ClassSection {
  id: number; gradeLevel: string; sectionName: string; label: string;
  schoolYear: string; active: boolean; adviserBorrowerId?: number; adviserName?: string;
}

export function availableAdvisoryClasses(classes: ClassSection[], teacherBorrowerId?: number) {
  return classes.filter(item => item.active &&
    (item.adviserBorrowerId == null || item.adviserBorrowerId === teacherBorrowerId));
}

export function schoolYearDefaults(now=new Date()) {
  const start=now.getMonth()>=5?now.getFullYear():now.getFullYear()-1;
  return {schoolYear:`${start}-${start+1}`,expectedReturnDate:`${start+1}-03-31`};
}

export function distributionSelectionReady(preview: DistributionPreview | null, selected: Record<number, number[]>) {
  return !!preview && preview.lines.every(line => (selected[line.bookId]?.length ?? 0) === line.quantity);
}

export function distributionCreateLines(preview: DistributionPreview, selected: Record<number, number[]>) {
  return preview.lines.map(line => ({bookId: line.bookId, copyIds: selected[line.bookId] ?? []}));
}

export type VisitPurpose = "READING" | "ATTEND_CLASS" | "REMEDIAL" | "MEETING" | "REVIEW" | "STUDY" | "PLAY_BOARD_GAMES" | "RESEARCH" | "USE_DIGITAL_SERVICES";
export type EntryMethod = "QR" | "MANUAL";
export type FeedbackRating = "SATISFIED" | "NEUTRAL" | "NOT_SATISFIED";

export interface AttendanceVisit {
  id: number;
  checkedInAt: string;
  name: string;
  role: Role;
  gradeSection?: string;
  purpose: VisitPurpose;
  entryMethod: EntryMethod;
}

export interface CheckInResult {
  visitId: number;
  checkedInAt: string;
  name: string;
  gradeSection?: string;
  department?: string;
  purpose: VisitPurpose;
}

export interface AttendanceSuggestion {
  name: string;
  identifier: string;
  schoolId: string;
  role: Role;
  gradeSection?: string;
}

export interface AttendanceReport {
  visits: AttendanceVisit[];
  feedback: Record<FeedbackRating, number>;
}

export interface TodayAttendance {
  date: string;
  count: number;
  visits: AttendanceVisit[];
}

const TOKEN = "vfgnhs-library-token";
export const session = {
  get: () => localStorage.getItem(TOKEN),
  set: (token: string) => localStorage.setItem(TOKEN, token),
  clear: () => localStorage.removeItem(TOKEN),
};

export const SESSION_EXPIRED_EVENT = "vfgnhs-session-expired";
export const SESSION_EXPIRED = "Your session has ended. Please sign in again.";

export const SERVER_UNREACHABLE =
  "Cannot reach the library server. Make sure it is running (npm start), then try again.";

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = session.get();
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      ...options,
      headers: {
        ...(options.body ? {"Content-Type": "application/json"} : {}),
        ...(token ? {Authorization: `Bearer ${token}`} : {}),
        ...options.headers,
      },
    });
  } catch {
    throw new Error(SERVER_UNREACHABLE);
  }
  if (response.status === 401 && token && path !== "/auth/login") {
    session.clear();
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    throw new Error(SESSION_EXPIRED);
  }
  const contentType = response.headers.get("content-type") ?? "";
  if (!response.ok && !contentType.includes("json")) {
    throw new Error(response.status >= 500 ? SERVER_UNREACHABLE : `Request failed (${response.status})`);
  }
  if (response.status === 204 || response.headers.get("content-length") === "0") return undefined as T;
  const body = contentType.includes("json") ? await response.json() : await response.text();
  if (!response.ok) throw new Error(body.message ?? "Request failed");
  return body as T;
}

export const json = (method: string, body?: unknown): RequestInit => ({
  method,
  body: body === undefined ? undefined : JSON.stringify(body),
});

export const displayDate = (value?: string) =>
  value ? new Intl.DateTimeFormat("en-PH", {year: "numeric", month: "short", day: "numeric"}).format(new Date(value)) : "—";

export const displayDateTime = (value?: string) =>
  value ? new Intl.DateTimeFormat("en-PH", {year: "numeric", month: "short", day: "numeric", hour: "numeric", minute: "2-digit"}).format(new Date(value)) : "—";
