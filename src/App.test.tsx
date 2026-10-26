import {act} from "react";
import {createRoot} from "react-dom/client";
import {renderToStaticMarkup} from "react-dom/server";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import App from "@/app/App";
import {MyLibrary} from "@/app/components/MyLibrary";
import {session} from "@/app/api";
import {homeView} from "@/app/types";
import {BookCover} from "@/app/components/BookCover";
import {catalogFilterOptions, catalogSubject, catalogSubjects, filterCatalogBooks, GENERAL_COLLECTION, matchCatalogSubject} from "@/app/catalogFilter";
import {AuthModal} from "@/app/components/AuthModal";
import {PublicCatalog} from "@/app/components/PublicCatalog";
import {AttendanceQrCard} from "@/app/components/PrintPreview";
import type {User} from "@/app/api";

describe("BookCover", () => {
  it("renders a real catalog cover with descriptive alt text", () => {
    const html=renderToStaticMarkup(<BookCover book={{title:"Noli Me Tangere",author:"José Rizal",coverUrl:"https://example.com/noli.jpg"}}/>);
    expect(html).toContain('src="https://example.com/noli.jpg"');
    expect(html).toContain('alt="Cover of Noli Me Tangere"');
    expect(html).toContain('loading="lazy"');
  });

  it("renders an illustrated, title-aware fallback when no cover is available", () => {
    const html=renderToStaticMarkup(<BookCover book={{title:"Florante at Laura",author:"Francisco Balagtas"}}/>);
    expect(html).toContain("Illustrated cover for Florante at Laura");
    expect(html).toContain("FAL");
    expect(html).toContain("Florante at Laura");
  });
});

describe("AttendanceQrCard", () => {
  it("prints the patron identity and QR payload without exposing login details", () => {
    const borrower: User={id:12,borrowerId:8,email:"private@example.test",firstName:"Juan",lastName:"Dela Cruz",role:"STUDENT",status:"ACTIVE",schoolId:"LRN-10",cardNumber:"S-LRN-10",gradeSection:"Grade 10 - Rizal"};
    const html=renderToStaticMarkup(<AttendanceQrCard borrower={borrower}/>);
    expect(html).toContain("Attendance QR Card");
    expect(html).toContain("Juan Dela Cruz");
    expect(html).toContain("S-LRN-10");
    expect(html).toContain("<svg");
    expect(html).not.toContain("private@example.test");
  });
});

describe("catalog subject filter", () => {
  const books = [
    {subject: "Fiction"},
    {subject: "Science"},
    {subject: "  fiction  "},
    {subject: ""},
    {subject: undefined},
  ];

  it("treats blank subjects as the general collection", () => {
    expect(catalogSubject({subject: ""})).toBe(GENERAL_COLLECTION);
    expect(catalogSubject({subject: "   "})).toBe(GENERAL_COLLECTION);
    expect(catalogSubject({})).toBe(GENERAL_COLLECTION);
    expect(catalogSubject({subject: "Science"})).toBe("Science");
  });

  it("lists unique subjects and can show all or one subject", () => {
    expect(catalogSubjects(books)).toEqual(["Fiction", GENERAL_COLLECTION, "Science"]);
    expect(filterCatalogBooks(books, "")).toHaveLength(5);
    expect(filterCatalogBooks(books, "Fiction")).toHaveLength(2);
    expect(filterCatalogBooks(books, GENERAL_COLLECTION)).toHaveLength(2);
  });

  it("uses predetermined subjects first and only keeps unmatched book subjects", () => {
    expect(catalogFilterOptions(["Fiction", "Science", "Technology"], books))
      .toEqual(["Fiction", "Science", "Technology", GENERAL_COLLECTION]);
    expect(matchCatalogSubject("fiction", ["Fiction", "Science"])).toBe("Fiction");
    expect(matchCatalogSubject("Robbers and outlaws", ["Fiction", "Science"])).toBe("");
  });
});

describe("reservations and password rules", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("lets a signed-in member reserve from the catalog", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([{
      id: 8, title: "Noli Me Tangere", author: "José Rizal", totalCopies: 1, availableCopies: 1, copies: [],
    }]), {status: 200, headers: {"content-type": "application/json"}})));
    const el = document.createElement("div");
    document.body.appendChild(el);
    const root = createRoot(el);
    await act(async () => { root.render(<PublicCatalog compact user={{
      id: 4, email: "ana@test.local", firstName: "Ana", lastName: "Cruz", role: "STUDENT", status: "ACTIVE", schoolId: "LRN-1",
    }}/>); });
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    expect(el.textContent).toContain("Reserve");
    expect(el.textContent).toContain("Noli Me Tangere");
    root.unmount();
    el.remove();
  });

  it("shows the letter-and-number password rule on registration", () => {
    const html = renderToStaticMarkup(<AuthModal onClose={() => undefined} onLogin={() => undefined} onPrint={() => undefined}/>);
    expect(html).toContain("At least 8 characters with a letter and a number");
  });
});

describe("member landing view", () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.unstubAllGlobals());

  it("sends administrators to the dashboard and members to My Library", () => {
    expect(homeView("ADMIN")).toBe("dashboard");
    expect(homeView("STUDENT")).toBe("my-library");
    expect(homeView("TEACHER")).toBe("my-library");
  });

  it("opens My Library for a signed-in student instead of a blank dashboard", async () => {
    session.set("student-token");
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      const payload = url.includes("/auth/me")
        ? {id: 4, firstName: "Ana", lastName: "Cruz", role: "STUDENT", status: "ACTIVE", schoolId: "LRN-1001", cardNumber: "S-LRN-1001", gradeSection: "Grade 10 - Rizal"}
        : [];
      return new Response(JSON.stringify(payload), {status: 200, headers: {"content-type": "application/json"}});
    }));
    const el = document.createElement("div");
    document.body.appendChild(el);
    const root = createRoot(el);
    await act(async () => { root.render(<App/>); });
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    expect(el.textContent).toContain("My Library");
    expect(el.textContent).toContain("Ana's Library");
    root.unmount();
    el.remove();
  });

  it("refreshes the signed-in user after a profile save instead of reloading", async () => {
    const onUser = vi.fn();
    const reload = vi.fn();
    Object.defineProperty(window, "location", {configurable: true, value: {...window.location, reload}});
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/auth/profile") && init?.method === "PUT") {
        return new Response(JSON.stringify({id: 4, firstName: "Ana"}), {status: 200, headers: {"content-type": "application/json"}});
      }
      if (url.includes("/auth/me")) {
        return new Response(JSON.stringify({
          id: 4, firstName: "Ana", lastName: "Santos", role: "TEACHER", status: "ACTIVE",
          schoolId: "T-1001", cardNumber: "T-1001", department: "Science",
        }), {status: 200, headers: {"content-type": "application/json"}});
      }
      return new Response("[]", {status: 200, headers: {"content-type": "application/json"}});
    }));
    const user: User = {
      id: 4, email: "ana@test.local", firstName: "Ana", lastName: "Cruz", role: "TEACHER", status: "ACTIVE",
      schoolId: "T-1001", cardNumber: "T-1001", department: "Science",
    };
    const el = document.createElement("div");
    document.body.appendChild(el);
    const root = createRoot(el);
    await act(async () => { root.render(<MyLibrary user={user} onUser={onUser} onPrint={() => undefined}/>); });
    await act(async () => { await Promise.resolve(); });
    const save = Array.from(el.querySelectorAll("button")).find(button => button.textContent?.includes("Save profile")) as HTMLButtonElement;
    await act(async () => { save.click(); });
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    expect(reload).not.toHaveBeenCalled();
    expect(onUser).toHaveBeenCalledWith(expect.objectContaining({lastName: "Santos"}));
    root.unmount();
    el.remove();
  });
});
