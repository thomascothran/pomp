# Pomp :peacock:

Server-driven web UI kit for **Datastar**, with dynamic components and pluggable styling.

* Datastar drives the DOM via **element patches** (morphing by default) delivered as `text/html` fragments or **SSE** (`text/event-stream`) events like `datastar-patch-elements`. Morphing updates only what changed and preserves focus/state. ([Datastar][1])
* Server-driven components with rich behaviors -- e.g., datatables.
* Styling pluggable via multimethods. A default implementation is
