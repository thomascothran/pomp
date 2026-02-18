# AGENTS.md

## Overview
- Pomp is a server-driven UI kit for Datastar and is consumed as a library.
- The server is written in Clojure
- Dynamic components live in `src/pomp/rad` and use Datastar for frontend/backend behavior.
- JavaScript should be injected via Datastar evaluation; do not require users to serve JS assets.
- Prefer daisyUI components over custom CSS or raw Tailwind utilities where possible.

## Repo layout
- Interactive examples live in `dev/` and are accessible in the browser.
  + `dev/demo`: full, polished examples of a feature
  + `dev/scratch` includes one-off experiments, it can be used to:
    * see what things look like
    * debug
    * experiment with datastar
- Architecture decisions live in `docs/adr`.
- Logs are written to `logs/events.log` in EDN format.

## Development environment
- `devenv` is the canonical environment.
- The user will run `devenv up` to start services and build processes.
- Agents MUST NOT run `devenv up`.

### Http Ports

- The development server runs on port 3000
- The tests run on a port defined in the test fixtures

## Testing
- Never run tests via CLI runners.
- Only run tests from a Clojure REPL using `kaocha.repl` functions.
  + Example: for unit tests `(kaocha.repl/run :unit)`
  + See tests.edn for full test suite list
  + scope as necessary. Remember `(kaocha.repl/run)` only runs tests in the current namespace
- Timeouts in the test suite are often because the css selector or xpath of an element has changed, not because something is slow to load

## Skills

Always use the relevant skills. `clojure-eval` will almost always be relevant.

To investigate clojure library code, use the `clojure-eval` and `clj-debug` skills. Don't try to look in the .m2 folder.

## Design

### Overridable
Users should be able to override the rendering of any component in Clojure on the backend by passing functions. The datatable provides a good example of how to do this.

### Helpers
We provide sql and in memory helpers for querying and updating state.

## IMPORTANT RULES

- If a clojure source file has been changed, ALWAYS evaluate `load-file` in the clojure repl to refresh it.
  + Otherwise you will have stale state.
- NEVER install anything unless explicitly instructed to by the user
- NEVER commit or stage anything unless instructed by the user. Only use git for read only operations unless specifically instructed by the user
- Avoid using the filesystem outside this repository.
  - For temporary files, use ./tmp instead of /tmp
  - To discover Clojure library code, use the `clojure-eval` and `clojure-dbg` skills instead of looking in .m2
- NEVER use playwright etc - if you need a browser, follow the browser skill
- NEVER try to use python, node, etc to do data analysis. Use the clojure repl as described in the clojure-eval skill
