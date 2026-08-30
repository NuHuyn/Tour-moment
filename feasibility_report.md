# Feasibility Report: DeepSeek-Powered Chatbot for Tour-moment

**Scope:** Investigation only — no code changes made. This report is for review before any implementation starts.
**Date:** 2026-08-30

---

## 1. Current State Findings

### 1.1 Chatbot screen (Android)

- `Mycurrenttour/app/src/main/java/com/example/mycurrenttour/ChatbotActivity.java` — a full-screen "Trip Assistant" chat, launched only from a floating icon on the Discovery tab. Standard `Activity`, no bottom nav.
- **It is 100% mocked.** `sendChatMessage()` shows a typing indicator, waits 1200ms, then always replies with the *first* item from `MockDataProvider.getMockTours()`. The class's own doc comment says: *"Demo reply only (no AI/RAG backend yet — chưa có DeepSeek+MongoDB)"*.
- UI plumbing that already exists and can be reused: chat bubble rendering (`item_chat_bot_message.xml`), typing indicator, quick-reply chips, and a "tour suggestion card" that reuses the existing `TourAdapter`/`item_tour` layout — so a real backend reply just needs to slot into `addBotBubble()` / `addTourSuggestionCard()`.
- No network call is currently made for chat at all — no `ApiService` endpoint for chat exists yet.

### 1.2 Backend framework & hosting

- **Stack:** Node.js + Express 5 + Mongoose 9, plain REST controllers (`tour-backend/src/{app,server}.js`, `controllers/`, `routes/`, `models/`). No TypeScript, no GraphQL, no existing LLM/AI SDK dependency.
- **Hosting:** Google Cloud Run, region `asia-southeast1`, service `tour-moment-backend` (confirmed from the Android `ApiClient.BASE_URL`: `https://tour-moment-backend-42345853831.asia-southeast1.run.app/`). Deploy commit `373f9a4` ("Cloud Run deploy setup") shows no IaC file (no `cloudbuild.yaml`/`service.yaml` in the repo) — deployment is presumably done ad hoc via `gcloud run deploy` or the console, with env vars set there. A `docker-compose.yml` + local `nginx` reverse proxy also exist, apparently for local/VM dev (an earlier decommissioned GCE VM at `35.224.85.222` is referenced in a code comment).
- **MongoDB Atlas connectivity:** `src/config/db.js` connects via `mongoose.connect(process.env.MONGODB_URI)` — a standard SRV connection string, no read replicas, no separate read-only DB user, no connection pooling tuning visible.

### 1.3 ⚠️ Pre-existing security issues (found during audit, independent of the chatbot work, but directly relevant since we're about to add another consumer of this DB)

1. **MongoDB Atlas credentials are committed to git in plaintext.** `tour-backend/docker-compose.yml` (tracked in git, not gitignored) hardcodes `MONGODB_URI=mongodb+srv://23521111_db_user:<password>@cluster0.o1rmyai.mongodb.net/newtour?...`. `.env` itself is correctly gitignored, but this compose file leaks the same live credential. **This should be rotated and removed from git history before doing anything else** — an LLM-facing feature increases the blast radius of a leaked DB credential, so this needs to be fixed first regardless of the chatbot project.
2. **No authentication middleware exists anywhere in the API.** `authController.js` has its own TODO: Google login trusts a client-supplied `googleId`/`email` with no server-side ID-token verification — *"right now anyone can POST an arbitrary googleId/email and have this upsert a User row as that identity."* Every route in `tourRoutes.js` is `@access Public`; `getMyTours` takes `userId` as a raw path param with no check that the caller *is* that user.
3. **No rate limiting, no API gateway, no request logging middleware** beyond a generic Express error handler.

**Implication for the chatbot design:** there is currently no reliable notion of "the authenticated current user" on the backend at all. A chatbot endpoint cannot be scoped "per user" in any way that resists spoofing until basic request auth (e.g. verified Google ID tokens + a session/JWT) is added. This isn't a blocker for a *read-only, non-personalized* recommendation chatbot, but it is a blocker for any chatbot feature that should see a specific user's own bookings/tours ("what have I booked?") or write on their behalf.

### 1.4 Relevant MongoDB collections/schemas

Only two collections exist. There is **no separate Booking collection and no separate Pricing collection** — pricing and "availability" are just fields embedded per waypoint inside a `Tour` document.

**`Tour`** (`tour-backend/src/models/Tour.js`)
| Field | Notes | Chatbot-safe? |
|---|---|---|
| `authorId` (String, ref User) | Actually stores the author's `googleId` | ⚠️ Internal ID — do not return raw to the model/user; map to a display name only |
| `title`, `description` | Free text | ✅ Safe |
| `startDate`, `endDate`, `status` (Upcoming/Ongoing/Completed) | Computed status is time-derived in `getMyTours` | ✅ Safe |
| `imageUrl`, `videoUrl` | Public asset URLs | ✅ Safe |
| `isShared` | Whether tour is public | ✅ Safe (use to filter — see below) |
| `originalTourId` | Copy lineage | ✅ Safe (low value to expose) |
| `waypoints[]` → `locationName`, `price` (Number), `coordinate` (GeoJSON Point), `arrivalDate`, `note`, `photos[]` | `price` is actually a **premium-waypoint paywall flag convention** (0 = free/unlocked, >0 = locked), per `scripts/seedTours.js` comments — **not necessarily a real booking price**. This must be clarified with product before a chatbot quotes it as "price" to a user. | ✅ Safe as data, ⚠️ semantics need confirming |

There is no `Booking` collection, so "check availability" (as sketched in the task's Option A) isn't backed by real data yet — `Tour.status` (Upcoming/Ongoing/Completed) and dates are the closest proxy for "availability" today.

**`User`** (`tour-backend/src/models/User.js`)
| Field | Chatbot-safe? |
|---|---|
| `_id` (= googleId), `googleId` | ❌ PII / internal identifier — never expose |
| `email` | ❌ PII — never expose |
| `displayName`, `photoUrl` | ✅ Safe — already returned publicly today as tour "author" info in `getPublicTours` |
| `role` | ❌ Not useful to a chatbot; low sensitivity but no reason to expose |
| `createdAt` | ⚠️ Low sensitivity, no reason to expose |

**Net effect:** the *only* collection a recommendation chatbot needs is `Tour`, filtered to `isShared: true` (the same set already exposed publicly via `GET /api/tours`), projecting only `title, description, startDate, endDate, status, imageUrl, waypoints.{locationName,price,coordinate,note,photos}`, plus `author.displayName/photoUrl` joined the same way `getPublicTours` already does it. That is a very small, already-public-equivalent surface — good news for the security story.

### 1.5 Auth model recap (for scoping chatbot requests)

There are no server sessions, no JWTs, no API keys today — only a `POST /api/auth/google-login` that upserts a `User` from client-supplied fields, unverified. A chatbot endpoint today can only be "scoped" by whatever the Android app sends (e.g. `userId`), and that is self-reported and spoofable. Since the recommended architecture (below) only needs to read already-public tour data, this gap doesn't block the recommended MVP — but it does mean **no personalized/"my bookings" chat features** and **no chatbot-triggered writes** should ship until real auth exists.

---

## 2. DeepSeek API — Confirmed Capabilities (verified against api-docs.deepseek.com, 2026-08-30)

| Item | Confirmed value |
|---|---|
| **Current models** | `deepseek-v4-flash`, `deepseek-v4-pro`, `deepseek-v4-flash-vision-exp` — all with 1M-token context, 384K max output |
| **Legacy aliases** | `deepseek-chat` / `deepseek-reasoner` are **already retired** (cutoff was 15:59 UTC, **July 24, 2026** — before today's date). They previously routed to `deepseek-v4-flash` non-thinking/thinking. Any integration must use `deepseek-v4-flash` or `deepseek-v4-pro` explicitly; the old names now error. |
| **API shape** | OpenAI-compatible `POST https://api.deepseek.com/chat/completions` (beta variant at `/beta`); an Anthropic-compatible mode also exists as a separate guide |
| **Tool/function calling** | Supported on all three models (v3.2+ added tool use inside "thinking" mode too). **The model never executes functions itself** — it returns `tool_calls`; the app must execute them and send results back in a follow-up message. Confirmed by DeepSeek's own docs: *"the functionality of the function needs to be provided by the user. The model itself does not execute specific functions."* |
| **`tool_choice`** | Standard OpenAI-style: `"none"`, `"auto"`, `"required"`, or a specific `{"type":"function","function":{"name": "..."}}` |
| **Max tools/request** | No documented hard cap found; keep the tool count small (≤5–8) anyway for reliability and prompt-token cost |
| **Strict mode** | Beta "strict" JSON-schema mode available to force the model's tool-call arguments to conform exactly to the declared schema — worth enabling for this use case |
| **Streaming** | Supported (`stream: true`), including streaming of `tool_calls` deltas |
| **Pricing (per 1M tokens, confirmed via docs page)** | See table below — **peak/off-peak billing went live 2026-08-16**, so this is very recent and easy to get wrong if referencing older cached knowledge |

**Pricing table (per 1M tokens, as published):**

| Model | Input, cache hit | Input, cache miss | Output |
|---|---|---|---|
| deepseek-v4-flash | $0.007 (off-peak) / $0.014 (peak) | $0.22 (off-peak) / $0.44 (peak) | $0.66 (off-peak) / $1.32 (peak) |
| deepseek-v4-pro | $0.022 / $0.044 | $0.66 / $1.32 | $1.98 / $3.96 |
| deepseek-v4-flash-vision-exp | same as flash | same as flash | same as flash |

- Off-peak = every hour **except** 01:00–04:00 and 06:00–10:00 UTC, Mon–Fri (≈79% of the week, including all weekend hours) — off-peak is exactly half the peak rate.
- Concurrency ceilings: 2,500 concurrent requests for Flash models, 500 for Pro.
- **Recommendation:** use `deepseek-v4-flash` for this feature — it's ~3x cheaper than Pro on input/output and tool-calling/JSON support is identical; Pro's extra reasoning strength isn't needed for "recommend a tour from a filtered candidate list."

---

## 3. Architecture Options

### Option A — Function-calling over fixed, parameterized queries (recommended)

DeepSeek is given a small, whitelisted tool schema (e.g. `search_tours(destination?, maxPrice?, dateRange?, tags?)`, `get_tour_details(tourId)`). The backend executes **only** these pre-written, parameterized Mongoose queries — never a model-generated query string — and returns JSON results back to the model, which then writes the natural-language reply (and the app renders the tour card the same way the current mock flow does).

- **Security:** Best of the three. The model can never touch the DB directly; the attack surface is exactly the fixed set of functions, each with validated/typed arguments (e.g. clamp `maxPrice`, whitelist `sort` fields, cap `limit`).
- **Latency:** One extra request round-trip per tool call (DeepSeek → tool result → DeepSeek again), typically 1–3 calls per turn. With Flash and a small `isShared:true` `Tour` collection, each Mongo query is sub-50ms; overall added latency is dominated by the second LLM call (~1–3s).
- **Cost:** Low — only a handful of small JSON tool results go into context per turn, not the whole catalog. Fits comfortably in Flash's pricing.
- **Complexity:** Low–medium. This is the standard, well-documented DeepSeek/OpenAI tool-calling pattern; the current tour catalog is small enough that no vector infra is needed.
- **Fit for this app today:** Very good — the existing `Tour` collection is small (a handful of seeded tours today), the needed read patterns (filter by destination/price/date, fetch by id) map cleanly to 2–4 tool functions, and it reuses `getPublicTours`'s existing `isShared:true` filtering logic almost as-is.

### Option B — RAG-style (embed + retrieve top-k, then generate)

Pre-compute embeddings for each shared tour (title + description + waypoint notes), store vectors (MongoDB Atlas Vector Search, since it's already on Atlas — no new infra provider needed), retrieve top-k similar tours per user query, and stuff them into the prompt context instead of calling functions.

- **Security:** Also good — retrieval still runs a fixed, backend-controlled query (a vector search call, not user-influenced raw Mongo), and results still pass through field allow-listing before hitting the prompt. Slightly larger context blob is sent per turn.
- **Latency:** One LLM call in the simple case (no back-and-forth tool loop) — potentially *faster* than Option A per turn, but an embedding call is needed to embed the user's message before the vector search, plus an index needs to be kept fresh whenever tours are created/edited/shared.
- **Cost:** Extra fixed cost (embedding generation + storage/index maintenance) on top of the chat completion; token cost per turn is roughly similar to A once you include the top-k context, sometimes more since you can't precisely target "only 2 fields for 3 tours" the way a tool call does.
- **Complexity:** Higher — needs an embedding pipeline, an index-refresh strategy (webhook or cron on Tour create/update/share), and a vector search index in Atlas. Overkill while the catalog is a handful of seed tours; starts paying off once the catalog is in the hundreds+ and free-text semantic matching (not just structured filters) becomes valuable.
- **Fit for this app today:** Weak — the catalog is currently tiny and highly structured (destination, price, dates, geo-coordinates), which is exactly what function-calling with parameterized filters handles well without needing semantic search at all.

### Option C — Hybrid (function-calling primary, RAG-lite fallback later)

Ship Option A now; if/when the tour catalog grows large enough that keyword/filter search stops finding good matches for vague queries ("somewhere relaxing with beaches, not too far from Hanoi"), add a `semantic_search_tours` tool backed by Atlas Vector Search as one more function in the same whitelisted tool set — everything else (auth boundary, allowlisting, logging) stays identical. This isn't a separate architecture so much as A designed to extend into a bit of B later without a rewrite.

### Recommendation: **Option A now, with C as the natural growth path.**

Given the current catalog size, the lack of a real auth layer, and the goal of shipping something safe quickly, Option A is the only one that's low-complexity *and* matches the "read-only, whitelisted" security requirement precisely. RAG adds real infrastructure (embeddings, index freshness) for a benefit (semantic matching over a large catalog) this app doesn't need yet.

---

## 4. Security & Risk Assessment

### 4.1 Why the chatbot must never get write access or a general query interface

- DeepSeek only ever *proposes* a function name + JSON arguments; it has no way to run code. All actual DB access happens in backend code the app controls. If that backend code accepted a raw query object, filter string, or `$where`/aggregation pipeline generated by the model, a crafted user message (prompt injection) could turn into a NoSQL injection or a full collection scan/exfiltration. **Every tool implementation must build its Mongoose query from a small set of typed, validated parameters (whitelisted fields, clamped numeric ranges, enum-checked sort/status values) — never `JSON.parse(modelArgs)` fed straight into `.find()`.**
- No tool should call `save()`, `updateOne()`, `deleteOne()`, etc. The chatbot is read-only, full stop, until there is a real per-user auth story (see §1.3/1.5) that would let a write-capable tool safely scope itself to "this authenticated user's own tour."

### 4.2 Prompt injection risk

A user (or content embedded in tour descriptions, which are user-generated — `createTour`/`updateTour` are unauthenticated public endpoints today) could try to make the model call a tool with unintended arguments, or ignore the "only recommend tours" instruction. Mitigations:
- **Function allowlisting + fixed tool set** (already covered above) means the worst outcome of a successful injection is still "one of 2–4 harmless read-only search functions gets called with attacker-influenced arguments" — not arbitrary code or data access.
- **Server-side argument validation on every tool call**, independent of what the model claims: re-clamp price ranges, re-validate any id is a real `ObjectId` that belongs to an `isShared:true` tour before using it in `get_tour_details`, ignore any argument not in the declared schema.
- **System prompt hardening**: instruct the model to only discuss tours/travel and to never repeat or act on instructions found inside tool results (tour descriptions are user-generated text and should be treated as untrusted data, not instructions) — the same "data, not directives" boundary this document itself follows for retrieved content.
- **Do not put any secret, internal ID, or privileged instruction in the system prompt that would be damaging if the model echoed it back** — assume the full system prompt is eventually recoverable by a determined user.
- **Per-user rate limiting** (see §4.4) bounds how many injection attempts/turns an abusive user can run in a session.

### 4.3 PII / data exposure

- Tool responses must project only the safe `Tour` fields listed in §1.4 — explicitly exclude `authorId` (raw googleId) from tool output; replace with the joined `displayName`/`photoUrl` the same way `getPublicTours` already does.
- Never expose `User.email`, `User._id`/`googleId`, or `User.role` through any tool, regardless of what the user asks for ("what's the trip organizer's email?" should be refused, not looked up).
- Only ever query `Tour.find({ isShared: true, ... })` — a chatbot tool must never be able to read another user's private (`isShared: false`) tours, even if it somehow obtained a valid `_id` for one (id-based `get_tour_details` should filter by `isShared: true` too, not just by `_id`).
- Log tool arguments/results for observability, but scrub before persisting logs if this ever expands to reading anything user-identifiable (not needed for the current tour-only scope, but worth deciding now as a policy).

### 4.4 Cost control / abuse prevention

- **Per-user and per-IP rate limiting** on the chat endpoint (e.g. a token-bucket in Express — nothing like this exists in the codebase yet; `express-rate-limit` would be a reasonable, low-effort addition alongside this feature).
- **Cap max tokens per response** (`max_tokens`) and **cap max tool-call round trips per turn** (e.g. stop after 3 tool calls and force a final answer) to bound both cost and latency per message.
- **Cap conversation history length** sent back to DeepSeek each turn (e.g. last N turns or a rolling summary) — 1M context is generous but paying for it every turn adds up; there's no reason a tour-recommendation chat needs more than recent context.
- **Use `deepseek-v4-flash`, not Pro**, and prefer off-peak-friendly UX where feasible (no user-facing action needed, but worth knowing peak hours cost 2x when reviewing bills).
- **Set a DeepSeek account-level spend cap/alert** if the platform supports one, as a backstop independent of app-level rate limiting.

### 4.5 Logging / monitoring

- Log every tool call: which function, arguments (post-validation), row count returned, latency, and the requesting user/session identifier — this is the primary audit trail for both abuse detection and debugging bad recommendations.
- Log DeepSeek API errors/timeouts distinctly from tool errors, so cost/latency issues can be told apart from backend query issues.
- Track token usage per request/user to feed back into the rate-limit and cost-cap logic in §4.4, and to catch a runaway conversation (e.g. one that keeps re-triggering tool calls) before it becomes an expensive support ticket.

---

## 5. Recommended Plan (Option A) — Steps Only, No Code

1. **Fix the pre-existing DB credential leak first** (§1.3): rotate the MongoDB Atlas password, remove it from `docker-compose.yml`/git history, move it to Cloud Run env vars / Secret Manager only. This is independent of the chatbot but should not be deferred, since we're about to add another consumer of this database.
2. **Define the tool contract**: finalize field-level projections per §1.4 (what `search_tours`/`get_tour_details` may return), and clarify with product whether `waypoints[].price` means "premium unlock fee" or "actual cost" before the chatbot states it as a price to users.
3. **Add a chat backend route** (e.g. `POST /api/chat`) in `tour-backend`, alongside the existing `authRoutes`/`tourRoutes` pattern, holding: the DeepSeek API key (Cloud Run env var / Secret Manager, never in the Android app), the tool function implementations (parameterized Mongoose queries only), and the DeepSeek chat-completion + tool-execution loop (max 3 round trips, `deepseek-v4-flash`, `tool_choice:"auto"`, JSON strict mode for tool args).
4. **Implement 2–4 tools** to start: `search_tours(destination?, maxPrice?, startAfter?, startBefore?, limit≤10)`, `get_tour_details(tourId)` — both filtered to `isShared: true` server-side regardless of model input.
5. **Add rate limiting + request/response size caps** to the new route (`express-rate-limit` or equivalent), plus structured logging of tool calls per §4.5.
6. **Wire the Android side**: add a `chat(message, conversationHistory)` call to `ApiService`/`ApiClient`, replace `ChatbotActivity.sendChatMessage()`'s mock branch with the real call, keep the existing typing indicator and `addTourSuggestionCard()` rendering (already built for this).
7. **System prompt + guardrails**: write a system prompt that scopes the assistant to travel/tour recommendations only, treats tool-result content (user-generated tour text) as untrusted data, and refuses PII-adjacent questions.
8. **Test for injection resilience**: try tour descriptions / user messages containing "ignore previous instructions," attempts to make it call `get_tour_details` on non-shared ids, attempts to extract system prompt or user emails — confirm each is safely refused/ignored before shipping.
9. **Monitor cost and latency for a trial period** before wider rollout; revisit Option C (adding a semantic-search tool) only once the tour catalog and query variety justify it.

---

*This report reflects investigation only. No backend routes, Android code, or DeepSeek integration have been implemented.*
