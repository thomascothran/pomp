(ns pomp.rad.datatable.core
  "Public API for the datatable component.

   This namespace provides the main entry points for using the datatable:
   - `render` and `render-skeleton` for rendering tables
   - `next-state` and `query` for state management
   - `query-fn` for creating in-memory query functions

   Customization:
   The `render` function accepts `:render-row` and `:render-header` options
   to override the default rendering. See the `render` docstring for details.

   For more control, you can require the sub-namespaces directly:
   - `pomp.rad.datatable.state.*` for state transition functions
   - `pomp.rad.datatable.ui.*` for rendering functions
   - `pomp.rad.datatable.ui.row` for default row/cell render functions
   - `pomp.rad.datatable.query.*` for query implementations"
  (:require [pomp.rad.datatable.state.table :as state]
            [pomp.rad.datatable.state.filter :as filter-state]
            [pomp.rad.datatable.state.group :as group-state]
            [pomp.rad.datatable.ui.table :as ui]
            [pomp.rad.datatable.query.in-memory :as in-memory]))

;; =============================================================================
;; State Management
;; =============================================================================

(defn next-state
  "Computes the next datatable state from current signals and query params.

   (next-state signals query-params) => new-signals

   signals: {:filters {...} :sort [...] :page {...} :group-by [...]}
   query-params: Ring query params map from request"
  [signals query-params]
  (state/next-state signals query-params))

(defn query
  "Executes a query and returns updated signals plus results.

   (query signals query-params query-fn) => {:signals ... :rows ... :total-rows ...}

   This is the main entry point for handling datatable requests."
  [signals query-params query-fn]
  (state/query signals query-params query-fn))

(defn compute-filter-patch
  "Computes a patch for updating filter signals (handles removals).

   (compute-filter-patch old-filters new-filters) => patch-map"
  [old-signals new-signals]
  (filter-state/compute-patch old-signals new-signals))

(defn group-rows
  "Groups rows by the specified columns.

   (group-rows rows group-by-cols) => [{:group-value ... :rows ... :count ...} ...]"

  [rows group-by-cols]
  (group-state/group-rows rows group-by-cols))

;; =============================================================================
;; Rendering
;; =============================================================================

(defn render
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
   - :toolbar      - Toolbar hiccup to render above table

   Render overrides:
   - :render-cell   - Custom cell render function. Receives:
                      {:value any :row map :col map}
                      Used by the default render-row. If :render-row is also provided,
                      :render-cell is passed in the context but the custom render-row
                      must explicitly use it.
                      See pomp.rad.datatable.ui.row/render-cell for default.

   - :render-row    - Custom row render function. Receives:
                      {:cols [...] :row {...} :selectable? bool :row-id id
                       :table-id str :grouped? bool :render-cell fn-or-nil}
                      See pomp.rad.datatable.ui.row/render-row for default.

   - :render-header - Custom header render function. Receives:
                      {:cols [...] :sort-state [...] :filters {...} :data-url str
                       :selectable? bool :table-id str :group-by [...]}
                      See pomp.rad.datatable.ui.header/render-sortable for default."
  [opts]
  (ui/render opts))

(defn render-skeleton
  "Renders a loading skeleton for the datatable.

   (render-skeleton opts) => hiccup

   opts:
   - :id          - Table element ID
   - :cols        - Column definitions
   - :n           - Number of skeleton rows
   - :selectable? - Show selection column"
  [opts]
  (ui/render-skeleton opts))

;; =============================================================================
;; Query
;; =============================================================================

(def query-fn
  "Creates an in-memory query function for the given rows.

   (query-fn rows) => (fn [signals] {:rows ... :total-rows ... :page ...})

   The returned function handles filtering, sorting, and pagination."
  in-memory/query-fn)
