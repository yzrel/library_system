import type {Copy, Loan, User, VisitPurpose} from "@/app/api";

export type View = "dashboard" | "catalog" | "circulation" | "distribution" | "borrowers" | "attendance" | "reports" | "accounts" | "classes" | "requests" | "settings" | "my-library";
export function homeView(role: string): View {
  return role === "ADMIN" ? "dashboard" : "my-library";
}
export type SinglePrintPayload = {kind: "book"; copy: Copy; transactions: Loan[]} | {kind: "borrower"; borrower: User; transactions: Loan[]};
export type QrPrintPayload = {kind: "qr"; borrower: User};
export type PrintPayload = SinglePrintPayload | QrPrintPayload | {kind: "batch"; cards: (SinglePrintPayload | QrPrintPayload)[]};
export type GenericRow = Record<string, string | number | boolean | null | undefined>;
export const SCHOOL = "Virginia F. Griño National High School";
export const ATTENDANCE_PURPOSES: {value: VisitPurpose; label: string}[] = [
  {value:"READING",label:"Reading"}, {value:"ATTEND_CLASS",label:"Attend class"},
  {value:"REMEDIAL",label:"Remedial"}, {value:"MEETING",label:"Meeting"},
  {value:"REVIEW",label:"Review"}, {value:"STUDY",label:"Study"},
  {value:"PLAY_BOARD_GAMES",label:"Play board games"}, {value:"RESEARCH",label:"Research"},
  {value:"USE_DIGITAL_SERVICES",label:"Use digital services"},
];
