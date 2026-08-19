# Video walkthrough script (8 minutes)

Record with OBS, Windows Game Bar (Win+G), or Loom. Show the IDE + a terminal. Keep logs visible for the request-id grep.

---

### 0:00–1:00 — Intro and scenario

“I’m [your name]. This is Keyloop Scenario A: the Unified Service Scheduler.

Dealerships still book work from a paper diary. The job is: an advisor requests a service for a **specific vehicle**, **service type**, and **site** at a **desired time**. We only confirm if a **bay** and a **qualified technician** are free for the **whole duration**, then we persist an appointment that ties customer, vehicle, technician, and bay.

I implemented the **backend** in Java 21 / Spring Boot. The client is mocked with OpenAPI and curl. I assumed one bay and one tech per job, weekday 08:00–17:00 in the dealer timezone, and no auth — all written in DESIGN.md.”

---

### 1:00–3:00 — System design

Open DESIGN.md. Point at the mermaid diagram.

“HTTP hits a thin controller. `AvailabilityEngine` is pure domain: half-open intervals, 30-minute grid, London hours. `AvailabilityService` loads bays, qualified techs, and the day’s busy rows — three queries — and matches in memory. `BookingService` locks the dealership, re-runs that same engine, then inserts.

That single engine is the point: GET /availability is advisory. POST /appointments is authoritative. Same rules, so a slot we showed is the slot we can defend — unless we lose a race, which becomes 409.”

Mention indexes and “H2 now, Postgres exclusion constraints later.”

---

### 3:00–4:30 — Implementation highlight

Open `BookingService.book` and `AvailabilityEngine.overlaps`.

“Duration comes from the catalog, not the client. Vehicle must belong to the customer. Only Pat can do a major service, so two majors at the same time is the concurrency test. We require `Idempotency-Key` so a retry is not a double book.”

Show a test name: `concurrentMajorBookingsYieldOneCreatedAndOneConflict`.

---

### 4:30–6:00 — AI collaboration (1–2 min)

“I directed the agent rather than pasting the PDF and hoping. Two reviews — client and architect — forced desired-time POST, timezone, metrics, and idempotency into the plan before code. I switched the stack from Python to Java when the machine had Maven and no CPython. I verified by running Maven tests and reading the overlap predicate myself. I kept OpenTelemetry out of the repo on purpose: request IDs plus Prometheus are enough to demonstrate observability here.”

---

### 6:00–8:00 — Live demo

Terminal:

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -q spring-boot:run
```

Browser: http://localhost:8080/docs — show POST /appointments.

Then PowerShell happy-path `Invoke-RestMethod` from the README. Show 201 body with customer, vehicle, bay, technician.

Second POST same vehicle/time, new idempotency key → 409 `VEHICLE_OVERLAP`. Point at `X-Request-ID`. Open `/metrics` and mention `booking_conflicts`.

“What I learned: availability search is easy; **confirm** under concurrency is the product. The challenge was keeping one engine and a lock, not adding more endpoints.”

Stop at 8:00. Do not overrun 10:00.
