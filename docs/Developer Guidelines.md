# CallShield – Developer Guidelines

> **2026-07-11: the Android app no longer makes any network calls.** Remote
> reputation lookup, report/correct submission, and seed DB delta updates were
> all removed client-side (backend Edge Functions are untouched and still
> deployed, just unreached). Sections below describing backend/remote behavior
> document the rules that applied when those calls existed and that would
> apply again if remote lookup is reintroduced — they are not describing the
> app's current live behavior. See `CLAUDE.md` Architecture section for specifics.

## Privacy Rules (Non-Negotiable)

These apply to every line of code, every API, and every database schema:

- **Never store raw phone numbers** on the backend. Hash them locally on the device before any network call.
- **Never collect or transmit:** contacts, call logs, SMS content, call recordings, device fingerprint, or advertising ID.
- **Never send a number to the backend without hashing it first.** The hash function must be applied client-side, not server-side.
- **Never auto-block a number based on a single report.** Confidence escalation requires multiple unique reporters.
- **Never block silently.** Every blocked call must produce a visible indicator the user can act on.

---

## Hashing Convention

All phone numbers must be hashed using **HMAC-SHA256** with a static salt bundled in the app binary before any network call or backend storage. Plain SHA-256 is not acceptable — Indian mobile numbers have a small enough space (~1 billion) to be fully rainbow-tabled in hours.

Rules:
- Normalize to E.164 format first (e.g., `+919876543210`), then apply HMAC-SHA256.
- The HMAC salt is a static value bundled in the app binary. It is not fetched from the backend — this ensures hashing works on first launch with no internet connection and eliminates a startup network dependency.
- The salt is not a secret that needs rotation. Its purpose is to prevent pre-computation attacks against the number space, not to hide data from the server. If the salt is ever compromised, all existing hashes remain valid (the server cannot reverse them without enumerating all possible numbers anyway).
- The hash is the **only** number-derived identifier sent to or stored on Supabase.

---

## Device Token Architecture

A device token is a stable, anonymous identifier used for rate limiting, trust scoring (Phase 2), and family pairing (Phase 3). It must be designed correctly in Phase 1 — every abuse protection mechanism depends on it.

Rules:
- Generated once on first install as a cryptographically random UUID (128-bit).
- Stored in Android Keystore (hardware-backed where available). Never in SharedPreferences or plaintext storage.
- The raw UUID never leaves the device. Only `HMAC-SHA256(uuid, bundled_salt)` is sent to the backend.
- The token **survives app updates** but **not full uninstalls**. Android Keystore entries are tied to the app's UID — a full uninstall deletes the UID and the Keystore entry with it. On reinstall, a new token is generated. This means a reinstalled app is treated as a new device for rate-limiting and deduplication purposes. This is an inherent Android platform limitation, not a bug to be worked around.
- Never link the device token to any user account, Google account, or advertising ID.

---

## Call Screening Architecture

- The call screening decision (allow / silence / reject) must complete entirely **within Android's screening time window**. All checks are on-device now, so this is comfortably true — if a remote check is ever reintroduced it must stay non-blocking with its own hard timeout, same as the removed 1500ms remote-reputation budget.
- Offline must work. The app must function correctly with no internet connection using local data alone — trivially true today since there's no network dependency at all.

### Lookup Order and Conflict Resolution

`ScreeningOrchestrator` applies checks in this strict priority order. **The highest-priority definitive match wins. Once a definitive decision is reached, remaining checks are skipped.**

| Priority | Check | Definitive if |
|---|---|---|
| 1 | Personal whitelist | Number is whitelisted → Always Allow, skip all further checks |
| 2 | Personal blocklist | Number is blocked → Reject |
| 3 | Prefix rules | Number matches a user-defined prefix → Reject or Silence per rule |
| 4 | Private/hidden number | No caller ID and user has enabled hidden number blocking → Reject |
| 5 | Seed DB | Number found in seed DB → Known Spam → Silence (currently never populated — dead code, see banner above) |
| 6 | Default | No match → Allow (Unknown) |

The personal whitelist always overrides everything.

### Circuit Breaker (removed 2026-07-11, historical reference)

When remote reputation lookup existed, `ReputationRemoteDataSource`/`CircuitBreaker` implemented a three-state circuit breaker (Closed → Open → Half-open) to stop the 1500ms timeout from compounding under sustained backend failure. Deleted along with the remote call — reintroduce this pattern if remote lookup ever comes back.

---

## Confidence Score Formula

Backend-only as of 2026-07-11 — the client never calls `GET /reputation`, so this score is never seen by the app. Kept here for when/if remote lookup returns. The confidence score is computed server-side by the Edge Function and stored in the `reputation` table. The formula is hardcoded in the Edge Function for Phase 1 — there is no config endpoint. If parameters need tuning, deploy a new Edge Function (Supabase deploys in seconds).

**Phase 1 formula:**

```
base_score = min(unique_reporters / 10, 1.0)
recency_decay = max(0, 1 - days_since_last_report / 90)
confidence_score = base_score * recency_decay
```

- `unique_reporters` of 10 = maximum base score (1.0).
- Scores decay to 0 linearly over 90 days with no new reports.
- After 12 months of zero reports, the record is purged.

---

## Supabase Backend Rules

- Row-level security (RLS) must be enabled on all tables. No table should be publicly readable or writable without explicit policy.
- Edge Functions are the **only** entry point — no direct client access to any table.
- Rate limiting is enforced at the Edge Function level per `device_token_hash` before any DB write.
- Do not store session tokens, user IDs, or any linkable identity alongside reputation records.
- The reputation schema stores derived state (`confidence_score`) alongside raw counters for read performance. The `report_events` append-only table is the source of truth for score recomputation (see Tech Stack schema).
- In Phase 1, category is stored as a single `category TEXT` column in the `reputation` table. The `number_categories` breakdown table is added in Phase 2 when category voting is implemented. The `report_events` table already records category per event, so the data is fully available for migration when the time comes.

---

## Abuse Resistance

All of this section describes backend-side protections for endpoints the client no longer calls (`report/`, `reputation/`) — still true of the backend in isolation, moot until the client talks to it again.

### Play Integrity API (Phase 2)
From Phase 2 onward, `POST /report` must include a Play Integrity token alongside the device token hash. The Edge Function verifies it server-side before processing the report. This prevents multi-reinstall Sybil attacks at scale.

In Phase 1, basic per-device rate limiting (enforced by `reporter_deduplication` and a time-window check) is sufficient — the abuse surface does not justify Play Integrity's implementation complexity and latency (~200–500ms Google round-trip) at MVP scale.

### Report Velocity Quarantine (Phase 2)
If more than 5 reports for the same `number_hash` arrive within 60 minutes, incoming reports are held in a `quarantine_queue` for 24 hours before being applied to the reputation counters. This prevents coordinated bombing campaigns from taking immediate effect.

### Reputation Lookup Rate Limiting
`GET /reputation` is rate-limited to **60 requests per device per hour**. This prevents hash oracle enumeration attacks where an adversary reverse-engineers numbers from lookup patterns.

### Category Voting (Phase 2)
When multiple categories are reported for the same number, the displayed category is determined by majority vote. A category requires a lead of ≥ 3 reports over the next closest category before being promoted. This prevents a single bad actor from flipping a number's category.

---

## Feature Gating and Subscription Verification

- All Pro-only features must be gated behind a subscription check at the point of use, not just the UI entry point.
- If a subscription lapses, Pro-only Advanced Blocking options (VIP-only, country filter, blocklist aging, Night Guard/Work Focus REJECT action) must disable gracefully without losing the user's configuration.
- **Subscription state should be verified server-side** — the intended design validates the Google Play purchase token via the Google Play Developer API through a Supabase Edge Function (`POST /verify-subscription`). This is aspirational, not current behavior: `BillingManager` has never called this endpoint — entitlement relies solely on the local Play Billing client. Treat this as an open gap, not a description of what exists.
- Use a single source of truth for entitlement state — do not scatter subscription checks across the codebase.

---

## OEM Compatibility

- Battery optimization exemption guidance must be shown proactively for MIUI, Samsung One UI, and Realme UI — these OEMs aggressively kill background services.
- Test call screening on physical devices for each major OEM before any release. Emulators are insufficient for Call Screening API validation.
- No ANRs, no background crashes. These are launch blockers.

---

## Phase Boundaries

Features scoped to a future phase must not be partially built into the current phase:

| Feature | Phase |
|---|---|
| Behavioral detection (call frequency, burst calls, short-ring) | 2 |
| Report velocity quarantine | 2 |
| Category voting system | 2 |
| Weighted / decaying reputation scoring | 2 |
| Encrypted cloud sync | 3 |
| Family Protection Mode (QR pairing) | 3 |
| On-device ML inference | 4 |
| iOS support | 4 |

**Exception:** The `report_events` table, `reporter_deduplication` table, and `negative_signals` column must be built in Phase 1 even though weighted scoring is Phase 2. These are schema decisions that cannot be retrofitted cheaply. The `number_categories` table is added in Phase 2 alongside category voting — the data is preserved in `report_events` and can be migrated when needed.

---

## What Goes Where

| Concern | Location |
|---|---|
| Blocking decision logic | On-device only |
| Behavioral detection scoring | On-device only |
| Number hashing (HMAC-SHA256) | On-device, before any network call |
| Device token | Android Keystore |
| Reputation counters & confidence scores | Supabase (hash-keyed) — orphaned, client no longer reads/writes this |
| Local blocklist, whitelist & prefix rules | Room (SQLite) |
| Seed spam DB | Room table, but never populated — no bundled asset, no download; dead code kept intentionally |
| Recent call events buffer (Phase 2) | Room (SQLite), 24h hard TTL |
| Encrypted backup blobs (Phase 3) | Supabase Storage |
| call_decision_audit log | Room (SQLite), on-device only |

---

## Metrics & Analytics

- Track only aggregate, anonymous events: blocks triggered, reports submitted, Advanced Blocking feature adoption rate, subscription conversion.
- No user-level event streams. No session replay. No funnel tracking tied to a device identity.
- If a third-party analytics SDK is added, audit it for data collection before integrating.
