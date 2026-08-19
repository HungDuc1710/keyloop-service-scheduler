# Keyloop Unified Service Scheduler

Java/Spring Boot backend for **Keyloop Scenario A**: book a service appointment only when a **service bay** and a **qualified technician** are free for the full job.

System design: [DESIGN.md](DESIGN.md). Video walkthrough script: [VIDEO_SCRIPT.md](VIDEO_SCRIPT.md).

## Prerequisites

- JDK 21+
- Maven 3.9+

On this machine:

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## Build, test, run

```powershell
mvn test
mvn spring-boot:run
```

The app listens on `http://localhost:8080`. Flyway migrates the schema and the seeder loads demo data on first start. Data is stored in `./data/scheduler.mv.db` (H2 file).

- OpenAPI UI (mocked client): http://localhost:8080/docs
- Health: http://localhost:8080/health
- Ready: http://localhost:8080/ready
- Prometheus: http://localhost:8080/metrics

Run a **single** process for the demo (H2 is single-writer).

## Seed personas

| Resource | Name / code | ID |
|---|---|---|
| Dealership | Keyloop Motors Oxford (`Europe/London`) | `11111111-1111-1111-1111-111111111111` |
| Customer | Alex Rivera | `22222222-2222-2222-2222-222222222222` |
| Vehicle | BMW 3 Series `AB12CDE` | `33333333-3333-3333-3333-333333333333` |
| Other customer | Jordan Blake | `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa` |
| Other vehicle | VW Golf `XY99ZZZ` | `bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb` |
| Service | MOT (60 min, skill MOT) | `44444444-4444-4444-4444-444444444444` |
| Service | Major service (180 min, skill MECHANICAL) | `55555555-5555-5555-5555-555555555555` |
| Bay 1 / Bay 2 | interchangeable workshop bays | `6666…` / `7777…` |
| Technician Pat | MOT + MECHANICAL | `88888888-8888-8888-8888-888888888888` |
| Technician Sam | MOT only | `99999999-9999-9999-9999-999999999999` |

Hours: 08:00–17:00 Monday–Friday London time. Use UTC instants in the API (`2026-08-21T08:00:00Z` is 09:00 BST).

## curl examples (PowerShell)

Happy path — desired time 09:00 London on Friday 21 Aug 2026:

```powershell
$headers = @{
  "Content-Type" = "application/json"
  "Idempotency-Key" = "demo-mot-1"
}
$body = @{
  customerId = "22222222-2222-2222-2222-222222222222"
  vehicleId = "33333333-3333-3333-3333-333333333333"
  dealershipId = "11111111-1111-1111-1111-111111111111"
  serviceTypeId = "44444444-4444-4444-4444-444444444444"
  startAt = "2026-08-21T08:00:00Z"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8080/appointments -Headers $headers -Body $body
```

Conflict — book the same MOT slot again with a new idempotency key (same vehicle → `VEHICLE_OVERLAP`). For a technician clash, book two **major** services at the same time (only Pat is MECHANICAL-qualified): one 201, one 409 `NO_TECH`.

```powershell
Invoke-RestMethod http://localhost:8080/availability?dealershipId=11111111-1111-1111-1111-111111111111&serviceTypeId=44444444-4444-4444-4444-444444444444&date=2026-08-21
```

Grep a failed booking by request id (JSON logs on stdout): search for the `X-Request-ID` header returned on the 409.

## AI collaboration narrative

**Strategy.** I used Cursor as a pair-programmer against a fixed challenge brief. First I chose Scenario A (richest domain: dual-resource booking and races). After a Python plan, the environment had Maven but no CPython, and I switched to a **Java 21 + Spring Boot** stack that also matches typical automotive-retail JVM services. Two review passes (a skeptical dealership client and a solution architect) ran *before* implementation so assumptions, timezone, ownership, observability, and idempotency were specified rather than invented mid-code.

**Guiding the model.** Prompts constrained the design: one availability engine for GET and POST, duration from catalog, half-open intervals, dealership-scoped queries, no nested SQL, pessimistic lock on confirm, metrics in-scope, OpenTelemetry out of the repo. Generated files were treated as drafts.

**Verification.** I compiled and ran `mvn test`. Domain tests lock overlap and close-of-day math without Spring. HTTP tests cover happy path (four associations), ownership, vehicle double-book, cancel-releases-slot, idempotency replay, naive timestamps, and a concurrent major-service race expecting `{201, 409}`. I rejected “optional Prometheus” and cartesian availability queries from earlier drafts.

**Quality ownership.** The scheduling rules live in `AvailabilityEngine` (pure Java) so they can be reasoned about without a container. Persistence is Flyway-validated JPA. I would not submit code I had not run. Remaining honest limits (H2 vs Postgres gist exclusions, no auth, no tracing SDK) are written in DESIGN.md rather than pretended.

## License

Demonstration code for a technical assessment.
