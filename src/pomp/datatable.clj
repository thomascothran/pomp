(ns pomp.datatable
  "Main entry point for the datatable component.

   Use `make-handlers` to create method-specific Ring handlers for your datatable.

   For more control, you can require the sub-namespaces directly:
   - `pomp.rad.datatable.state.*` for state transition functions
   - `pomp.rad.datatable.ui.*` for rendering functions
   - `pomp.rad.datatable.ui.row` for default row/cell render functions
   - `pomp.rad.datatable.query.*` for query implementations

   Signal schema (Datastar signals under `datatable.<id>`):
   - `_editing` (object) {<row-id> {<col-key> 'active'|'in-flight'}} private per-cell edit state.
   - `cells` (object) {<row-id> {<col-key> value}} edited cell values by row/column.
   - `submitInProgress` (boolean) true while save requests are running.
   - `enumBlurLock` (number) timestamp used to suppress enum blur events.
   - `cellSelectDragging` (boolean) true while drag-selecting cells.
   - `cellSelectStart` (object) {:row number :col number} drag start coordinates.
   - `cellSelection` (vector) [row-col ...] selected cell keys.
   - `selections.<row-id>` (boolean) per-row checkbox selection state.

   Full signal names and shapes:
   - `datatable.<id>._editing` (object) {<row-id> {<col-key> 'active'|'in-flight'}} private per-cell edit state.
   - `datatable.<id>.cells` (object) {<row-id> {<col-key> value}} edited cell values by row/column.
   - `datatable.<id>.submitInProgress` (boolean) true while save requests are running.
   - `datatable.<id>.enumBlurLock` (number) timestamp used to suppress enum blur events.
   - `datatable.<id>.cellSelectDragging` (boolean) true while drag-selecting cells.
   - `datatable.<id>.cellSelectStart` (object) {:row number :col number} drag start coordinates.
   - `datatable.<id>.cellSelection` (vector) [row-col ...] selected cell keys.
   - `datatable.<id>.selections.<row-id>` (boolean) per-row checkbox selection state.
  "
  (:require [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.adapter.ring :refer [->sse-response on-open]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [pomp.icons :as icons]
            [pomp.rad.datatable.state.table :as state]
            [pomp.rad.datatable.ui.table :as table]
            [pomp.rad.datatable.state.column :as column-state]
            [pomp.rad.datatable.state.group :as group-state]
            [pomp.rad.datatable.state.filter :as filter-state]
            [pomp.rad.datatable.ui.columns-menu :as columns-menu]))

(def ^:private cell-select-script
  "JavaScript for cell selection functionality, loaded from classpath."
  (slurp (io/resource "public/pomp/js/datatable.js")))

(defn get-signals
  "Extracts datatable signals from a Ring request for a specific table.

   Handles both:
   - Direct body access (when no body-parsing middleware is present)
   - Muuntaja/similar middleware (when body is already parsed to :body-params)

   Returns the signals map for the given table id, or an empty map if not present."
  [req id]
  (if (get-in req [:headers "datastar-request"])
    (let [;; Try :body-params first (set by muuntaja/format-request-middleware)
          ;; Fall back to reading body directly via d*/get-signals
          signals-data (or (:body-params req)
                           (let [signals-raw (d*/get-signals req)
                                 signals-str
                                 (when signals-raw
                                   (if (string? signals-raw)
                                     signals-raw
                                     (slurp signals-raw)))]
                             (when (seq signals-str)
                               (json/read-str signals-str {:key-fn keyword}))))]
      (some-> signals-data
              :datatable
              (get (keyword id))))
    {}))

(defn extract-cell-edit
  "Extracts a cell edit from the signals.

   The :cells signal format: {row-id-keyword {col-key-keyword \"value\"}}

   Invariant for save extraction:
   - 0 edited-cell candidates in :cells => nil
   - 1 edited-cell candidate in :cells => {:row-id :col-key :value}
   - >1 edited-cell candidates in :cells => throws ex-info

   Returns {:row-id \"...\" :col-key :keyword :value ...} or nil if no edit found."
  [signals]
  (let [candidates
        (vec
         (for [[row-id row-cells] (:cells signals)
               :when (map? row-cells)
               [col-key value] row-cells]
           {:row-id (name row-id)
            :col-key col-key
            :value value}))]
    (case (count candidates)
      0 nil
      1 (first candidates)
      (throw (ex-info "Expected exactly one edited cell"
                      {:candidate-count (count candidates)
                       :candidates candidates})))))

(defn has-editable-columns?
  "Returns true if any column in the columns vector has :editable true."
  [columns]
  (boolean (some :editable columns)))

(defn- make-handler*
  "Creates a Ring handler for a datatable.

   Required options:
   - :id            - Table element ID (string)
   - :columns       - Column definitions [{:key :name :label \"Name\" :type :string} ...]
   - :rows-fn       - Row query function (see `pomp.rad.datatable.query.*`)
   - :data-url      - URL for data fetches (string)
   - :render-html-fn - Function to convert hiccup to HTML string

   Optional options:
   - :page-sizes    - Available page sizes (default [10 25 100])
   - :selectable?   - Enable row selection (default false)
   - :skeleton-rows - Number of skeleton rows on initial load (default 10)
   - :render-row    - Custom row render function (see pomp.rad.datatable.ui.row/render-row)
   - :render-header - Custom header render function (see pomp.rad.datatable.ui.header/render-sortable)
   - :render-cell   - Custom cell render function (see pomp.rad.datatable.ui.row/render-cell)
                      Used by the default render-row; ignored if :render-row is provided
                      unless the custom render-row chooses to use it.
   - :filter-operations - Override filter operations per type or column
                           Map of type keyword to operations vector.
                           Example: {:string [{:value \"contains\" :label \"Includes\"}]
                                    :boolean [{:value \"is\" :label \"equals\"}]}
   - :count-fn      - Optional count query function for total row count.
   - :render-table-search - Optional global search render function for table toolbar.
   - :save-fn       - Function to save cell edits. Called with {:row-id :col-key :value :req}.
                     See `pomp.rad.datatable.query.sql/save-fn` for SQL implementation.
   - :initial-signals-fn - Optional (fn [req] signals-map) used only when request has no table signals.
                           Useful for seeding saved views or default hidden columns on first load.

   Returns a Ring handler function that handles datatable requests via SSE."
  [{:keys [id columns rows-fn count-fn table-search-query data-url render-html-fn
           page-sizes selectable? skeleton-rows render-row render-header render-cell
           filter-operations render-table-search save-fn initial-signals-fn]
    :or {page-sizes [10 25 100]
         selectable? false
         skeleton-rows 10}}
   {:keys [save-action?]}]
  (fn [req]
    (let [query-params (:query-params req)
          raw-signals (get-signals req id)
          action (get query-params "action")]
      ;; Handle save action
      (if (and save-fn (save-action? req action))
        (let [cell-edit (extract-cell-edit raw-signals)]
          (when cell-edit
            (save-fn (assoc cell-edit :req req)))
          ;; Respond with SSE to clear per-cell edit state
          (->sse-response req
                          {on-open
                           (fn [sse]
                             (when cell-edit
                               (let [{:keys [row-id col-key]} cell-edit
                                     editing-path {(keyword row-id) {col-key false}}]
                                 (d*/patch-signals! sse
                                                    (json/write-str
                                                     {:datatable {(keyword id) {:_editing editing-path
                                                                                :cells nil}}}))))
                             (d*/close-sse! sse))}))

        ;; Normal query/render flow
        (let [initial-load? (empty? raw-signals)
              seeded-signals (when (and initial-load? initial-signals-fn)
                               (initial-signals-fn req))
              effective-signals (if (map? seeded-signals)
                                  (merge seeded-signals raw-signals)
                                  raw-signals)
              current-signals (-> effective-signals
                                  (assoc :group-by (mapv keyword (:groupBy effective-signals))))
              columns-state (:columns current-signals)
              column-order (column-state/next-state (:columnOrder current-signals) columns query-params)
              ordered-cols (column-state/reorder columns column-order)
              visible-cols (column-state/filter-visible ordered-cols columns-state)
              run-rows-fn (if table-search-query
                            (fn [query-signals request]
                              (if (seq (:search-string query-signals))
                                (table-search-query (assoc query-signals :columns columns) request)
                                (rows-fn query-signals request)))
                            rows-fn)
              {:keys [signals rows]} (state/query-rows current-signals query-params req run-rows-fn)
              group-by (:group-by signals)
              groups (when (seq group-by) (group-state/group-rows rows group-by))
              filters-patch (filter-state/compute-patch (:filters current-signals) (:filters signals))
              table-signals-patch (cond-> {:sort (:sort signals)
                                           :page (:page signals)
                                           :filters filters-patch
                                           :groupBy (mapv name group-by)
                                           :openFilter ""
                                           :columnOrder column-order
                                           :dragging nil
                                           :dragOver nil}
                                    (or (= action "global-search")
                                        (and table-search-query
                                             (contains? current-signals :globalTableSearch)))
                                    (assoc :globalTableSearch (:search-string signals)))]
          (->sse-response req
                          {on-open
                           (fn [sse]
                             (when initial-load?
                               (d*/patch-elements! sse (render-html-fn (table/render-skeleton {:id id
                                                                                               :cols visible-cols
                                                                                               :n skeleton-rows
                                                                                               :selectable? selectable?})))
                               (d*/execute-script! sse cell-select-script))
                             (d*/patch-signals! sse (json/write-str
                                                     {:datatable {(keyword id) table-signals-patch}}))
                             (let [render-table
                                   (fn [total-rows]
                                     (render-html-fn
                                      (table/render {:id id
                                                     :cols visible-cols
                                                     :rows rows
                                                     :groups groups
                                                     :sort-state (:sort signals)
                                                     :filters (:filters signals)
                                                     :group-by group-by
                                                     :total-rows total-rows
                                                     :page-size (get-in signals [:page :size])
                                                     :page-current (get-in signals [:page :current])
                                                     :page-sizes page-sizes
                                                     :data-url data-url
                                                     :selectable? selectable?
                                                     :render-row render-row
                                                     :render-header render-header
                                                     :render-cell render-cell
                                                     :filter-operations filter-operations
                                                     :render-table-search render-table-search
                                                     :global-table-search (:search-string signals)
                                                     :toolbar (columns-menu/render {:cols ordered-cols
                                                                                    :columns-state columns-state
                                                                                    :table-id id
                                                                                    :data-url data-url})})))]
                               (d*/patch-elements! sse (render-table nil))
                               (when count-fn
                                 (let [total-rows (state/query-count signals req count-fn)]
                                   (d*/patch-elements! sse (render-table total-rows)))))
                             (d*/close-sse! sse))}))))))

(defn make-handlers
  "Creates method-specific datatable handlers.

   Returns {:get fn :post fn}.
   - :get always executes the query/render flow.
   - :post executes save flow when query-param action=save, otherwise query/render flow."
  [opts]
  {:get (make-handler* opts {:save-action? (constantly false)})
   :post (make-handler* opts {:save-action? (fn [_ action] (= action "save"))})})
