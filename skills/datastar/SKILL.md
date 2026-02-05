---
name: datastar
description: Datastar framework guidance for adding data-* attributes, frontend actions, reactive signals, and backend SSE/SDK usage (including Clojure). Use when modifying HTML/Hiccup with Datastar attributes, handling Datastar requests, patching elements/signals, or tracing signal updates.
---

# Datastar

## Overview

Provide Datastar-specific guidance for UI wiring, server updates, and debugging.

Signals are Datastar's reactive state values. They are created with `data-signals`, `data-bind`, or `data-computed` and referenced in expressions with the `$` prefix (for example `$name`). Expressions inside `data-*` attributes are JavaScript expressions.

## Workflow Selector

- For HTML/Hiccup attribute wiring and actions, read `references/attributes.md`.
- For Clojure SDK SSE handlers, backend signal updates, and request parsing, read `references/clj-sdk.md`.
- For debugging signal flow or inspecting patches, read `references/signal-tracing.md`.

## Subworkflow: Frontend attributes

1. Identify the signals and UI elements to bind.
2. Apply the smallest set of `data-*` attributes needed for binding, display, or events.
3. Use Datastar actions (`@get`, `@post`, etc.) for server calls and add indicators for loading.
4. Ensure morph targets have stable ids/selectors for patching.

## Subworkflow: Backend SSE + Clojure SDK

1. Use `->sse-response` with `on-open` to establish SSE.
2. Read incoming signals using parsed request data (see `references/clj-sdk.md`).
3. Set backend signals with `d*/patch-signals!` and update HTML with `d*/patch-elements!`.
4. Close the SSE for one-shot responses, or store the generator for streaming.

## Subworkflow: Signal tracing

1. Add `data-on-signal-patch` handlers to log or forward patch data.
2. Use `data-json-signals` with include/exclude filters for on-screen inspection.
3. Remove tracing attributes once the behavior is verified.

## Resources

- `references/attributes.md`
- `references/clj-sdk.md`
- `references/signal-tracing.md`
