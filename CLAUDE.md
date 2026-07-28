# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**CallShield** — a privacy-first spam and scam call protection app for Android, targeting the Indian market. No contact uploads. No ads. No raw phone number storage. Phases 1–5 are implemented on `dev`: Android app under `android/`, Supabase backend under `supabase/`.

Full specs: `docs/PRD/`, `docs/Tech Stack.md`, `docs/Developer Guidelines.md`, `docs/Wireframes.md`

---

## Commands

Android (from `android/`; Java may not be on `PATH` — prefix with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` if `./gradlew` fails to find a JDK):
```
./gradlew assembleDebug              # debug build
./gradlew installDebug                # build + install to connected device/emulator
./gradlew testDebugUnitTest           # unit tests (reports: app/build/reports/tests/testDebugUnitTest/)
./gradlew lintDebug                   # lint
./gradlew bundleRelease               # signed release AAB (needs key.properties + upload-keystore.jks)
```
Single test class: `./gradlew testDebugUnitTest --tests "com.fenn.callshield.util.PhoneNumberHasherTest"`. There is no `src/androidTest` — no instrumented tests exist yet.

`./run_android.sh` wraps `installDebug`, optionally passing `-PHMAC_SALT=...` read from `android/local.properties`. The app no longer calls Supabase directly (see Architecture below), so no Supabase config is required to build or run it.

Backend (from repo root, requires Supabase CLI):
```
./run_backend.sh                                  # supabase start + db reset/migration up + functions serve
supabase db reset                                 # replay all migrations locally
supabase functions serve --env-file <path>         # serve Edge Functions locally
supabase link --project-ref $PROJECT_REF && supabase db push --include-all   # deploy migrations
supabase functions deploy --no-verify-jwt --project-ref $PROJECT_REF          # deploy functions
```
Local ports (`supabase/config.toml`): API 43210, DB 43211, Studio 43212, Inbucket 43213. `enable_signup=false` — there is no user-account system, only device-token auth.

No Edge Function test files exist yet (`*.test.ts`); CI no-ops that step until some are added.

---

## Tech Stack

- **Android:** Kotlin 2.0, Jetpack Compose, Hilt, Room (SQLite), DataStore. AGP 8.6.1, compileSdk/targetSdk 35, JVM target 17. No HTTP client — the app makes zero network calls (see Architecture).
- **Backend:** Supabase — PostgreSQL + Edge Functions (TypeScript/Deno), RLS enabled, no direct client DB access. Deployed and untouched, but currently orphaned — the Android app doesn't call it.
- **Key Android API:** Call Screening API — app must register as the device's call screening service.
- **Monetization:** Google Play Billing Library — freemium, ₹399/year primary / ₹49/month secondary.
- **Min SDK:** Android 10 (API 29).

---

## Architecture

**Pattern:** Clean Architecture + MVVM. Dependencies point inward — domain layer has zero Android/Supabase imports.

```
UI Layer (Compose + ViewModels)
  → Domain Layer (Use Cases + Entities — pure Kotlin)
    → Data Layer (Repositories + Room)
```

**`CallScreeningService`** is a separate Android Service, outside the MVVM flow. It has a strict time budget:

```
CallScreeningService
  → ScreeningOrchestrator (injectable, unit-testable)
    → [parallel] LocalBlocklistCheck, SeedDatabaseCheck, PrefixRuleCheck
  → CallDecision (Allow / Silence / Reject)
```

**The app no longer makes any network calls at all** (2026-07-11) — remote reputation lookup, report/correct submission, and seed DB delta updates were all removed; `network/ApiClient.kt`, `network/CircuitBreaker.kt`, and `di/NetworkModule.kt` are deleted. Screening is 100% on-device (blocklist/whitelist/prefix/seed DB/behavioral). The Supabase backend (`supabase/`) is untouched and still deployed, it's just never called by the client — see Backend Schema below for what's now orphaned. Do not reintroduce a network call here without an explicit product decision.

**Module layout** (source root is `android/app/src/main/kotlin/com/fenn/callshield/`, not `src/main/java`):
```
billing/       # BillingManager, PlanType, PromoGrantManager
data/
  ├── behavioral/    # Phase 2 behavioral signal buffer
  ├── local/         # Room DAOs (dao/), entities (entity/), seed DB access
  ├── preferences/   # DataStore (ScreeningPreferences etc.)
  └── repository/
domain/
  ├── model/     # e.g. AdvancedBlockingPolicy
  ├── repository/  # interfaces
  └── usecase/   # e.g. ScreenCallUseCase, EvaluateAdvancedBlockingUseCase
notification/
screening/       # CallScreeningService + ScreeningOrchestrator
ui/
  ├── components/, theme/
  └── screens/   # one dir per feature (home, onboarding, paywall, settings,
                 # blocklist, whitelist, calllog, dnd, trai, privacy, protect,
                 # reason, report, prefix, backup, advancedblocker,
                 # currentplan, activity, main, permissions)
di/              # Hilt modules
util/
```

---

## Call Decision Priority

`ScreeningOrchestrator` checks in this order — first definitive match wins:

| Priority | Check | Result |
|---|---|---|
| 1 | Personal whitelist | Always Allow (skips all further checks) |
| 2 | Personal blocklist | Reject |
| 3 | Prefix rules | Reject or Silence per rule |
| 4 | Private/hidden number (if enabled) | Reject |
| 5 | Seed DB | Known Spam → Silence (never populated in practice — see What Goes Where) |
| 6 | Default | Allow (Unknown) |

---

## Hashing Convention

All phone numbers are hashed **on-device** using HMAC-SHA256 with a **static salt bundled in the app binary** before any network call. The salt is not fetched from the server — this ensures hashing works offline on first launch.

- Normalize to E.164 first: `+919876543210`
- Then: `HMAC-SHA256(e164_number, bundled_salt)`
- The convention still applies to every hash the app computes (blocklist/whitelist/prefix/seed-DB entries), even though as of 2026-07-11 the app makes no network calls at all — nothing is sent to or stored on Supabase currently. Keep hashing on-device regardless, in case remote lookup is reintroduced.

Plain SHA-256 is not used — Indian mobile numbers (~1 billion) are small enough to fully enumerate.

---

## Device Token

- Generated once as a cryptographically random UUID, stored in **Android Keystore**.
- Survives app **updates** but **not full uninstalls** (Keystore entry is tied to the app UID; uninstall deletes it).
- `HMAC-SHA256(uuid, bundled_salt)` is used for local promo-code validation (`PromoGrantManager`) — never linked to any account. It's no longer sent to a backend for rate limiting/deduplication since those API calls were removed (2026-07-11); the raw UUID has never left the device.

---

## What Goes Where

| Concern | Location |
|---|---|
| Blocking decision logic | On-device only |
| Number hashing (HMAC-SHA256) | On-device, before any network call |
| Device token | Android Keystore |
| Reputation counters & confidence scores | Supabase (hash-keyed) — orphaned, client no longer reads/writes this |
| Local blocklist, whitelist & prefix rules | Room (SQLite) |
| Seed spam DB | Room table, but **never populated** — nothing bundles or downloads seed data (dead code, kept intentionally, see 2026-07-11 note above) |
| call_decision_audit log | Room (SQLite), on-device only |
| Behavioral events buffer (Phase 2) | Room (SQLite), 24h hard TTL |
| Encrypted backup blobs (Phase 3) | Supabase Storage |

---

## Backend Schema (Phase 1)

```sql
reputation          -- number_hash, report_count, unique_reporters,
                    -- confidence_score, category TEXT, negative_signals,
                    -- last_reported_at, last_computed_at

report_events       -- append-only source of truth; enables score recomputation
                    -- number_hash, device_token_hash, category, reported_at, schema_version

reporter_deduplication  -- (number_hash, device_token_hash) PRIMARY KEY
                        -- enforces true unique_reporters count; one contribution per device per number ever

quarantine_queue    -- Phase 2 only; created on Phase 2 feature flag activation
number_categories   -- Phase 2 only; added alongside category voting
```

Migrations in `supabase/migrations/` are applied in order: `0001_initial_schema` (reputation, report_events) → `0002_rls_policies` → `0003_auto_purge` (`purge_stale_reputation()`, 12-month TTL) → `0004_phase2_tables` (quarantine_queue, number_categories) → `0005_phase3_family` (despite the filename, this actually creates `reputation_flags` for spike/oscillation/low-trust detection — Family Plan was cut from the product, the migration file just kept its old name).

Edge Functions (`supabase/functions/`): `correct/`, `report/`, `reputation/`, `reputation-harden/`, `seed-db-manifest/`, `verify-subscription/`, sharing `_shared/` (`confidence.ts`, `cors.ts`, `errors.ts`, `rateLimit.ts`).
- `POST /report` — rate-limit → dedup → insert event → recompute score
- `GET /reputation` — hash lookup, rate-limited 60/device/hr
- `POST /correct` — "Not Spam" signal → increment negative_signals → recompute score
- `GET /seed-db/manifest` — current version + SHA-256 checksum

**As of 2026-07-11, `report/`, `reputation/`, and `seed-db-manifest/` are orphaned — the Android app no longer calls them.** They're still deployed and functionally correct, just unreached. `verify-subscription/` was already unused client-side before that (see Subscription Tiers). `reputation-harden/` operates on backend data independent of client calls. Backend code/infra was deliberately left as-is when the client-side calls were removed — decommissioning it is a separate decision.

Clients never access the DB directly (RLS denies it) — Edge Functions use the service_role key.

---

## Confidence Score Formula (Phase 1, hardcoded in Edge Function)

```
base_score        = min(unique_reporters / 10, 1.0)
recency_decay     = max(0, 1 - days_since_last_report / 90)
confidence_score  = base_score * recency_decay
```

Records purged after 12 months of zero reports. Formula is hardcoded — no `GET /config` endpoint in Phase 1. Backend-only now — the client never calls `GET /reputation`, so this score is never seen by the app (see Architecture).

---

## Phase 1 Scope — What's Deliberately Excluded

These are **not** Phase 1. Do not partially build them:

| Feature | Phase |
|---|---|
| Play Integrity API on POST /report | 2 |
| Ed25519 seed DB signing | 2 |
| Behavioral detection (call frequency, burst, short-ring) | 2 |
| Report velocity quarantine + category voting | 2 |
| `number_categories` table | 2 |
| TRAI Quick Report | 2 |
| SMS scam detection | 2 |
| Encrypted cloud sync | 3 |
| Family Protection (QR pairing) | 3 (later cut entirely — see Subscription Tiers) |
| On-device ML | 4 (exploratory only) |

**Also removed from Phase 1:** server-managed HMAC secret (use static salt), `GET /config` endpoint (hardcode formula), timing normalization on GET /reputation, 4-digit QR confirmation.

---

## Subscription Tiers

- **Free:** manual blocking, whitelist, prefix blocking, manual spam reporting (local-only, adds to blocklist — no backend involved as of 2026-07-11). Seed DB detection is currently dead code — nothing populates it.
- **Pro (₹399/year or ₹49/month, plus lifetime):** VIP-contacts-only mode, Night Guard/Work Focus with REJECT action, country filter, blocklist aging, advanced prefix rules, early DB delta updates.
- Billing product IDs: `callshield_pro_annual` / `callshield_pro_monthly` (SUBS), `callshield_pro_lifetime` (INAPP, no offerToken — use `launchInAppBillingFlow()` not `launchBillingFlow()`). `PlanType` enum: `NONE, PRO_MONTHLY, PRO_ANNUAL, PRO_LIFETIME, PROMO_PRO`.
- **Family Plan was removed entirely from the codebase** — do not reintroduce it without an explicit product decision. No `FAMILY` variant exists in `PlanType` or `PromoGrant`.
- **Confidence-score auto-block, Burst Protection, and Auto-Escalate were removed entirely** (2026-07-11) — the app never auto-rejects a call based on reputation score or call frequency alone; repeated calls can be a legitimate emergency, so nothing takes an automatic blocking action without an explicit user-created rule (blocklist/prefix/VIP-only/etc). Do not reintroduce without an explicit product decision. High-confidence spam (≥0.8) still Silences (ring-suppressed), it just never Rejects.
- `verify-subscription` Edge Function exists on the backend but was never called client-side even before the 2026-07-11 cleanup — `BillingManager` relies solely on the local Play Billing client, no server-side entitlement verification is wired up despite `docs/Developer Guidelines.md` describing one.

Paywall is triggered at the **value moment** (first spam call silenced for a free user) — not on install.

---

## CI Workflows (`.github/workflows/`)

- **ci-pr.yml** — PRs to main/dev; path-filtered. Android lint + `testDebugUnitTest` (Java 17); backend `deno check` + credential-logging scan + Edge Function structure validation; gated by a final `ci-gate` job.
- **backend-deploy.yml** — push to main touching `supabase/**`, or manual dispatch. Validates, then `supabase link` → `db push --include-all` → deploy functions → push secrets → smoke-test each endpoint.
- **android-deploy-playstore.yml** — push to main touching `android/**` or `distribution/whatsnew/**`, or manual dispatch. Validates release notes, builds signed `bundleRelease`, uploads to Play Store Internal Testing, cuts a GitHub prerelease.
- **deploy-pages.yml** — push to main touching `privacy-policy/**`; publishes it to GitHub Pages.

---

## Key Documents

| File | Purpose |
|---|---|
| `docs/Developer Guidelines.md` | Non-negotiable privacy rules, hashing, circuit breaker, abuse resistance, phase boundaries |
| `docs/Tech Stack.md` | Full schema DDL, module structure, seed DB pipeline, API diagram |
| `docs/PRD/Phase 1-5 PRD.md` | Functional requirements per phase |
| `docs/Wireframes.md` | All 14 Phase 1 screens with annotations |
| `docs/Roadmap.md` | Phase trigger conditions, risk management |
| `docs/Known Issues.md` | Standing code-quality audit — critical/major/minor bugs found in the built app, not yet all fixed |
