# VFGNHS Library System

Web-based catalog, circulation, borrower-card, book-card, and evaluation reporting system for **Virginia F. Griño National High School (VFGNHS)**.

## Features

- Public OPAC search by title, author, subject, ISBN, call number, or accession number
- Immediate student and teacher self-registration with role-limited portals
- Administrator account, borrower, catalog, physical-copy, and lifecycle management
- Checkout/return workflow with availability, due-date, overdue, and role-specific limit enforcement
- Student/teacher loan history, reservations, and teacher acquisition requests
- Exact A4-landscape borrower-card and book-card print layouts based on the supplied Word templates
- Accession, circulation, inventory, popular-title, frequent-borrower, and overdue reports
- CSV/JSON exports, configurable loan rules, and immutable audit trail

## Technology

- React 18.3, TypeScript, Vite 6.3, Tailwind CSS 4, Lucide icons
- Java 17, Spring Boot 3.5, Spring Security/JWT, Spring Data JPA
- Flyway, H2 for local development, MySQL for production
- Vitest, Spring Boot Test, Docker, NGINX

## Local development

Requirements: Node.js 24+, npm, and Java 17.

```powershell
npm install
npm start
```

`npm start` opens the API in a separate window, waits until it is healthy, then starts the frontend. To run them separately, use `npm run backend` in one terminal and `npm run dev` in another.

Open `http://localhost:5173`. The API runs at `http://localhost:8080/api`. Both must be running; if the API is down the app shows "Cannot reach the library server".

The public attendance kiosk is available at `http://localhost:5173/attendance`. Visitors choose a purpose and either type their school/card ID or scan their printed QR card. Camera scanning works on localhost; production camera access requires the site to be served over HTTPS.

The local database is stored in `backend/data/`. Initial local administrator:

- Username: `admin`
- Password: `admin12354`

Change the password immediately through Account Management before using real data. Override local values with environment variables such as `ADMIN_EMAIL`, `ADMIN_PASSWORD`, and `JWT_SECRET`.

## Verification

```powershell
npm run typecheck
npm run lint
npm test
npm run build
cd backend
.\mvnw.cmd test
```

## Production deployment

1. Copy `.env.example` to `.env`.
2. Replace every placeholder password and use a random JWT secret of at least 32 characters.
3. Set `PUBLIC_ORIGIN` to the exact HTTPS origin used by the school.
4. Start the deployment:

```powershell
docker compose up -d --build
```

Only the NGINX web port is published. MySQL and the Spring API remain on the internal Docker network. Flyway applies schema migrations when the API starts. Put a TLS-enabled reverse proxy or managed HTTPS service in front of the web container for public internet deployment.

Health endpoints:

- Web: `/health`
- API: `/actuator/health`

## First-use checklist

1. Sign in as the seeded administrator and replace its password.
2. Open **Settings & Audit** and verify school name and student/teacher loan rules.
3. Encode a bibliographic book, then add each physical copy with a unique accession number.
4. Students and teachers may register themselves. Administrators can suspend accounts or change roles.
5. Use **Circulation** to select an available copy and borrower, then check out or return it.
6. Print a book card from Catalog or a borrower card from Borrowers/My Library. In the browser print dialog choose A4, landscape, 100% scale, and disable browser headers/footers.
7. Open **Attendance** to review the date-filtered logbook, feedback summary, and CSV export. Student and teacher QR cards can be reprinted from **Accounts**.

## Backups and restore

Create a consistent MySQL backup:

```powershell
docker compose exec db sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction library' > library-backup.sql
```

Restore into an empty `library` database:

```powershell
Get-Content library-backup.sql | docker compose exec -T db sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" library'
```

Keep encrypted backups outside the server, test restore regularly, and back up before upgrades. The named Docker volume `library-db` is persistent but is not a substitute for off-server backups.

## Privacy and operations

- OPAC endpoints expose catalog and availability only; borrower identities and circulation are authenticated.
- Public registration cannot create administrator accounts. Only an existing administrator may assign that role.
- Disable departed accounts instead of deleting records so historical circulation remains auditable.
- Use Lost, Damaged, Weeded, or Archived copy states rather than deleting accession records.
- Review overdue loans and audit logs regularly, and export evaluation reports for each reporting period.

Librarika integration is intentionally excluded because no free supported public API is documented; its SIP2 integration requires a paid plan and license.
