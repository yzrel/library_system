import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, availableAdvisoryClasses, ClassSection, displayDate, distributionCreateLines, DistributionPreview, distributionSelectionReady, schoolYearDefaults, session, SESSION_EXPIRED, SESSION_EXPIRED_EVENT } from "@/app/api";

describe("library client helpers", () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.unstubAllGlobals());

  it("clears a rejected session and notifies the app", async () => {
    session.set("stale-token");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      JSON.stringify({message: "Authentication required"}),
      {status: 401, headers: {"content-type": "application/json"}},
    )));
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);

    await expect(api("/admin/catalog")).rejects.toThrow(SESSION_EXPIRED);
    expect(session.get()).toBeNull();
    expect(expired).toHaveBeenCalledOnce();
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it("stores and clears authentication tokens", () => {
    session.set("signed-token");
    expect(session.get()).toBe("signed-token");
    session.clear();
    expect(session.get()).toBeNull();
  });

  it("shows blank dates consistently", () => {
    expect(displayDate()).toBe("—");
    expect(displayDate("2026-10-07T00:00:00Z")).toContain("2026");
  });

  it("uses the Philippine school-year convention for distribution defaults", () => {
    expect(schoolYearDefaults(new Date(2026, 5, 1))).toEqual({schoolYear:"2026-2027",expectedReturnDate:"2027-03-31"});
    expect(schoolYearDefaults(new Date(2026, 4, 31))).toEqual({schoolYear:"2025-2026",expectedReturnDate:"2026-03-31"});
  });

  it("requires exact reviewed copy quantities and builds the confirmation lines", () => {
    const preview={lines:[
      {bookId:10,quantity:2,proposedCopies:[],availableCopies:[],shortfall:0,title:"Science"},
      {bookId:20,quantity:1,proposedCopies:[],availableCopies:[],shortfall:1,title:"Math"},
    ]} as unknown as DistributionPreview;
    expect(distributionSelectionReady(preview,{10:[101,102],20:[]})).toBe(false);
    const selected={10:[101,102],20:[201]};
    expect(distributionSelectionReady(preview,selected)).toBe(true);
    expect(distributionCreateLines(preview,selected)).toEqual([
      {bookId:10,copyIds:[101,102]},{bookId:20,copyIds:[201]},
    ]);
  });

  it("offers only active, unassigned classes or the teacher's current assignment", () => {
    const classes=[
      {id:1,active:true,adviserBorrowerId:10},
      {id:2,active:true},
      {id:3,active:true,adviserBorrowerId:20},
      {id:4,active:false},
    ] as ClassSection[];
    expect(availableAdvisoryClasses(classes,10).map(item=>item.id)).toEqual([1,2]);
    expect(availableAdvisoryClasses(classes,30).map(item=>item.id)).toEqual([2]);
  });
});
