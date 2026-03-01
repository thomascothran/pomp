(ns pomp.rad.datatable
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
            [clojure.data.json :as json]
            [pomp.rad.datatable.handler.export :as export-handler]
            [pomp.rad.datatable.handler.query-render :as query-render-handler]
            [pomp.rad.datatable.handler.save :as save-handler]))

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

(defn- has-id-column?
  [columns]
  (boolean (some (fn [{:keys [key]}] (= :id key)) columns)))

(defn- validate-identity-column!
  [{:keys [id columns selectable?]}]
  (let [editable-columns? (has-editable-columns? columns)
        identity-required? (or selectable? editable-columns?)
        has-identity-column? (has-id-column? columns)]
    (when (and identity-required?
               (not has-identity-column?))
      (throw
       (ex-info
        "Datatable requires an :id column when row selection or editable columns are enabled"
        {:table-id id
         :required-column :id
         :selectable? selectable?
         :editable-columns? editable-columns?
         :column-keys (mapv :key columns)})))))

(defn- handle-query-render-action!
  [opts]
  (query-render-handler/handle-query-render-action! opts))

(defn- make-handler*
  "Creates a Ring handler for a datatable.

   Required options:
   - :id            - Table element ID (string)
   - :columns       - Column definitions [{:key :name :label \"Name\" :type :string} ...]
   - :rows-fn       - Row query function (see `pomp.rad.datatable.query.*`).
                      Query payload includes optional server-derived `:project-columns`
                      for adapters that support projection.
                      SQL adapters can enforce request-scoped visibility by configuring
                      `:visibility-fn` in the SQL query config passed to
                      `pomp.rad.datatable.query.sql/rows-fn`.
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
                      When using SQL helpers, configure the same `:visibility-fn`
                      in `pomp.rad.datatable.query.sql/count-fn` config so totals
                      stay scoped to the request.
   - :render-table-search - Optional global search render function for table toolbar.
   - :save-fn       - Function to save cell edits. Called with {:row-id :col-key :value :req}.
                      See `pomp.rad.datatable.query.sql/save-fn` for SQL implementation.
   - :initial-signals-fn - Optional (fn [req] signals-map) used only when request has no table signals.
                             Useful for seeding saved views or default hidden columns on first load.
   - :export-enabled? - Enables default export control and export action handling (default true).
   - :export-stream-rows-fn - Adapter-injected stream function with signature:
                              (fn [ctx on-row! on-complete!]).
                              SQL streaming should use
                              `pomp.rad.datatable.query.sql/stream-rows-fn` with
                              the same `:visibility-fn` config.
   - :export-filename-fn - Optional (fn [{:keys [id columns query req]}] filename).
   - :export-limits - Optional map passed to adapter stream context under :limits.
   - :render-export - Optional export button render override.

    Query payload contract note:
   - `:project-columns` is optional and additive.
   - SQL adapters use it to optimize projection.
   - Adapters that do not support projection can ignore it without breaking behavior.

    SQL visibility hook contract:
    - `:visibility-fn` => `(fn [query-signals request] nil | {:where-clauses [[\"sql = ?\" param ...] ...]})`
    - Configure it on the SQL query helper config (`rows-fn`, `count-fn`, and
      `stream-rows-fn`), not directly on `make-handler*`.

    Returns a Ring handler function that handles datatable requests via SSE."
  [{:keys [id columns rows-fn count-fn table-search-query data-url render-html-fn
           page-sizes selectable? skeleton-rows render-row render-header render-cell
           filter-operations render-table-search save-fn initial-signals-fn
           export-enabled? export-stream-rows-fn export-limits export-filename-fn render-export]
    :or {page-sizes [10 25 100]
         selectable? false
         skeleton-rows 10
         export-enabled? true}}
   {:keys [save-action? export-action?]}]
  (fn [req]
    (let [query-params (:query-params req)
          raw-signals (get-signals req id)
          action (get query-params "action")
          export-available? (and export-enabled? (some? export-stream-rows-fn))]
      (cond
        (and save-fn (save-action? req action))
        (save-handler/handle-save-action! {:req req
                                           :id id
                                           :raw-signals raw-signals
                                           :query-params query-params
                                           :save-fn save-fn
                                           :extract-cell-edit-fn extract-cell-edit})

        (and export-available? (export-action? req action))
        (export-handler/handle-export-action! {:req req
                                               :id id
                                               :columns columns
                                               :raw-signals raw-signals
                                               :query-params query-params
                                               :initial-signals-fn initial-signals-fn
                                               :export-limits export-limits
                                               :export-filename-fn export-filename-fn
                                               :export-stream-rows-fn export-stream-rows-fn})

        :else
        (handle-query-render-action! {:req req
                                      :id id
                                      :columns columns
                                      :rows-fn rows-fn
                                      :count-fn count-fn
                                      :table-search-query table-search-query
                                      :data-url data-url
                                      :render-html-fn render-html-fn
                                      :page-sizes page-sizes
                                      :selectable? selectable?
                                      :skeleton-rows skeleton-rows
                                      :render-row render-row
                                      :render-header render-header
                                      :render-cell render-cell
                                      :filter-operations filter-operations
                                      :render-table-search render-table-search
                                      :render-export render-export
                                      :raw-signals raw-signals
                                      :query-params query-params
                                      :initial-signals-fn initial-signals-fn
                                      :export-available? export-available?})))))

(defn make-handlers
  "Creates method-specific datatable handlers.

    Returns {:get fn :post fn}.
    - :get always executes the query/render flow.
    - :post executes save flow when query-param action=save, otherwise query/render flow.

    Identity requirement:
    - If :selectable? is true OR any column is :editable true,
      :columns must include an :id key.
    - The :id column may be hidden from UI via signals/column visibility.

    SQL visibility note:
    - To scope rows/count/export per request, configure `:visibility-fn` on the
      SQL query helper config used to build `:rows-fn`, `:count-fn`, and
      `:export-stream-rows-fn`."
  [opts]
  (validate-identity-column! opts)
  {:get (make-handler* opts {:save-action? (constantly false)
                             :export-action? (fn [_ action] (= action "export"))})
   :post (make-handler* opts {:save-action? (fn [_ action] (= action "save"))
                              :export-action? (fn [_ action] (= action "export"))})})
