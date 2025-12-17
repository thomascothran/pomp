# ADR: Component mapping strategy (daisyUI → Clojure Hiccup helpers) with **real `defn` per component**

**Status:** Proposed
**Date:** 2025-10-25
**Owners:** Pomp maintainers

---

## Context

Pomp is an HTML-first UI kit. The server returns **`text/html` fragments** or **SSE** events (e.g., `datastar-patch-elements`), and **Datastar** morphs only the targeted DOM regions, preserving focus/state. We treat **daisyUI v5** (on **Tailwind v4**) as the canonical CSS API. Tailwind v4 exposes design tokens as **CSS variables** defined via `@theme`, and daisyUI v5 is explicitly Tailwind-v4 compatible and configured directly **in CSS** (including a `prefix`). Our helpers must emit predictable Hiccup that aligns with those contracts and with Datastar’s patching model. ([Datastar][1])

We also want ergonomics familiar to Clojure UI developers (props map first, then children), similar in spirit to Fulcro’s DOM factories. And we want **first-class `defn` vars** (docstrings, arglists, jump-to-def) for every component. ([Fulcro][2])

---

## Problem

How do we map the evolving **daisyUI** class surface (base classes, variants, sizes, sub-parts) into a **uniform, testable Clojure API** that:

* Mirrors daisyUI semantics 1:1 (including **CSS prefix**),
* Produces **stable, patch-friendly** markup for Datastar,
* Gives **excellent DX** (real `defn` for each component; docstrings; IDE navigation),
* Minimizes drift/maintenance as daisyUI evolves?

---

## Decision

Adopt a **schema-driven registry** as the single source of truth and a single internal renderer **`make-component`**. **Expose each component as a real `defn`** (not `intern`) that delegates to `make-component` with that component’s registry entry.

**Public API shape (uniform):**

* Two arities everywhere:
  `([& children])` and `([props & children])`
  (props map first, then children — atoms allow content-only calls; composites require children).

**Example (public):**

```clojure
(defn button
  "daisyUI button wrapper"
  [& args]
  (make-component (get component-registry :button) args))
```

This guarantees docstrings, arglists, and “jump to definition” work as expected (because these are **actual `defn` vars**). ([ClojureDocs][3])

---

## Details

### 1) Registry (source of truth)

Each entry encodes structure and class semantics (unprefixed; prefix applied centrally):

```edn
{:component      :button
 :tag            :button
 :base           ["btn"]
 :variant->class {:primary "btn-primary" :ghost "btn-ghost" :link "btn-link"}
 :size->class    {:xs "btn-xs" :sm "btn-sm" :lg "btn-lg"}
 :flags->class   {:outline? "btn-outline" :loading? "loading"}
 :parts          {}                       ;; for composites: sub-parts like :start/:end
 :attach-ds      :self                    ;; where to place data-on:* (Datastar actions)
 :doc            "daisyUI button wrapper"}
```

* Registry aligns with **daisyUI v5** class contracts and respects the **CSS-side prefix** set in `@plugin "daisyui" { prefix: "…" }`. ([daisyUI][4])

### 2) `make-component` (single implementation)

Parses two arities into `[props children]`, composes class list:

* Base → variant/size/flags → user `:class` (prefix applied once),
* Attributes compiled uniformly:

  * `:data {k v}` → `data-k="v"`,
  * `:aria {k v}` → `aria-k="v"`,
  * `:ds {:click "..."} → {"data-on:click" "..."}` for **Datastar actions**,
  * `:attrs` merged last as escape hatch,
* Tag from registry `:tag` (overridable by `:as`),
* For composites, attach `data-on:*` where `:attach-ds` specifies.

Returns standard **Hiccup**: `[tag attrs & children]`.

### 3) Per-component **`defn`** helpers (public)

For every entry: emit a **handwritten or generated** `defn` in source:

```clojure
(defn navbar [& args] (make-component (get component-registry :navbar) args))
(defn navbar-start [& args] (make-component (get component-registry :navbar-start) args))
;; ...
```

> We deliberately choose **real `defn` forms in source** (checked into VCS) so docstrings, arglists, stack traces, and IDE navigation are first-class. (We may optionally generate these `defn`s from the registry, but the committed artifact is still `defn`, not `intern`.) ([ClojureDocs][3])

### 4) Props model (uniform across all helpers)

* `:id`, `:class` (string/vec/set; merged), `:data`, `:aria`, `:attrs`,
* `:variant`, `:size`, boolean flags (e.g., `:disabled?`, `:outline?`, `:loading?`),
* `:ds` → Datastar `data-on:*`,
* `:as` (optional tag override), `:test-id` → `data-testid`.

### 5) Atoms vs. composites

* **Atoms** (e.g., `button`, `badge`, `label`): `(button "Save")` and `(button {:variant :primary} "Save")`. True `<input>` atoms don’t take children; values in `:attrs`.
* **Composites** (e.g., `navbar`, `menu`, `card`, `tabs`): composition via **sub-part helpers** as children (mirrors daisy docs and yields patch-friendly DOM).

### 6) Tailwind v4 & theming

Helpers emit daisy **semantic classes** only (e.g., `btn-primary`). **Design tokens** live in Tailwind v4 `@theme`, which become runtime **CSS variables** and utilities, and daisy themes are selected via `[data-theme]`. ([Tailwind CSS][5])

### 7) Datastar compatibility

All markup is plain HTML/Hiccup. Datastar actions on elements trigger server endpoints that return **fragments** or **SSE** (`datastar-patch-elements`); Datastar **morphs** the targeted regions by ID/selector. ([Datastar][1])

---

## Rationale

* **Predictability & maintainability.** Centralizing class semantics in a registry avoids per-component drift; changing daisy classnames or adding variants is a data update.
* **DX.** Real **`defn`** vars (not `intern`) give great doc/navigation ergonomics, while keeping one renderer path. ([ClojureDocs][3])
* **Compatibility.** The output aligns with **daisyUI v5** (Tailwind v4) and **Datastar**’s morphing model (small, stable patch targets). ([daisyUI][6])
* **Familiar call shape.** The two-arity, props-then-children API mirrors the *feel* of Fulcro’s DOM factories without pulling in Fulcro at runtime. ([Fulcro][2])

---

## Alternatives considered

1. **Handwritten helpers only.**

   * Control per component; − repetitive, prone to drift.

2. **Load-time `intern` of functions.**

   * Centralized; − worse IDE nav/def-jump stories vs. committed `defn`s (and stack traces reference anonymous fns). Rejected in favor of real `defn`. ([ClojureDocs][3])

3. **Macro DSL defining components.**

   * Concise; − data/tooling frictions vs. EDN/CLJC registry; harder to diff.

4. **Pure build-time codegen with no registry.**

   * Fast startup; − harder to evolve. We may **generate `defn` files from the registry** in a tools.build task, but the registry remains the source of truth.

---

## Consequences

**Benefits**

* Single renderer; consistent merging rules; small, precise fragments for Datastar. ([Datastar][1])
* Clear upgrade path when daisyUI changes (edit registry, regenerate `defn` wrappers).
* First-class docs/arglists/navigation thanks to real `defn`.

**Risks / Costs**

* Requires keeping the registry accurate (mitigate with snapshot tests).
* A few composites may need per-component overrides (where `data-on:*` attaches internally).

---

## Implementation sketch

```clojure
(ns pomp.html
  (:require [clojure.string :as str]))

(def ^:dynamic *daisy-prefix* "p-")

(defn- pfx [cls] (when cls (str *daisy-prefix* cls)))

(def component-registry
  {:button {:component :button
            :tag :button
            :base ["btn"]
            :variant->class {:primary "btn-primary" :ghost "btn-ghost"}
            :size->class    {:xs "btn-xs" :sm "btn-sm" :lg "btn-lg"}
            :flags->class   {:outline? "btn-outline" :loading? "loading"}
            :attach-ds      :self
            :doc "daisyUI button"}})

(defn make-component [entry args]
  (let [[props children] (if (and (seq args) (map? (first args)))
                           [(first args) (rest args)]
                           [{} args])
        ;; …compose classes (apply pfx), compile attrs (:data/:aria/:ds/:attrs),
        ;; choose tag (props :as or entry :tag), attach data-on:* per :attach-ds…
        ]
    [tag attrs & children]))

;; Public, real defn:
(defn button
  "daisyUI button wrapper"
  [& args]
  (make-component (get component-registry :button) args))
```

---

## Testing & docs

* **Snapshot tests**: helper → expected class list for `(variant, size, flags)` (with prefix).
* **Contract tests**: `:data/:aria/:ds` expansion, `:attrs` last merge, patch-target IDs.
* **Docs**: generate API tables and examples from the registry (keeps README in sync).

---

## References

* **Datastar – Getting started** (morphing element patches); **SSE events** (`datastar-patch-elements`). ([Datastar][1])
* **Tailwind v4** – `@theme` (theme variables as CSS custom properties); v4 blog on CSS vars by default. ([Tailwind CSS][5])
* **daisyUI v5** – Tailwind v4 compatibility; **CSS-side config** with `prefix`. ([daisyUI][6])
* **Fulcro** – developers guide (pattern precedent: props, then children). ([Fulcro][2])
* **Clojure `defn`** – real vars with doc/arglists (why we prefer `defn` over `intern`). ([ClojureDocs][3])

---

**Outcome:** Real `defn` helpers per component, powered by a registry and a single renderer, yield predictable markup aligned with daisyUI v5/Tailwind v4, excellent IDE/docs ergonomics, and Datastar-native patchability.

[1]: https://data-star.dev/guide/getting_started?utm_source=chatgpt.com "Getting Started Guide"
[2]: https://book.fulcrologic.com/?utm_source=chatgpt.com "Fulcro Developers Guide"
[3]: https://clojuredocs.org/clojure.core/defn?utm_source=chatgpt.com "defn - clojure.core"
[4]: https://daisyui.com/docs/config/?lang=en&utm_source=chatgpt.com "Config — daisyUI Tailwind CSS Component UI Library"
[5]: https://tailwindcss.com/docs/theme?utm_source=chatgpt.com "Theme variables - Core concepts"
[6]: https://daisyui.com/docs/v5/?lang=en&utm_source=chatgpt.com "daisyUI 5 release notes"
