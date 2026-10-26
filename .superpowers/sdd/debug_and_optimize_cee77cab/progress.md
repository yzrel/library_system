# SDD ledger — plan: C:/Users/DOST 12/.cursor/plans/debug_and_optimize_cee77cab.plan.md

Pre-flight: no shared interfaces that conflict. Tasks 1 and 7 both touch App.tsx (landing view vs lazy imports); Task 5 also touches MyLibrary/PublicCatalog. Apply in order.

Ruling: Repo has no commits and no isolated worktree — implement in the current workspace. SDD task-start/task-done scripts need HEAD and would fail. Ledger this file by hand. User asked not to commit unless requested; plan has no commit steps.

Spec: none reachable — rulings are provisional.

Task 1: complete — member landing + profile refresh tests green.
Task 2: complete — borrower list includes classSectionId. Ruling: Borrower list does not 500; field-access lazy loading leaves classSection null. Cost if wrong: we miss a getter-based LazyInitializationException path.
Task 3: complete — invalid copy status 400, normalized card collision 409.
Task 4: checkout uses lockAllById; accession generation is transactional, Manila-year, row-locked, retry-once.
Task 5: unique pending reservation + catalog/My Library UI.
Task 6: batched catalog copies, dashboard counts, V7 indexes, OPAC assertion by title.
Task 7: complete — lazy admin/kiosk pages, unused deps removed, Due label, loading/mobile cards.
Task 8: complete — typecheck, lint, 17 frontend tests, production build, backend mvnw test (V7 applied). Browser: dashboard counts, borrowers with class, Circulation Due, student My Library, reserve Book A, print card overlay.

Final review: self-review (no git commits / review-package HEAD). No Critical/Important leftovers from the plan's Review Focus.

Ruling: Did not create the first repo commit or a PR — user did not ask to commit. Cost if wrong: work stays uncommitted on disk.
