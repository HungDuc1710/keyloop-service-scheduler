# Keyloop Unified Service Scheduler

Java/Spring Boot backend for **Keyloop Scenario A**: book a service appointment only when a **service bay** and a **qualified technician** are free for the full job.

This app is self-contained: **JDK 21**, embedded **H2**, no Docker, no extra services. First Maven download needs internet (Maven Central). After that it runs offline.

---

## 0. What you need

| Need | Details |
|---|---|
| **JDK 21** | Required. Spring Boot 3.4 will not start on Java 17. |
| **Internet (first time)** | Downloads Maven (via the wrapper) and project dependencies. |
| **Port 8080 free** | Default listen port. |
| **Not required** | A global Maven install, Docker, Postgres, Redis, Node. |

Check Java:

1. Open a terminal.
   - **Windows:** Start menu → type `PowerShell` → Open.
   - **macOS:** Spotlight → Terminal.
   - **Linux:** open your terminal app.
2. Run:

```bash
java -version
```

**Expected:** a line containing `21` (for example `openjdk version "21.0.12"`).

If the command is not found, or the version is 8 / 11 / 17: install [Eclipse Temurin 21](https://adoptium.net/) and open a **new** terminal.

**Optional — this terminal only.** `java -version` can show 21 while `.\mvnw.cmd` still fails with `JAVA_HOME is not defined correctly`. Maven reads `JAVA_HOME`, not `PATH`. Set `JAVA_HOME` to **your** JDK 21 home folder (the folder that contains `bin\java.exe`, not `bin` itself).

Find it on Windows:

```powershell
(Get-Command java).Source
```

Example output: `C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot\bin\java.exe` — then the home folder is two levels up. Use **your** path:

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot"
```

macOS / Linux:

```bash
dirname "$(dirname "$(command -v java)")"
export JAVA_HOME=/path/to/jdk-21
```

---

## 1. Go to the project folder

The repository root is the folder that contains `pom.xml`, `mvnw`, and `mvnw.cmd`.

**Windows (PowerShell)** — adjust the path if you cloned elsewhere:

```powershell
cd path\to\keyloop-service-scheduler
dir pom.xml, mvnw.cmd
```

Example: `cd D:\keyloop-service-scheduler`

**macOS / Linux:**

```bash
cd path/to/keyloop-service-scheduler
ls pom.xml mvnw
```

Example: `cd ~/keyloop-service-scheduler`

**Expected:** `pom.xml` is listed. If not, you are in the wrong directory.

On macOS/Linux, if `./mvnw` says permission denied:

```bash
chmod +x mvnw
```

The Maven wrapper is the command. Do not install Maven yourself.

- **Windows:** `.\mvnw.cmd`
- **macOS / Linux:** `./mvnw`

With no extra arguments, that **builds** the project (`package`). To start the server, use section 4 (`spring-boot:run`).

(If you already have Maven 3.9+ on your PATH, `mvn` works the same way.)

---

## 2. Build

Still in the repository root, run **one** of:

**Windows:**

```powershell
.\mvnw.cmd
```

**macOS / Linux:**

```bash
./mvnw
```

That is the same as `.\mvnw.cmd package` / `./mvnw package` (`pom.xml` sets this as the default). Faster first build (skip tests): `.\mvnw.cmd -DskipTests package`.

The first run can take several minutes (wrapper downloads Maven 3.9.9, then dependencies).

**Expected (end of log):**

```
[INFO] BUILD SUCCESS
```

and the file `target/service-scheduler-1.0.0.jar` exists.

**If it fails:**

- `Unsupported class file major version` / wrong Java → install JDK 21 and retry in a new terminal.
- Download timeouts → check internet / proxy; retry the same command (Maven resumes).

---

## 3. Automated tests

From the same folder:

**Windows:**

```powershell
.\mvnw.cmd test
```

**macOS / Linux:**

```bash
./mvnw test
```

You do **not** need the server running. Tests use in-memory H2 and a fixed clock (`2026-08-19T08:00:00Z`).

**Expected (end of log):**

```
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| Class | Tests | What they prove |
|---|---|---|
| `AvailabilityEngineTest` | 7 | Overlap, hours, grid, shortage classification (no Spring) |
| `SchedulerApiTest` | 9 | Health, happy-path booking (customer + vehicle + bay + technician), ownership, vehicle double-book, cancel, idempotency replay, naive timestamp rejected, concurrent majors `{201, 409}` |

A failure is a red `FAILURE` / `ERROR` line and `BUILD FAILURE`. Re-run the same command; do not start the app to “fix” tests.

Run one class only (optional):

```bash
./mvnw test -Dtest=AvailabilityEngineTest
./mvnw test -Dtest=SchedulerApiTest
```

(Windows: prefix with `.\mvnw.cmd`.)

---

## 4. Run the application

Use a **single** process (H2 is single-writer). Stop anything else on port 8080 first.

**Preferred — from source**

**Windows:**

```powershell
.\mvnw.cmd spring-boot:run
```

**macOS / Linux:**

```bash
./mvnw spring-boot:run
```

**Or from the jar** (after step 2):

```bash
java -jar target/service-scheduler-1.0.0.jar
```

Leave this terminal open. Logs are JSON lines on stdout.

**Expected:** wait until you see Tomcat on 8080 and the Spring Boot started line, for example:

```
Tomcat started on port 8080 (http) with context path '/'
Started SchedulerApplication
```

Flyway creates the schema; the seeder loads demo dealership / bays / technicians on first start. Data file: `./data/scheduler.mv.db`.

**Clean demo (optional):** stop the app (`Ctrl+C`), delete the `data` folder, start again.

**If port 8080 is in use:** stop the other process, or set `SERVER_PORT=8081` (then use `8081` in the URLs below).

**Sanity check in a second terminal** (app still running):

Open a browser to http://localhost:8080/health

**Expected page / JSON:**

```json
{"status":"UP"}
```

Also:

| URL | Expected |
|---|---|
| http://localhost:8080/ready | `{"status":"READY"}` |
| http://localhost:8080/docs | Swagger UI (mocked client) |
| http://localhost:8080/metrics | Prometheus text (includes `jvm_` or `http_requests`) |

If `/health` does not load, the process is not up yet — watch the first terminal for the `Started SchedulerApplication` line.

---

## 5. Manual test in the browser (Swagger)

Keep the app running. These IDs are seeded on startup.

Workshop hours: **08:00–17:00 Monday–Friday, Europe/London**. API timestamps are **UTC**. The walkthrough uses **Friday 3 September 2027, 09:00 London = `2027-09-03T08:00:00Z`** (BST). If you pick another day, it must be a **future weekday**; weekends return `OUTSIDE_HOURS`, past times return `PAST_START`.

### 5.1 Open the mocked client

1. In Chrome/Edge/Firefox go to **http://localhost:8080/docs**
2. You should see **OpenAPI** / Swagger UI with the title **OpenAPI definition** (or similar) and a list of operations (`GET`, `POST`).
3. If the page is blank, wait 2 seconds and refresh once.

### 5.2 List the dealership

1. Click the row **`GET /dealerships`** so it expands.
2. Click the **Try it out** button (right side).
3. Click **Execute**.
4. Under **Server response**, **Code** should be **200**.
5. In **Response body**, parse:
   - `id`: `11111111-1111-1111-1111-111111111111`
   - `name`: `Keyloop Motors Oxford`
   - `timezone`: `Europe/London`

### 5.3 List bays, technicians, service types

1. Click **`GET /dealerships/{id}/resources`**.
2. Click **Try it out**.
3. In **id**, paste: `11111111-1111-1111-1111-111111111111`
4. Click **Execute**.
5. **Code** = **200**.
6. Parse the body:
   - `serviceTypes` includes `MOT` (60 min, skill `MOT`) and `MAJOR` (180 min, skill `MECHANICAL`).
   - `bays` includes **Bay 1** and **Bay 2**.
   - `technicians` includes **Pat Okonkwo** (`MOT`, `MECHANICAL`) and **Sam Chen** (`MOT` only).

### 5.4 Search availability (does not reserve)

1. Click **`GET /availability`**.
2. Click **Try it out**.
3. Fill:
   - `dealershipId`: `11111111-1111-1111-1111-111111111111`
   - `serviceTypeId`: `44444444-4444-4444-4444-444444444444` (MOT)
   - `date`: `2027-09-03`
4. Click **Execute**.
5. **Code** = **200**.
6. **Response body** is a JSON **array** of slots. Each slot has `startAt`, `endAt`, `exampleBayName`, `exampleTechnicianName`.
7. Find the object whose `startAt` is `2027-09-03T08:00:00Z`. That is 09:00 London. `endAt` should be `2027-09-03T09:00:00Z` (60-minute MOT). Example names should be a bay (`Bay 1` or `Bay 2`) and a technician (`Pat Okonkwo` or `Sam Chen`).

This GET does **not** lock the slot.

### 5.5 Book the MOT (happy path)

1. Click **`POST /appointments`**.
2. Click **Try it out**.
3. Find the header **`Idempotency-Key`**. Type: `judge-mot-1`
4. Replace the **Request body** with:

```json
{
  "customerId": "22222222-2222-2222-2222-222222222222",
  "vehicleId": "33333333-3333-3333-3333-333333333333",
  "dealershipId": "11111111-1111-1111-1111-111111111111",
  "serviceTypeId": "44444444-4444-4444-4444-444444444444",
  "startAt": "2027-09-03T08:00:00Z"
}
```

5. Click **Execute**.
6. **Code** should be **201**.
7. Parse **Response body**:
   - `status`: `CONFIRMED`
   - `customerName`: `Alex Rivera`
   - `vehicleRegistration`: `AB12CDE`
   - `serviceTypeCode`: `MOT`
   - `serviceBayName`: `Bay 1` or `Bay 2`
   - `technicianName`: `Pat Okonkwo` or `Sam Chen`
   - `startAt`: `2027-09-03T08:00:00Z`
   - `endAt`: `2027-09-03T09:00:00Z`
8. Copy the appointment `id` (UUID). You need it for cancel (step 5.8).

### 5.6 Same vehicle, same window (conflict)

1. Still on **`POST /appointments`** (or expand it again → **Try it out**).
2. Change **`Idempotency-Key`** to `judge-mot-2` (a **new** key; same key would replay 200).
3. Leave the **same JSON body** as step 5.5.
4. Click **Execute**.
5. **Code** should be **409**.
6. Parse **Response body**:
   - `code`: `VEHICLE_OVERLAP`
   - `message`: a short reason
   - `requestId`: a UUID (also in response header `X-Request-ID`)
7. Optional: in the **server terminal**, search the JSON logs for that `requestId` — you should see `event=booking.conflict`.

### 5.7 Two major services at the same time (only one MECHANICAL tech)

Only **Pat** has `MECHANICAL`. Two overlapping majors must be `{201, 409}`.

1. **`POST /appointments`** → **Try it out**.
2. `Idempotency-Key`: `judge-major-1`
3. Body (Alex / BMW, **MAJOR** `5555…`, **10:00 London = 09:00Z**):

```json
{
  "customerId": "22222222-2222-2222-2222-222222222222",
  "vehicleId": "33333333-3333-3333-3333-333333333333",
  "dealershipId": "11111111-1111-1111-1111-111111111111",
  "serviceTypeId": "55555555-5555-5555-5555-555555555555",
  "startAt": "2027-09-03T09:00:00Z"
}
```

4. **Execute** → **201**, `technicianName` is **Pat Okonkwo**, `endAt` three hours later (`2027-09-03T12:00:00Z`).
5. Change `Idempotency-Key` to `judge-major-2`.
6. Body (Jordan / VW, same start, same MAJOR):

```json
{
  "customerId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "vehicleId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "dealershipId": "11111111-1111-1111-1111-111111111111",
  "serviceTypeId": "55555555-5555-5555-5555-555555555555",
  "startAt": "2027-09-03T09:00:00Z"
}
```

7. **Execute** → **409**, `code`: **`NO_TECH`**.

### 5.8 Cancel and confirm the diary

1. Click **`GET /appointments`**.
2. **Try it out** → `dealershipId` = `11111111-1111-1111-1111-111111111111` → **Execute**.
3. **200**. The array includes the MOT and the major you created (`status` `CONFIRMED`).
4. Click **`POST /appointments/{id}/cancel`**.
5. **Try it out**. Paste the MOT appointment `id` from step 5.5 into **id**.
6. **Execute** → **200**, `status`: **`CANCELLED`**.
7. Repeat **`GET /availability`** (step 5.4). The `2027-09-03T08:00:00Z` MOT slot should appear again (cancel freed the bay and technician; the vehicle is free too).

### 5.9 Stop the app

In the terminal where the server is running, press **Ctrl+C**.

**Expected:** the process exits; http://localhost:8080/health no longer loads.

---

## Seed IDs (copy-paste)

| Resource | Name / code | ID |
|---|---|---|
| Dealership | Keyloop Motors Oxford (`Europe/London`) | `11111111-1111-1111-1111-111111111111` |
| Customer | Alex Rivera | `22222222-2222-2222-2222-222222222222` |
| Vehicle | BMW 3 Series `AB12CDE` | `33333333-3333-3333-3333-333333333333` |
| Other customer | Jordan Blake | `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa` |
| Other vehicle | VW Golf `XY99ZZZ` | `bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb` |
| Service | MOT (60 min, skill MOT) | `44444444-4444-4444-4444-444444444444` |
| Service | Major service (180 min, skill MECHANICAL) | `55555555-5555-5555-5555-555555555555` |
| Bay 1 | interchangeable | `66666666-6666-6666-6666-666666666666` |
| Bay 2 | interchangeable | `77777777-7777-7777-7777-777777777777` |
| Technician Pat | MOT + MECHANICAL | `88888888-8888-8888-8888-888888888888` |
| Technician Sam | MOT only | `99999999-9999-9999-9999-999999999999` |

## AI collaboration narrative

GenAI was treated as a collaborator, not an authority, and not as a substitute for design. Implementation started only after the architecture was clear enough to write down and defend.

A large part of the design phase was thinking, not prompting. Direction came first, for example: Scenario A (resource-constrained booking of a bay and a qualified technician), backend-only, REST plus a persistent store, client mocked. Research followed that direction, then chose the tech stack suitable for it.

The prompts I used were not just “build this.” They were closer to: this is the intent; how would you approach it; what is missing; what are the alternatives; where does this fail? The model was allowed to change a decision when the argument was sound, and suggestions were rejected when they violated ownership or the booking model. The goal was to stress-test the design, not to make the model agree.

Once the direction was stable, it was written down so the model could not silently rewrite it. DESIGN.md is the record of decisions: if approach A was dropped for B, the reason stays in the document. The same notes cover request flow, endpoints, data movement, service boundaries, and failure behaviour. That document is the source of truth. The model was then asked to quiz me on the system and not on syntax.

Before code, the model was asked how the system breaks: races on the same slot, inconsistent diary vs confirm, retries, missing auth (assessment assumption), and edge cases on hours and grid. Some answers were noise; some were kept (one engine for GET and POST, duration from the catalogue, half-open intervals, pessimistic lock, idempotency, metrics in scope). Optional metrics, nested per-slot SQL, caching availability as truth, and shipping an OpenTelemetry SDK in the take-home were rejected.

Only after that pool existed was the model used to write the implementation. The cost of generating code is low; the cost of deciding whether that code should exist is not. GenAI made the unhappy path, as well as the happy path, cheaper to explore.

The model is good at turning a specification into code. Responsibility stays on defining what must be compiled: what exists, why, constraints, trade-offs, what must never happen (double-book a bay or technician), and how correctness is shown. System design is that specification. GenAI compiles it. It does not choose the architecture.

## License

Demonstration code for a technical assessment.
