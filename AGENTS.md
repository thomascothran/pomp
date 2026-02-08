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

## Skills

Always use the relevant skills. `clojure-eval` will almost always be relevant.

## IMPORTANT RULES

- If a clojure source file has been changed, ALWAYS call load-file to refresh it.
  + Otherwise you will have stale state.
- NEVER install anything unless explicitly instructed to by the user
- NEVER commit or stage anything unless instructed by the user. Only use git for read only operations unless specifically instructed by the user
