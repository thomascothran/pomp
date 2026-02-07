# ADR: Simplify Datatable Signals and Handlers

**Status:** Proposed
**Date:** 2026-02-06
**Context:** The datatable UI wires selection, editing, and copy behavior through Datastar signals and inline `data-on:*` handlers in `src/pomp/rad/datatable/ui/table.cljc`, `src/pomp/rad/datatable/ui/row.cljc`, and `src/pomp/rad/datatable/ui/cell/editable.cljc`. Selection drag and copy are assisted by `pompCellSelectMove` and `pompCellSelectCopy` in `resources/public/pomp/js/datatable.js`.

---

## Problem

Editing, selection, and dropdown interactions are coordinated across multiple handler surfaces with string-built JS, while state is split across many signals. This creates implicit precedence rules (edit vs selection vs dropdown) that are not centralized, and makes it hard to change one behavior without unintended side effects in another.

Specific pain points include the `editable-mousedown-handler`, `enumBlurLock`, and competing click contexts (drag selection, edit entry, dropdown selection). The current signal model also normalizes selection between array, map, empty array, and null, while cell highlighting assumes a single shape.

## Event Surface (cell/row/column/table/window)

- Cell
  - `mousedown` on `td` starts drag selection and can cancel other edits.
  - Pencil click enters edit mode and clears selection.
  - Input `keydown`, `blur`, and change handlers submit or cancel.
  - Enum `blur` is guarded by `enumBlurLock` timing.
- Row
  - Checkbox `change` toggles `datatable.<id>.selections.<row-id>`.
  - Group header checkbox uses `@setAll` to set row selections.
- Column
  - Sort and filter interactions exist but are out of scope for this signal analysis.
- Table
  - `mousemove` calls `pompCellSelectMove` and dispatches `pompcellselection`.
  - `pompcellselection` normalizes selection and updates `cellSelection`.
  - `data-class` toggles `select-none` while dragging.
- Window
  - `mouseup` clears drag state.
  - `keydown` clears selection on Escape and triggers copy on Ctrl or Cmd+C.

## Signals Involved

- `datatable.<id>.editing` and `datatable.<id>.cells.<row-id>.<col-key>` track edit target and values.
- `datatable.<id>.submitInProgress` guards blur after submit.
- `datatable.<id>.enumBlurLock` suppresses enum blur races.
- `datatable.<id>.cellSelectDragging` and `datatable.<id>.cellSelectStart` track drag selection.
- `datatable.<id>.cellSelection` stores selected cell keys.
- `datatable.<id>.selections.<row-id>` tracks row checkbox selection.
- `datatable.<id>.expanded` controls grouped row expansion.

## Complexity Drivers

- String-built handlers are spread across table, row, and cell namespaces, making ownership unclear.
- Selection state is normalized between multiple shapes while highlight logic expects one type.
- Editing and selection cancel each other across different event surfaces.
- `enumBlurLock` uses timing to avoid blur races, indicating fragile event ordering.
- Three click contexts compete: drag selection, edit entry, and dropdown selection.

## Options (A-D)

- Option A: Keep signal-first flow, but normalize selection shape and clearing semantics.
  - Pros: Minimal change, preserves Datastar-first approach.
  - Cons: Handler sprawl and cross-cutting state remain.
- Option B: Split public signals from private or computed UI state.
  - Pros: Reduces serialized payloads and clarifies state ownership.
  - Cons: Requires more JS or computed-signal logic and reinit on re-render.
- Option C: Per-type edit entry rules (double-click for text and number, explicit edit for enum and boolean).
  - Pros: Reduces accidental edits and clarifies click intent.
  - Cons: Dblclick vs drag conflicts and mobile UX considerations.
- Option D: Remove `enumBlurLock` by changing enum exit semantics.
  - Pros: Removes timing hacks and simplifies handlers.
  - Cons: Changes cancel behavior and may require explicit cancel UI.

## Open Questions

- Should `cellSelection` be a single canonical type (array or null) with no map support?
- Are selection signals needed server-side, or should they stay client-only?
- Should edit and selection share a single state machine, or stay independent with explicit rules?
- Do we want optimistic updates, and is `submitInProgress` the right abstraction?
- Should copy always prefer `data-value` over rendered text, and how to handle missing values?
