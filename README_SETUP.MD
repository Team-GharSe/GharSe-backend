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