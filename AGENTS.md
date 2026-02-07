# AGENTS.md

## Overview
- Pomp is a server-driven UI kit for Datastar and is consumed as a library.
- The server is written in Clojure
- Dynamic components live in `src/pomp/rad` and use Datastar for frontend/backend behavior.
- JavaScript should be injected via Datastar evaluation; do not require users to serve JS assets.
- Prefer daisyUI components over custom CSS or raw Tailwind utilities where possible.

## Repo layout
- Interactive examples live in `dev/demo` and are accessible in the browser.
- Architecture decisions live in `docs/adr`.
- Logs are written to `logs/events.log` in EDN format.

## Development environment
- `devenv` is the canonical environment.
- The user will run `devenv up` to start services and build processes.
- Agents MUST NOT run `devenv up`.

## Testing
- Never run tests via CLI runners.
- Only run tests from a Clojure REPL using `kaocha.repl` functions.
  + for unit tests `(kaocha.repl/run :unit)`
  + scope as necessary. Remember `(kaocha.repl/run)` only runs tests in the current namespace


## REPL workflow
- After any Clojure code change, reload namespaces with `require` and the `:reload` flag.

## Skills

Always use the relevant skills. `clojure-eval` will almost always be relevant.

## Rules

- ALWAYS reload a clojure file at the REPL when it is update with `require` and the `:reload` argument
