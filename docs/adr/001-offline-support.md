# ADR: Offline behavior for Pomp (Datastar + Service Worker)

**Status**: Proposed
**Date**: 2025-10-25
**Context**: Pomp is HTML-first, Datastar-driven (fragments or SSE) with a Clojure/Ring backend and Tailwind v4/daisyUI v5 for styling. We want resilient UX when the network drops, without introducing a client state store.

---

## Context

* **Datastar interaction model.** Backends return either `text/html` fragments (default) that Datastar **morphs** into the DOM (preserving focus/state), or SSE events like `datastar-patch-elements`. Both are the canonical “Datastar way.” ([Datastar][1])
* **Service Worker capabilities.** A service worker can **intercept** fetches and **respond with custom `Response`s** (e.g., HTML fragments), enabling offline fallbacks. Background Sync can **defer and replay** failed requests once connectivity returns; Workbox provides a queueing plugin and routing helpers. ([MDN Web Docs][2])
* **SW modules & shared code.** Service workers support ES modules with **static imports**; we can compile shared rendering helpers to small ESM (via Squint/CLJC → JS) and import them in the SW. ([MDN Web Docs][3])
* **SSE through a SW.** In principle a SW can proxy EventSource requests, but cross-browser support and lifecycle management are finicky; offline “simulated” SSE yields little benefit compared to returning a single fragment. ([MDN Web Docs][4])

---

## Decision

We will implement **offline behavior** with a **fragment-first** strategy and **queued writes**, aligned with Datastar’s model:

1. **Read path (GET actions):**

   * When online, requests go to the server as usual.
   * When offline, the Service Worker (SW) intercepts Datastar action requests and returns **`text/html` fragments** from cache or **locally synthesized** via shared rendering helpers (ESM). Datastar will morph the target region in the page. ([MDN Web Docs][2])

2. **Write path (POST actions):**

   * If a POST fails due to connectivity, the SW **enqueues** it using Background Sync (Workbox `BackgroundSyncPlugin`) and immediately returns an **offline acknowledgment fragment** (e.g., toast/banner or optimistic UI) to keep the UI responsive.
   * When connectivity resumes, the queue **replays** requests; server responses replace optimistic fragments with authoritative HTML. ([Chrome for Developers][5])

3. **Signals for local state:**

   * Prefer **Datastar signals** for client-local UI state (filters, expansion, selection) to avoid inventing a client store; they remain effective offline and are reconciled as real fragments arrive. ([Datastar][1])

4. **No offline SSE simulation:**

   * While technically possible, we **will not emulate SSE** while offline. Use a single **fragment** response from the SW instead; switch back to server SSE automatically when online. ([MDN Web Docs][4])

5. **Shared rendering code:**

   * Provide small, **pure** rendering helpers (CLJC/Squint → ESM) that map `(params, cached data) → HTML string` for specific regions (e.g., `<tbody id="users-rows">…</tbody>`). SW **statically imports** these helpers. ([MDN Web Docs][3])

---

## Consequences

### Benefits

* **Native to Datastar.** Fragments produced offline are indistinguishable from server fragments; Datastar morphing still preserves focus/state. ([Datastar][1])
* **Robust writes.** Background Sync gives reliable **store-and-forward** for POSTs without custom plumbing; Workbox simplifies routing and queueing. ([Chrome for Developers][5])
* **Small surface.** No client store/framework; minimal SW code; rendering purely functional and testable.

### Trade-offs / Risks

* **SSE not proxied offline.** We lose “progress streams” while offline; acceptable because offline users can’t receive server progress anyway.
* **Background Sync availability.** Not uniformly supported across all browsers; Workbox provides fallbacks but we must **detect and degrade** gracefully. ([MDN Web Docs][6])
* **Auth & privacy.** Must avoid caching sensitive fragments improperly; SW must forward cookies/headers correctly.

---

## Alternatives considered

* **Full SSE proxy in SW.** Complex, brittle across browsers; little user value offline. Rejected. ([Stack Overflow][7])
* **Client state store & rehydration.** Heavier than needed; conflicts with our server-driven model.
* **“App shell” only.** Too limited for CRUD-heavy admin UIs; we want real offline fragments.

---

## Implementation sketch

1. **Service Worker (module):**

   * **Routing** (Workbox):

     * `GET /actions/**` → try network; `catch` returns **synthesized fragment** (`Content-Type: text/html`).
     * `POST /actions/**` → `NetworkOnly` with **`BackgroundSyncPlugin`** queue. ([Chrome for Developers][8])
   * **Shared helpers:** static `import` of ESM rendering functions (compiled from Squint/CLJC). ([MDN Web Docs][3])

2. **Caching policy:**

   * Precache shell assets; runtime cache recent **HTML fragments** by URL+params **or** cache normalized data in IndexedDB for synthesis (project-dependent).

3. **UI affordances:**

   * Datastar-patched **offline banner**; toasts for queued writes; reconcile on replay success/failure.

4. **Server endpoints:**

   * Keep POSTs **idempotent** where possible (replay safety).
   * Continue to return **fragments**/SSE as today (no server change needed). ([Cljdoc][9])

---

## Rollout & testing

* **Feature flag** offline SW.
* **Simulation** via DevTools offline + throttling; verify fragment morphing and queue replay.
* **Contract tests** for synthesized fragments (IDs/selectors stable).
* **Browser matrix**: confirm Background Sync and fallbacks; if unavailable, disable queuing UX. ([MDN Web Docs][10])

---

## References

* Datastar “Getting started” (morphing element patches); SSE events (`datastar-patch-elements`). ([Datastar][1])
* Datastar Clojure/Ring SDK (SSE generators, ADR alignment). ([Cljdoc][11])
* MDN: Service Worker fetch/`respondWith`; Background Sync (`SyncManager`); Periodic Background Sync (optional); SSE usage. ([MDN Web Docs][2])
* Workbox: `workbox-background-sync`, routing, strategies, plugins. ([Chrome for Developers][5])
* ES modules in SW (static imports). ([MDN Web Docs][3])

---

## Decision notes

This ADR keeps Pomp’s **server-driven** philosophy intact: **no local store**, **no SPA framework**, and offline behavior that is **Datastar-native** (HTML fragments that morph). When connectivity returns, the server reasserts truth via the same mechanisms.

[1]: https://data-star.dev/guide/getting_started?utm_source=chatgpt.com "Getting Started Guide"
[2]: https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope/fetch_event?utm_source=chatgpt.com "ServiceWorkerGlobalScope: fetch event - Web APIs | MDN"
[3]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Modules?utm_source=chatgpt.com "JavaScript modules - MDN Web Docs - Mozilla"
[4]: https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events?utm_source=chatgpt.com "Using server-sent events - Web APIs | MDN"
[5]: https://developer.chrome.com/docs/workbox/modules/workbox-background-sync?utm_source=chatgpt.com "workbox-background-sync | Modules - Chrome for Developers"
[6]: https://developer.mozilla.org/en-US/docs/Web/API/SyncManager?utm_source=chatgpt.com "SyncManager - Web APIs | MDN"
[7]: https://stackoverflow.com/questions/61041389/managing-server-side-events-with-a-service-worker?utm_source=chatgpt.com "Managing Server Side Events with a Service Worker"
[8]: https://developer.chrome.com/docs/workbox/modules/workbox-routing?utm_source=chatgpt.com "workbox-routing | Modules - Chrome for Developers"
[9]: https://cljdoc.org/d/dev.data-star.clojure/ring/1.0.0-RC3/doc/sdk-docs/sdk-libraries/core-sdk?utm_source=chatgpt.com "Core SDK — dev.data-star.clojure/ring 1.0.0-RC3"
[10]: https://developer.mozilla.org/en-US/docs/Web/API/Background_Synchronization_API?utm_source=chatgpt.com "Background Synchronization API - MDN Web Docs"
[11]: https://cljdoc.org/d/dev.data-star.clojure/ring/1.0.0-RC3/doc/sdk-docs/using-datastar?utm_source=chatgpt.com "Using Datastar — dev.data-star.clojure/ring 1.0.0-RC3"
