(ns pomp.rad.datatable.core
  "Public API for the datatable component.
   
   This namespace provides the main entry points for using the datatable:
   - `render` and `render-skeleton` for rendering tables
   - `next-state` and `query` for state management
   - `query-fn` for creating in-memory query functions
   
   For more control, you can require the sub-namespaces directly:
   - `pomp.rad.datatable.state.*` for state transition functions
   - `pomp.rad.datatable.ui.*` for rendering functions
   - `pomp.rad.datatable.query.*` for query implementations"
  (:require [pomp.rad.datatable.state.table :as state]
            [pomp.rad.datatable.state.filter :as filter-state]
            [pomp.rad.datatable.state.group :as group-state]
            [pomp.rad.datatable.ui.table :as ui]
            [pomp.rad.datatable.query.in-memory :as in-memory]))

;; =============================================================================
;; State Management
;; =============================================================================

(def next-state
  "Computes the next datatable state from current signals and query params.
   
   (next-state signals query-params) => new-signals
   
   signals: {:filters {...} :sort [...] :page {...} :group-by [...]}
   query-params: Ring query params map from request"
  state/next-state)

(def query
  "Executes a query and returns updated signals plus results.
   
   (query signals query-params query-fn) => {:signals ... :rows ... :total-rows ...}
   
   This is the main entry point for handling datatable requests."
  state/query)

(def compute-filter-patch
  "Computes a patch for updating filter signals (handles removals).
   
   (compute-filter-patch old-filters new-filters) => patch-map"
  filter-state/compute-patch)

(def group-rows
  "Groups rows by the specified columns.
   
   (group-rows rows group-by-cols) => [{:group-value ... :rows ... :count ...} ...]"
  group-state/group-rows)

;; =============================================================================
;; Rendering
;; =============================================================================

(def render
  "Renders a complete datatable with header, body, and pagination.
   
   (render opts) => hiccup
   
   Required opts:
   - :id          - Table element ID
   - :cols        - Column definitions [{:key :name :label \"Name\" :type :text} ...]
   - :rows        - Data rows to display
   - :data-url    - URL for data fetches
   
   Optional opts:
   - :groups       - Grouped row data (from group-rows)
   - :sort-state   - Current sort state [{:column \"name\" :direction \"asc\"}]
   - :filters      - Current filter state {:col-key {:type \"text\" :op \"contains\" :value \"...\"}}
   - :group-by     - Columns to group by [:col-key]
   - :total-rows   - Total row count for pagination
   - :page-size    - Current page size
   - :page-current - Current page (0-indexed)
   - :page-sizes   - Available page sizes [10 25 100]
   - :selectable?  - Enable row selection
   - :row-id-fn    - Function to get row ID (default: :id)
   - :toolbar      - Toolbar hiccup to render above table"
  ui/render)

(def render-skeleton
  "Renders a loading skeleton for the datatable.
   
   (render-skeleton opts) => hiccup
   
   opts:
   - :id          - Table element ID
   - :cols        - Column definitions
   - :n           - Number of skeleton rows
   - :selectable? - Show selection column"
  ui/render-skeleton)

;; =============================================================================
;; Query
;; =============================================================================

(def query-fn
  "Creates an in-memory query function for the given rows.
   
   (query-fn rows) => (fn [signals] {:rows ... :total-rows ... :page ...})
   
   The returned function handles filtering, sorting, and pagination."
  in-memory/query-fn)
