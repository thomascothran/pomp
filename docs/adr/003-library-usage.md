# ADR 003: Library Usage and Extensibility

## Status

Proposed

## Context

The `pomp.rad.datatable` component is a server-rendered datatable for Clojure web applications using Datastar (for reactivity) and DaisyUI (for styling). It currently provides filtering, sorting, pagination, column reordering, column visibility, row grouping, and row selection—all driven by server-side rendering with SSE updates.

The component works well for its designed use case, but the current architecture creates friction when users need to customize behavior or rendering beyond what the options map provides.

## Core Tensions

### 1. Fast Adoption vs. Deep Customization

Users should be able to get a fully functional datatable with minimal configuration (theme + column definitions), but should also have a clear path to deep customization without hitting walls. Currently, customization options are limited—users can provide a `:render` function per column, but cannot easily:
- Make cells editable
- Change row sizing/padding
- Customize selection behavior
- Override header, row, or pagination rendering

### 2. Library Stability vs. User Control

A traditional library approach (pass options, get output) provides stability and easy upgrades, but limits flexibility. A shadcn-style approach (copy source into your project) provides maximum control, but users are on their own for updates. We need a hybrid that gives users control without abandoning them.

### 3. Interleaved Concerns

The current codebase mixes:
- **State logic** (filter/sort/page state transitions) with **UI rendering** (hiccup generation)
- **Primitives** (icons, basic inputs) with **composed components** (filter menus, headers)
- **Datastar-specific attributes** scattered throughout rather than isolated

This makes it hard to:
- Understand what's safe to override
- Upgrade logic without breaking custom UI
- Provide render function hooks with stable contracts

## Requirements

### Hard Constraints

- Server-rendered HTML over SSE (no SPA/ClojureScript client)
- Datastar for reactivity (signals, SSE actions)
- DaisyUI for styling (or at least DaisyUI-compatible class names)
- `.cljc` compatibility (Clojure + ClojureScript compilation)

### HTTP Semantics for Table Operations

- `GET` - Load/refresh table state
- `PUT` - Table-level operations (pagination, filtering, sorting, grouping)
- `POST` - Row-level mutations (cell edits), with signals indicating row ID, cell name, and value

### Customization Layers

1. **Config values**: Simple options like `:size :sm`, `:selectable? true` that modify behavior without requiring user code
2. **Render function overrides**: User-provided functions (`:render-cell`, `:render-row`, `:render-header`, `:on-select-all`) that receive the full signal map plus query results
3. **Source ownership**: CLI-based copying of source into user's project for maximum control, leveraging git for change tracking

### Signal Contract

The signal map structure must be documented and stable, as it forms the API contract for render function overrides. User-provided render functions receive:
- The full datatable signal map (filters, sort, page, selections, etc.)
- Query output (rows, total-rows, groups)
- Column/row context as appropriate

### Namespace Organization

Clear separation into:
- `state/` - Pure state transition functions (filter, sort, page, column, group, selection)
- `ui/` - Rendering functions, further split into primitives (cells, icons) and composed components (header, body, menus)
- `query/` - Query function implementations (in-memory, with room for DB-backed)
- `core` - Public API that wires everything together with sensible defaults

## Success Criteria

1. A new user can render a functional datatable with ~10 lines of configuration
2. A user needing custom cell rendering can provide a `:render-cell` function without understanding the full codebase
3. A user needing editable cells has a clear pattern: column spec indicates editability, cells render as forms, POST handles mutations
4. A user wanting full control can run a CLI command to copy the source, then modify freely with git tracking changes
5. The library maintainer can update state logic without breaking users who only override UI
6. The signal map structure is documented and changes follow semantic versioning

## Solution Approaches

### Approach A: Pure Library with Rich Options Map

**Description**: Keep everything as a library dependency. Expand the options map to support all customization needs through config values and render function overrides.

**Structure**:
```clojure
(datatable/render
  {:columns [...]
   :query-fn my-query-fn
   :data-url "/api/table"
   ;; Layer 1: Config values
   :size :sm                    ; :xs :sm :md :lg
   :selectable? true
   :editable? true              ; enables POST-based cell editing
   ;; Layer 2: Render overrides
   :render-cell (fn [{:keys [value row col signals]}] ...)
   :render-row (fn [{:keys [row cols signals]}] ...)
   :render-header (fn [{:keys [cols signals]}] ...)
   :on-select-all (fn [{:keys [signals]}] ...)})
```

**Pros**:
- Simple dependency management (just add to deps.edn)
- Upgrades are easy for users who stick to the API
- Single source of truth

**Cons**:
- Options map can explode in complexity
- Deep customization still limited by what hooks you expose
- Users who need "one small tweak" outside the API are stuck

---

### Approach B: shadcn-Style Copy with CLI

**Description**: Provide a CLI tool that copies the datatable source into the user's project. Users own the code and modify freely. Git tracks their changes.

**Usage**:
```bash
# Initial copy
clj -M:pomp copy datatable --target src/myapp/components/

# Later: see what changed upstream
clj -M:pomp diff datatable --target src/myapp/components/

# User manually reconciles using git diff
```

**Structure after copy**:
```
src/myapp/components/datatable/
  core.clj
  state/
    filter.clj
    sort.clj
    ...
  ui/
    primitives.clj
    header.clj
    body.clj
    ...
  query/
    in_memory.clj
```

**Pros**:
- Maximum flexibility—users own everything
- No API surface area to maintain
- Clean namespace separation makes it easy to modify just what you need

**Cons**:
- Users are largely on their own for updates
- Harder to share improvements back
- Every user has a fork

---

### Approach C: Hybrid—Stable Core + Copyable UI

**Description**: Split the library into two parts:
1. **Core library** (`pomp.rad.datatable.state`, `pomp.rad.datatable.query`): Pure state logic, versioned as a normal dependency
2. **Reference UI** (`pomp.rad.datatable.ui`): Copyable via CLI into user's project

Users depend on the stable state/query namespaces normally, but own their UI layer.

**Structure**:
```
;; Library (deps.edn dependency)
pomp.rad.datatable.state.filter
pomp.rad.datatable.state.sort
pomp.rad.datatable.state.page
pomp.rad.datatable.state.selection
pomp.rad.datatable.query.in-memory

;; Copied into user project
myapp.datatable.ui.core
myapp.datatable.ui.primitives
myapp.datatable.ui.header
myapp.datatable.ui.body
myapp.datatable.ui.filter-menu
```

**User's UI code imports from stable library**:
```clojure
(ns myapp.datatable.ui.header
  (:require [pomp.rad.datatable.state.filter :as filter-state]
            [pomp.rad.datatable.state.sort :as sort-state]))
```

**Pros**:
- State logic is stable and upgradeable
- UI is fully customizable
- Clear boundary: "library handles state, you handle rendering"
- Render overrides still possible for users who don't want to copy

**Cons**:
- More complex mental model
- Need to carefully design the state/UI boundary
- CLI tooling still needed for the UI copy

---

### Approach D: Layered Library with "Eject" Escape Hatch

**Description**: Default to Approach A (rich options map), but provide an "eject" command that copies everything when users outgrow the API. Once ejected, they're on Approach B.

**Workflow**:
1. Start with library dependency, use options map
2. Hit a wall? Run `clj -M:pomp eject datatable`
3. Now you own the code, modify freely

**Pros**:
- Smooth on-ramp (just use the library)
- Escape hatch when needed
- Clear "you're on your own now" boundary

**Cons**:
- Ejection is one-way—hard to merge upstream improvements
- Still need to maintain both the library API and the ejectable source

---

## Recommendation

**Approach C (Hybrid—Stable Core + Copyable UI)** is recommended because:

1. **State logic is the hard part to get right**—filter state transitions, pagination edge cases, signal structure. Users benefit from this being stable and maintained.

2. **UI is where customization needs are highest**—cell rendering, row styling, menu layouts. This is also where Datastar/DaisyUI coupling lives.

3. **The boundary is natural**—state functions are pure (`signals, query-params -> new-signals`), UI functions take state and produce hiccup. These don't need to be interleaved.

4. **Supports all user types**:
   - Quick start: Use the reference UI as-is (don't copy, just require it)
   - Medium customization: Use options map overrides
   - Full control: Copy UI, keep state dependency

The namespace restructuring work is the same regardless of approach—we need to separate concerns anyway. Approach C just makes that separation load-bearing.

## Decision

We will take an **incremental approach**, starting with the most flexible foundation and adding convenience layers only as needed:

### Phase 1: Render Function Overrides (Approach A, flexibility-first)

Start with render function overrides as the primary customization mechanism:

```clojure
(datatable/render
  {:columns [...]
   :query-fn my-query-fn
   :data-url "/api/table"
   :render-cell (fn [{:keys [value row col signals]}] ...)
   :render-row (fn [{:keys [row cols signals]}] ...)
   :render-header (fn [{:keys [cols signals]}] ...)
   :on-select-all (fn [{:keys [signals]}] ...)})
```

This provides maximum flexibility from day one. Users who need custom behavior have a clear path.

### Phase 2: Config Value Conveniences (if needed)

Based on actual usage patterns, add simple config values that map to pre-built render functions:

```clojure
(datatable/render
  {:columns [...]
   :size :sm  ; => uses render-row-sm internally
   :selectable? true
   :editable? true})
```

Internally, these are just defaults:

```clojure
(let [render-row (or (:render-row opts)
                     (case (:size opts :sm)
                       :xs render-row-xs
                       :sm render-row-sm
                       :md render-row-md))]
  ...)
```

We only add config values for patterns that emerge as common needs.

### Phase 3: CLI Copy Escape Hatch (if needed)

If users hit walls that render overrides can't solve, provide a CLI that copies the UI layer into their project (Approach C):

```bash
clj -M:pomp copy datatable-ui --target src/myapp/components/
```

Users then own the UI code while still depending on the stable `state/` and `query/` namespaces from the library.

### Prerequisite: Namespace Separation

Regardless of phase, the **namespace restructuring is required now**. Clean separation of `state/`, `ui/`, and `query/` is what makes render function overrides composable and a future CLI copy feasible.

Target structure:

```
pomp.rad.datatable/
  core.cljc              ; Public API
  state/
    filter.cljc          ; Pure: signals, query-params -> new-filter-state
    sort.cljc            ; Pure: signals, query-params -> new-sort-state
    page.cljc            ; Pure: signals, query-params -> new-page-state
    column.cljc          ; Pure: column ordering/visibility
    group.cljc           ; Pure: grouping state
    selection.cljc       ; Pure: selection state
  ui/
    primitives.cljc      ; Icons, basic inputs
    cell.cljc            ; Cell rendering
    row.cljc             ; Row rendering
    header.cljc          ; Header rendering
    body.cljc            ; Body (rows + groups)
    filter_menu.cljc     ; Filter dropdown
    column_menu.cljc     ; Column context menu
    columns_menu.cljc    ; Column visibility menu
    pagination.cljc      ; Pagination controls
    table.cljc           ; Top-level table composition
  query/
    in_memory.cljc       ; In-memory filtering/sorting/pagination
```

### Signal Contract

The signal map structure must be documented as part of this work, since it forms the API contract for render function overrides:

```clojure
{:filters {:col-key {:type "text" :op "contains" :value "search"}}
 :sort [{:column "name" :direction "asc"}]
 :page {:size 10 :current 0}
 :group-by [:region]
 :selections {"row-1" true "row-2" false}
 :columns {:name {:visible true} :email {:visible false}}
 :column-order [:name :email :role]
 :expanded {0 true 1 false}  ; group expansion state
 :dragging nil
 :drag-over nil}
```

## Consequences

### Positive

- **Flexibility from day one**: Users with custom needs aren't blocked waiting for config options
- **No premature abstraction**: We don't build config sugar until we know what's actually needed
- **No wasted CLI tooling**: We don't build copy infrastructure until the library approach proves insufficient
- **Clean architecture regardless**: Namespace separation benefits all approaches and makes the codebase more maintainable

### Negative

- **Render functions require more user code**: Users who just want `:size :sm` have to wait for Phase 2 or write their own render function
- **Steeper initial learning curve**: Understanding the signal contract is required for any customization
- **Documentation burden**: Signal structure must be documented and kept stable

### Risks

- **Signal contract ossification**: Once documented, the signal structure becomes hard to change. We need to get it right early.
- **Render function API creep**: If we're not careful, render functions will accumulate parameters over time. Need discipline to keep contracts minimal.

## Next Steps

1. Restructure namespaces into `state/`, `ui/`, `query/` layout
2. Document the signal contract
3. Define render function signatures and implement override support
4. Update demo to exercise the new API
5. Evaluate need for Phase 2/3 based on usage
