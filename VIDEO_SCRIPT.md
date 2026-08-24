# Video walkthrough script (8 minutes)

Record with OBS, Windows Game Bar (Win+G), or Loom. Show the IDE + a terminal. Keep logs visible for the request-id grep.

---

### 0:00–1:00 — Intro and scenario

“I’m [your name]. This is Keyloop Scenario A: the Unified Service Scheduler.

Dealerships still book work from a paper diary. The job is: an advisor requests a service for a **specific vehicle**, **service type**, and **site** at a **desired time**. We only confirm if a **bay** and a **qualified technician** are free for the **whole duration**, then we persist an appointment that ties customer, vehicle, technician, and bay.

I implemented the **backend** in Java 21 / Spring Boot. The client is mocked with OpenAPI and curl. I assumed one bay and one tech per job, weekday 08:00–17:00 in the dealer timezone, and no auth — all written in DESIGN.md.”

---

### 1:00–3:00 — System design

Open DESIGN.md. Trace the architecture diagram **left to right**, then the persistence row, then observability underneath.

“Start on the left: the **client**. That’s the mocked service advisor — OpenAPI at `/docs`, curl, or the test harness. There is no real UI.

Next, **SchedulerController**. Thin HTTP: validate ISO timestamps with an offset, map error codes to status. It does not decide whether a bay is free. `GET /availability` goes to AvailabilityService. `POST /appointments` goes to BookingService.

**BookingService** is the confirm path. Duration comes from the service catalog, not the client. The vehicle must belong to that customer. We lock the dealership row so two advisors cannot both take Pat. We re-check availability **inside that same transaction**, then insert a CONFIRMED appointment linking customer, vehicle, bay, and technician. `Idempotency-Key` means a retry is a replay, not a second job.

**AvailabilityService** is shared by GET and POST. Three queries: bays at the site, technicians qualified for the skill, busy intervals for the day. Matching is in memory — no nested SQL per slot. GET is a diary search; it does not reserve.

Those services call **AvailabilityEngine**: pure domain, no Spring, no database. Half-open overlap, dealer hours 08:00–17:00, 30-minute grid, first free bay and qualified tech for the **full** duration.

Under the services: **Spring Data JPA and Flyway** — schema and indexes. Demo is an H2 file; same mappings target Postgres, where we’d add gist exclusion constraints.

Underneath the whole path: **observability** — `X-Request-ID` on every response, JSON logs, Prometheus at `/metrics`.

The design point: one engine. GET is advisory. POST is authoritative. Same rules, so a slot we showed is a slot we can defend — unless we lose a race, which is 409.”

---

### 3:00–4:30 — Implementation highlight

Do **not** run tests here. Open three files in the IDE, scroll to the method, and point at the highlighted lines while you speak. Ctrl+P (or Cmd+P) to jump.

**Shot 1 — `BookingService.java`, method `book`**

Ctrl+P → `BookingService.java`. Ctrl+F → `book(`. Stay inside this method. Highlight one block, say the line, then move on. About 45 seconds.

**1. Lines 94–111 — `Idempotency-Key`**

Highlight the blank-key check, then the `existing.isPresent()` replay.

Say: “Every confirm needs an Idempotency-Key. If we have already seen that key with the same body, we replay the saved 201 — we do not insert a second appointment. Same key, different body, is 409. That is how a double-click from the advisor does not double-book.”

**2. Line 116 — `dealerships.lockById`**

Highlight:

```java
DealershipEntity dealer = dealerships.lockById(request.dealershipId())
```

Say: “This is a pessimistic lock on the dealership row. While this transaction is open, the next confirm at the same site waits. So two advisors cannot both look at the diary, both see the same technician free, and both write a booking.”

**3. Line 125 — vehicle ownership**

Highlight:

```java
if (!vehicle.getCustomerId().equals(customer.getId())) {
```

Say: “The vehicle on the request has to belong to that customer. If it does not, we stop here with 422. We are booking a real car for a real owner, not a free-floating slot.”

**4. Line 135 — duration from the catalog**

Highlight:

```java
Instant end = AvailabilityEngine.endAt(start, service.getDurationMinutes());
```

Say: “The client only sends the desired start. End time is start plus the duration on the service type — MOT is sixty minutes, major is three hours. The advisor cannot shorten a major to squeeze it in.”

**5. Lines 144 and 160 — assign, then persist**

Highlight `assignOrThrow`, then scroll to `appointments.save` and `"CONFIRMED"`.

Say: “assignOrThrow is the same availability engine GET uses, but it runs inside this locked transaction. If a bay and a qualified tech are free for the whole window, we insert a CONFIRMED appointment that ties customer, vehicle, technician, and bay. GET never writes that row. POST does.”

**Shot 2 — `AvailabilityEngine.java`, method `overlaps`**

Ctrl+P → `AvailabilityEngine.java`. Ctrl+F → `overlaps`. About 20 seconds. Do not scroll the rest of the class unless you have spare time — hours and the 30-minute grid live in `validateWindow` below.

**1. Lines 14–16 — class comment**

Highlight:

```java
 * Pure scheduling rules: half-open intervals, dealer hours, 30-minute grid.
 * Used by both availability search and booking confirmation.
```

Say: “This class is the rules. No Spring, no SQL. GET availability and POST confirm both call it, so a slot we showed is a slot we can defend.”

**2. Lines 36–38 — `overlaps`**

Highlight the method, especially the return:

```java
return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
```

Say: “Two jobs overlap only if each one starts before the other ends. That is a half-open interval: `[start, end)`. A job that ends at ten does not block a job that starts at ten — the bay and the technician are free on the boundary. Same predicate for a bay, a tech, or a vehicle.”

**Shot 3 — `SchedulerApiTest.java`, method `concurrentMajorBookingsYieldOneCreatedAndOneConflict`**

Ctrl+P → `SchedulerApiTest.java`. Ctrl+F → `concurrentMajor`. Click the method name so lines 141–169 are on screen. **Do not press the green Run triangle.**

Do three highlights, one at a time (mouse-select the line so it lights up on camera):

**1. Lines 148–149 — two `pool.submit`**

Select these two lines:

```java
Future<ResponseEntity<String>> a = pool.submit(() -> fire(start, "conc-a", ready, go));
Future<ResponseEntity<String>> b = pool.submit(() -> fire(start, "conc-b", ready, go));
```

Say: “Two HTTP POSTs fired at the same instant. Different idempotency keys — `conc-a` and `conc-b` — so this is two real bookings, not a retry.”

(`pool.submit` = put the call on a thread pool. `newFixedThreadPool(2)` two lines above is those two threads. `go.countDown()` on line 151 is the starting gun.)

**2. Line 166 — `SERVICE_MAJOR` inside `fire`**

Scroll a few lines down to the helper `fire(`. Highlight `SeedIds.SERVICE_MAJOR`:

```java
return http.exchange("/appointments", HttpMethod.POST, entity(start, SeedIds.SERVICE_MAJOR,
```

Say: “Both threads book a **major**. Only Pat is qualified, so they are fighting over one technician. The next argument picks a different vehicle per thread, so this is a bay/tech race, not `VEHICLE_OVERLAP`.”

**3. Lines 155–156 — `assertTrue` 201 and 409**

Scroll back up. Highlight:

```java
assertTrue(codes.contains(201) && codes.contains(409), "statuses=" + codes
```

Say: “We do not care which thread wins. One must be 201 created, the other 409 conflict. That is confirm under a race — not another endpoint.”

Then stop. Live demo is later.

---

### 4:30–6:00 — AI collaboration (~1 min spoken)

Open README.md, heading **AI collaboration narrative**. Do not read the page. Speak this (~150 words). Use leftover time to glance at DESIGN.md.

“GenAI was a collaborator, not an authority, and not a substitute for design. I coded only after the architecture was written down and I could defend it.

Design was thinking, not prompting. Direction first: Scenario A, bay plus qualified technician, backend only, client mocked. Then research, then the stack that fitted that.

The prompts were not ‘build this.’ They were: this is the intent; how would you approach it; what is missing; where does this fail? The model could change a decision if the argument was sound. I rejected anything that broke ownership or the booking model.

I wrote the decisions in DESIGN.md so the model could not silently rewrite them. That file is the source of truth. I asked the model to quiz me on the system, not on syntax.

Before code I asked how it breaks. I kept one engine for GET and POST, duration from the catalogue, half-open intervals, a lock, idempotency. I rejected nested SQL per slot, caching availability as truth, and OpenTelemetry in a take-home.

Generating code is cheap. Deciding whether it should exist is not. System design is the spec. GenAI compiles it. It does not choose the architecture.”

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
