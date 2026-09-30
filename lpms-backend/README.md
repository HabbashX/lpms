# LPMS Backend

Pharmacy Management System backend — Java 25, Spring Boot 3.5, Spring Security + JWT,
Spring Data JPA, MySQL 8 / PostgreSQL, Flyway, Maven.

## Requirements

- JDK 25
- Maven 3.9+
- MySQL 8 (local/dev) — PostgreSQL is supported for cloud deployment

## Quick start (dev)

```powershell
# database (once)
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS lpms_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# run
mvn spring-boot:run
```

- Flyway applies all migrations on startup; `ddl-auto: validate` then checks the schema.
- On first boot the bootstrap administrator is created:
  - username: `admin` (override with `ADMIN_USERNAME`)
  - password: `Ph@rmacy2026` (override with `ADMIN_INITIAL_PASSWORD`, must satisfy the password policy)
  - the account is flagged **must change password** and must change it at `/api/v1/auth/change-password` before using other endpoints.
- Swagger UI: <http://localhost:8080/swagger-ui.html> — API docs: `/v3/api-docs`
- Root health check: `GET /` returns `{"name":"lpms-backend","status":"UP"}`.

## Configuration

All secrets are environment variables with sensible dev defaults.

| Variable | Required (prod) | Description |
|---|---|---|
| `DB_URL` | yes | JDBC URL (e.g. `jdbc:mysql://host:3306/lpms_dev?...` or `jdbc:postgresql://host:5432/db?sslmode=require`) |
| `DB_USERNAME` / `DB_PASSWORD` | yes | Database credentials |
| `JWT_SECRET` | yes | HMAC secret, min 32 chars |
| `ADMIN_USERNAME` / `ADMIN_INITIAL_PASSWORD` | yes | Bootstrap admin (password must pass `PasswordPolicy`) |
| `CORS_ALLOWED_ORIGINS` | yes | Comma-separated allowed origins |
| `SERVER_PORT` / `PORT` | no | Listen port (Render injects `PORT`; default 8080) |
| `JWT_EXPIRATION` | no | Access-token lifetime in seconds (default 3600) |
| `APP_TIMEZONE` | no | Default `UTC` |
| `DB_POOL_SIZE` / `DB_MIN_IDLE` | no | Hikari pool sizing |

Profiles: `dev` (default) — permissive defaults, `prod` — fails fast on missing secrets.

## API overview

Base path `/api/v1`. All endpoints except `/`, `/auth/login`, actuator and Swagger
require `Authorization: Bearer <token>`.

| Area | Endpoints | Access |
|---|---|---|
| Auth | `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`, `POST /auth/change-password` | login public; rest authenticated |
| Users | `GET/POST /users`, `GET/PUT/DELETE /users/{id}` | ADMIN |
| Audit | `GET /audit` | ADMIN |
| Reports | `GET /reports/profit/daily\|weekly\|monthly`, `GET /reports/profit?from&to`, `GET /reports/profit/details` | ADMIN |
| Settings | `GET/PUT /settings` | ADMIN |
| Drugs | `GET /drugs` (+filters), `GET /drugs/{id}`, `GET /drugs/barcode/{code}` | authenticated |
| | `POST /drugs`, `PUT/DELETE /drugs/{id}` | ADMIN or PHARMACIST |
| Inventory | `POST /inventory/purchases` | ADMIN or PHARMACIST |
| | `GET /inventory/batches`, `/inventory/batches/{drugId}`, `/inventory/valuation`, `/inventory/low-stock`, `/inventory/expiring` | authenticated |
| Customers | `GET/POST /customers`, `GET/PUT/DELETE /customers/{id}`, account + transactions | authenticated |
| | `POST /customers/{id}/payments`, `POST /customers/{id}/adjustments` | authenticated / ADMIN-PHARMACIST |
| Sales | `POST /sales`, `GET /sales`, `GET /sales/{id}` | authenticated |
| | `POST /sales/{id}/refund` | ADMIN or PHARMACIST |
| Dashboard | `GET /dashboard` | authenticated |

### Error format

```json
{ "timestamp": "...", "status": 409, "code": "INSUFFICIENT_STOCK",
  "message": "...", "path": "/api/v1/sales", "errors": [] }
```

Notable codes: `INVALID_CREDENTIALS` (401), `ACCOUNT_LOCKED` (423),
`PASSWORD_CHANGE_REQUIRED` (403), `INSUFFICIENT_STOCK` / `EXPIRED_STOCK` (409),
`VALIDATION_FAILED` (400), `FORBIDDEN` (403).

### Business rules

- **Weighted-average costing**: purchases add batches; sales consume FEFO under a
  pessimistic lock; sale cost is frozen per line (`unitCostPrice`, `cost`).
- **Stock cache**: `drugs.current_quantity` is kept in sync with batch sums.
- **Refunds** never delete sales: they create `refunds`/`refund_items`, reverse the
  ledger (customer credit capped by balance unless negative balances are allowed)
  and restock a new batch at the sale's historical unit cost.
- **Credit sales** require a customer; payments/adjustments maintain the customer
  ledger (`customer_transactions`) and account balance.
- **Profit reports** are net of refunds: `sum(total - refunded_total)` /
  `sum(cost_total - refunded_cost)` over `[from, to+1day)`.

## Tests

```powershell
# requires local MySQL; creates/uses database lpms_test
$env:DB_USERNAME='root'; $env:DB_PASSWORD='<password>'
mvn test
```

- Unit tests (`*Test` in `src/test/java`) cover money math, date ranges, pagination,
  password policy, JWT and costing.
- `EndToEndFlowIntegrationTest` and `SecurityIntegrationTest` run the full flow
  (purchases → sales → refunds → reports/dashboard reconciliation; auth, RBAC,
  lockout, forced password change) against the real schema. Each test is
  transactional and rolls back. If MySQL is unreachable the integration tests are
  skipped via JUnit assumptions.

## Deployment (Docker / Render)

Multi-stage `Dockerfile`: Maven + JDK 25 build → JRE 25 runtime.

```bash
docker build -t lpms-backend .
docker run -p 8080:8080 --env-file .env lpms-backend
```

On Render: create a Postgres instance, a web service (runtime **Docker**,
Dockerfile path `./Dockerfile`), set the required env vars above, and the service
health-checks `GET /`. Migrations and admin bootstrap run automatically on deploy.
