# BuildBack — Construction Surplus Marketplace

Sellers list leftover construction materials (tiles, paint, plywood, fittings).
Buyers search them, **calculate how many tiles their floor needs**, reserve the stock
and pay through a payment gateway in **test mode**. Admins moderate listings and review totals.

Final-exam project · **React.js + Spring Boot + MySQL** · Kaif Khan

---

## 1. Features

| Role | What they can do |
| --- | --- |
| **Visitor** | Browse and search the catalogue, open listing details, use the tile calculator |
| **Buyer** | Reserve a quantity, pay (test mode), retry failed payments, cancel unpaid orders, see order history |
| **Seller** | Add / edit / archive own listings, see orders on own listings, mark paid orders as collected |
| **Admin** | Totals dashboard, all users, all orders, archive (moderate) any listing |

| Module | Implemented |
| --- | --- |
| Authentication | Registration, login, logout, BCrypt password hashing, server-side sessions, CSRF protection, role checks |
| Material CRUD | Create, view, edit, archive (soft delete), optimistic locking against stale edits |
| Discovery | Keyword search, category / city / price / in-stock filters, sorting, server-side pagination |
| Tile calculator | `required tiles = round up( floor area × (1 + allowance %) ÷ tile area )` |
| Orders | Quantity validation, stock reservation with row locks, 15-minute expiry, cancel, history |
| Payments | Razorpay **test mode** or a clearly labelled simulator; server-side signature verification; failed and duplicate confirmations handled |
| Dashboards | Real totals per role; admin console |

### The demo scenario (from the approved proposal)
1. Seller adds **35** tiles of 2 ft × 2 ft.
2. Buyer enters **60 sq ft** with a 10 % allowance → calculator says **17 tiles** (60 × 1.10 = 66 ÷ 4 = 16.5 → 17).
3. Buyer reserves 17 → the listing immediately shows **18** available.
4. Buyer pays → the server verifies → order **PAID**, still **18** available.
5. Seller marks the order **COLLECTED**.

Failure cases you can show: ordering 19 when 18 are available (rejected), a declined payment
(stock stays reserved, retry works), a seller trying to edit another seller's listing (404),
an unpaid order expiring (stock returns exactly once).

---

## 2. Architecture

```
React (Vite, :5173) ──HTTP + session cookie + CSRF header──► Spring Boot (:8081) ──JPA──► MySQL (buildback_db)
                                                                    │
                                                                    └──► Razorpay API (test mode, optional)
```

Backend layers (`buildback-backend/src/main/java/com/buildback`):

| Package | Responsibility |
| --- | --- |
| `entity` | JPA classes = database tables (`User`, `Material`, `PurchaseOrder`, `Payment`) |
| `repository` | Database access (Spring Data JPA; row locks for stock changes) |
| `service` | Business rules: registration, listings, search, calculator, orders, payments, admin |
| `controller` | REST endpoints; turn HTTP into service calls |
| `dto` | What goes in and out as JSON (never the entities themselves) |
| `security`, `config` | Spring Security, CORS, admin bootstrap, scheduling |
| `payment` | Gateway interface + Razorpay test-mode client + simulator |
| `exception` | One JSON error format for every error |

Frontend (`buildback-frontend/src`): `api/client.js` (all HTTP calls, cookies, CSRF),
`auth/` (who is logged in), `pages/`, `components/`, `materials/` and `orders/` helpers.

Database schema: [`docs/schema.sql`](docs/schema.sql) (generated from the entities).

---

## 3. Setup

### Prerequisites
Java 21, Maven (wrapper included), Node.js 22+, MySQL 8 with an empty database:
```sql
CREATE DATABASE buildback_db;
```
Tables are created automatically on first start.

### Backend (Eclipse)
1. **File → Import → Maven → Existing Maven Projects** → `buildback-backend`.
2. Right-click the project → **Maven → Update Project…**
3. **Run → Run Configurations… → BuildbackBackendApplication → Environment**, add:

| Variable | Value |
| --- | --- |
| `DB_USERNAME` | your MySQL user (e.g. `root`) |
| `DB_PASSWORD` | your MySQL password |

4. Run. Check http://localhost:8081/api/health → `"database":"Connected"`.

### Frontend (VS Code)
```
cd buildback-frontend
npm install
npm run dev
```
Open http://localhost:5173. `buildback-frontend/.env` contains only the public API address
(`VITE_API_BASE_URL=http://localhost:8081/api`) — never put secrets there.

### First admin account (one time)
There is no public "make admin" endpoint. Add these to the Eclipse Environment tab, run once, then remove them:

| Variable | Value |
| --- | --- |
| `ADMIN_BOOTSTRAP_ENABLED` | `true` |
| `ADMIN_EMAIL` | admin email |
| `ADMIN_PASSWORD` | 8+ characters |
| `ADMIN_NAME` | optional |

After the log says *"admin account created"*, set `ADMIN_BOOTSTRAP_ENABLED=false` and delete `ADMIN_PASSWORD`.
It never resets a password or promotes an existing buyer/seller.

### Payments (test mode only)
**Default: built-in simulator.** Works without any keys and is labelled *"Test payment simulator — no real money"*
in the UI. It still produces a signed confirmation that the server verifies, so the verification,
failure and duplicate-handling code is the same one used with Razorpay.

**Razorpay test mode** (needs your own free Razorpay test account):
1. Razorpay Dashboard → switch to **Test Mode** → *Account & Settings → API Keys* → generate test keys.
2. Add to the Eclipse Environment tab:

| Variable | Value |
| --- | --- |
| `PAYMENT_PROVIDER` | `razorpay` |
| `RAZORPAY_KEY_ID` | `rzp_test_...` |
| `RAZORPAY_KEY_SECRET` | your test secret |

3. Restart. Pay with the test cards / UPI IDs from Razorpay's *Test Card Details* documentation.
Live keys (`rzp_live_...`) are refused on purpose. The secret is only used by the backend.

### Demo listings (optional)
Register a seller in the app, then (backend running):
```
powershell -ExecutionPolicy Bypass -File scripts\add-demo-listings.ps1 -Email your-seller@example.com
```
It asks for that seller's password and adds five `[Demo]` listings (one per category, including the 35 tiles)
through the normal API. No accounts or passwords are hard-coded anywhere.

---

## 4. Main API endpoints

| Method | Endpoint | Who |
| --- | --- | --- |
| GET | `/api/health` | public |
| GET | `/api/auth/csrf` · POST `/api/auth/register` · POST `/api/auth/login` · GET `/api/auth/me` · POST `/api/auth/logout` | public / logged in |
| GET | `/api/materials?keyword=&category=&city=&minPrice=&maxPrice=&inStock=&sort=&page=&size=` | public |
| GET | `/api/materials/{id}` · `/api/materials/{id}/tile-estimate?areaSqFt=60&allowancePercent=10` | public |
| GET POST | `/api/seller/materials` · GET PUT DELETE `/api/seller/materials/{id}` | seller (own only) |
| GET | `/api/seller/orders` · POST `/api/seller/orders/{id}/collect` | seller |
| POST GET | `/api/buyer/orders` · GET `/api/buyer/orders/{id}` · POST `/{id}/cancel` · POST `/{id}/payments` | buyer (own only) |
| POST | `/api/buyer/payments/{id}/verify` · `/failure` · `/simulate` | buyer |
| GET | `/api/admin/summary` · `/users` · `/materials` · `/orders` · POST `/api/admin/materials/{id}/archive` | admin |

Every POST/PUT/DELETE needs the `X-CSRF-TOKEN` header from `/api/auth/csrf` (the React client does this).
Errors always look like `{"message": "...", "errors": {"field": "..."}}`.

---

## 5. Tests

```
cd buildback-backend
mvnw.cmd test
```
67 automated tests run against an in-memory H2 database (profile `test`), so they never touch `buildback_db`:

| Test class | Covers |
| --- | --- |
| `AuthIntegrationTest` | registration rules, login/logout, sessions, CSRF, CORS, roles |
| `MaterialIntegrationTest` | listing CRUD, ownership, archive, optimistic locking, validation, search/sort/paging |
| `OrderPaymentIntegrationTest` | the 35 → 17 → 18 demo, excess quantity, two buyers racing for the last stock, failed payment + retry, forged signature, expiry releasing stock once, payment after expiry, cancel, permissions, admin |
| `TileCalculatorTest` | the formula, rounding up, exact decimals |
| `RazorpayPaymentGatewayTest` | Razorpay requests/answers against a fake Razorpay server, HMAC signature check |
| `HealthControllerTest`, `AdminBootstrapTest`, `UserServiceDuplicateTest` | health check, first admin, duplicate emails |

---

## 6. Key design decisions (viva notes)

- **DTOs instead of entities in JSON.** Clients can't set `id`, `role`, `seller`, `status` or prices, and password hashes never leave the server.
- **Ownership in the query.** `WHERE id = ? AND seller_id = ?`, so another seller's listing is simply "not found" (404).
- **Stock reservation with row locks.** Creating an order runs `SELECT … FOR UPDATE` on the listing row, checks stock, moves it from *available* to *reserved*, and commits. Two buyers can't both get the last tiles.
- **Expiry exactly once.** A scheduled job (every minute) expires unpaid orders after 15 minutes. It re-checks the status while the order row is locked, so a cancel and an expiry at the same moment can't release stock twice.
- **Server-verified payments.** The browser only reports what the gateway returned. The server checks `HMAC-SHA256(orderId|paymentId, secret)`, and for Razorpay it also asks Razorpay directly that the amount and order match. `provider_payment_id` is UNIQUE and repeated confirmations are ignored, so stock can't be reduced twice.
- **Payment after expiry.** Recorded as successful and flagged `refund_required`. It doesn't silently disappear and doesn't break stock.
- **BigDecimal / DECIMAL for money and sizes.** Exact, no floating-point errors. Money goes over JSON as text, e.g. `"765.00"`.
- **Optimistic locking (`@Version`).** Two edits of the same listing: the second one gets 409 instead of silently overwriting the first.
- **Archive instead of delete.** Listings with order history stay in the database for records.
- **Sessions + CSRF instead of JWT.** An HttpOnly cookie can't be read by JavaScript. The CSRF token stops other websites from sending requests with that cookie.

## 7. Scope (version 1) and future work

Included: one listing per order, tile calculator (rectangular tiles, feet, whole tiles), test payments,
collection status, basic dashboards.

Future: multi-seller cart, calculators for paint/plywood, live payments and seller payouts, automated refunds,
delivery integration, chat, recommendations, image uploads.
