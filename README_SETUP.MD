# GharSe Backend — Team Conventions

## Architecture
- **Package-by-feature:** `auth/`, `cart/`, `catalog/`, `order/`, `payment/`, `seller/`, etc.
- **Layers:** Controller → Service → Repository (never skip layers).
- **DTOs only:** Controllers accept/return DTOs, never JPA entities.

## Code Standards
- **Entity IDs:** Always `Long`.
- **Enums:** `@Enumerated(EnumType.STRING)`.
- **URLs:** All routes under `/v1`.
- **Business rules:** Live in Service classes only.
- **Class names:** `XxxController`, `XxxService`, `XxxRepository`, `XxxRequest`/`XxxResponse`.

## Flyway Migrations
- **Location:** `src/main/resources/db/migration/`.
- **Naming:** `V<n>__description.sql` (capital V, two underscores).
- **Rules:**
    - Never edit a migration after it's merged or applied to the DB.
    - If you need changes, create a new `V<n+1>` file.
    - Table creation order: dependencies first (users → seller_profile → product → ...).

## Development Workflow
```bash
./gradlew build -x test
java -jar build/libs/GharSe-backend-0.0.1-SNAPSHOT.jar
```
**Never use `./gradlew bootRun`** — it hangs at 80%.

## Database
- **Dev profile:** `application-dev.yaml` reads `DB_PASSWORD` from environment.
- **Set it:** `echo 'export DB_PASSWORD=your_password' >> ~/.bashrc && source ~/.bashrc`.
- **Test:** `mysql -u gharse_app -p gharse -e "SHOW TABLES;"`.

## Testing
- Every endpoint must be independently testable via Postman (NFR13).
- Global exception handler returns standardized JSON (see `ApiResponse.java`).

## Optional: real email notifications

By default, order-status notifications are only logged, not sent. You don't need anything below to run the app or the tests.

To send real email through Gmail, set these environment variables before starting the app:

| Variable | Purpose | Default |
|---|---|---|
| `NOTIFICATIONS_EMAIL_ENABLED` | Set to `true` to send real email | `false` |
| `GMAIL_USERNAME` | The Gmail address that sends the mail | (none) |
| `GMAIL_APP_PASSWORD` | A 16-character Google app password for that account | (none) |

Rules:

- Never commit the app password, and never paste it into chat or screenshots.
- If `NOTIFICATIONS_EMAIL_ENABLED=true` and either credential is missing, the app refuses to start. This is intentional.
- SMS is always simulated in Phase 1 (logged only).
- To create an app password: sign in to the Gmail account, enable 2-Step Verification, then open the App Passwords page and create one.