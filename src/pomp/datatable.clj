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
            [clojure.string :as str]
            [pomp.icons :as icons]
            [pomp.rad.datatable.state.table :as state]
            [pomp.rad.datatable.ui.table :as table]
            [pomp.rad.datatable.ui.export-button :as export-button]
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

(defn- normalize-col-key
  [col-key]
  (cond
    (keyword? col-key) col-key
    (string? col-key) (keyword col-key)
    :else nil))

(defn- derive-project-columns
  [columns ordered-cols columns-state query-signals]
  (let [known-col-keys (set (map :key columns))
        visible-keys (->> ordered-cols
                          (filter (fn [{:keys [key]}]
                                    (get-in columns-state [key :visible] true)))
                          (map :key))
        filter-keys (->> (keys (:filters query-signals))
                         (map normalize-col-key))
        sort-keys (->> (:sort query-signals)
                       (map :column)
                       (map normalize-col-key))
        group-keys (->> (:group-by query-signals)
                        (map normalize-col-key))
        projected-cols (->> (concat [:id] visible-keys filter-keys sort-keys group-keys)
                            (filter known-col-keys)
                            distinct
                            vec)]
    (when (seq projected-cols)
      projected-cols)))

(defn- derive-export-columns
  [ordered-cols]
  (->> ordered-cols
       (map :key)
       distinct
       vec))

(defn- csv-cell
  [value]
  (let [cell (if (nil? value) "" (str value))]
    (if (re-find #"[\",\n\r]" cell)
      (str "\"" (str/replace cell "\"" "\"\"") "\"")
      cell)))

(defn- csv-line
  [row columns]
  (str (str/join "," (map (fn [col-key]
                            (csv-cell (get row col-key)))
                          columns))
       "\n"))

(defn- emit-export-script!
  [sse fn-name payload]
  (let [result (d*/execute-script! sse
                                   (str "if (typeof window !== 'undefined' && typeof window."
                                        fn-name
                                        " === 'function') { window."
                                        fn-name
                                        "("
                                        (json/write-str payload)
                                        "); }"))]
    (when (false? result)
      (throw (ex-info "Export stream disconnected"
                      {:type :export-disconnected
                       :fn-name fn-name})))))

(defn- default-export-filename
  [table-id]
  (str table-id "-export.csv"))

(defn- utf8-bytes
  [s]
  (count (.getBytes ^String s "UTF-8")))

(defn- run-export-stream!
  [{:keys [id sse export-filename export-columns export-stream-rows-fn stream-context export-limits]}]
  (let [chunk-size (max 1 (long (or (:chunk-rows export-limits) 100)))
        timeout-ms (:timeout-ms export-limits)
        max-rows (:max-rows export-limits)
        max-bytes (:max-bytes export-limits)
        deadline-ms (when timeout-ms (+ (System/currentTimeMillis) timeout-ms))
        row-count (volatile! 0)
        byte-count (volatile! 0)
        chunk-row-count (volatile! 0)
        chunk-buffer (volatile! "")
        fail-if-timeout! (fn []
                           (when (and deadline-ms (> (System/currentTimeMillis) deadline-ms))
                             (throw (ex-info "Export exceeded timeout"
                                             {:type :export-timeout
                                              :timeout-ms timeout-ms}))))
        flush-chunk! (fn []
                       (when (seq @chunk-buffer)
                         (emit-export-script! sse
                                              "pompDatatableExportAppend"
                                              {:tableId id
                                               :chunk @chunk-buffer})
                         (vreset! chunk-buffer "")
                         (vreset! chunk-row-count 0)))
        on-row! (fn [row]
                  (fail-if-timeout!)
                  (let [line (csv-line row export-columns)
                        next-row-count (inc @row-count)
                        next-byte-count (+ @byte-count (utf8-bytes line))]
                    (when (and max-rows (> next-row-count max-rows))
                      (throw (ex-info "Export exceeded max rows"
                                      {:type :export-max-rows
                                       :max-rows max-rows
                                       :row-count next-row-count})))
                    (when (and max-bytes (> next-byte-count max-bytes))
                      (throw (ex-info "Export exceeded max bytes"
                                      {:type :export-max-bytes
                                       :max-bytes max-bytes
                                       :byte-count next-byte-count})))
                    (vreset! row-count next-row-count)
                    (vreset! byte-count next-byte-count)
                    (vreset! chunk-buffer (str @chunk-buffer line))
                    (let [next-chunk-count (inc @chunk-row-count)]
                      (vreset! chunk-row-count next-chunk-count)
                      (when (>= next-chunk-count chunk-size)
                        (flush-chunk!)))))
        on-complete! (fn [metadata]
                       (fail-if-timeout!)
                       (flush-chunk!)
                       (emit-export-script! sse
                                            "pompDatatableExportFinish"
                                            {:tableId id
                                             :filename export-filename
                                             :metadata (merge metadata
                                                              {:row-count @row-count
                                                               :byte-count @byte-count})}))]
    (if timeout-ms
      (let [stream-future (future
                            (export-stream-rows-fn stream-context on-row! on-complete!))
            timeout-result (deref stream-future timeout-ms ::timeout)]
        (when (= ::timeout timeout-result)
          (future-cancel stream-future)
          (throw (ex-info "Export exceeded timeout"
                          {:type :export-timeout
                           :timeout-ms timeout-ms}))))
      (export-stream-rows-fn stream-context on-row! on-complete!))))

(defn- make-handler*
  "Creates a Ring handler for a datatable.

   Required options:
   - :id            - Table element ID (string)
   - :columns       - Column definitions [{:key :name :label \"Name\" :type :string} ...]
   - :rows-fn       - Row query function (see `pomp.rad.datatable.query.*`).
                     Query payload includes optional server-derived `:project-columns`
                     for adapters that support projection.
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
    - :export-enabled? - Enables default export control and export action handling (default true).
    - :export-stream-rows-fn - Adapter-injected stream function with signature:
                               (fn [ctx on-row! on-complete!]).
    - :export-filename-fn - Optional (fn [{:keys [id columns query req]}] filename).
    - :export-limits - Optional map passed to adapter stream context under :limits.
    - :render-export - Optional export button render override.

    Query payload contract note:
   - `:project-columns` is optional and additive.
   - SQL adapters use it to optimize projection.
   - Adapters that do not support projection can ignore it without breaking behavior.

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

        ;; Handle export action
        (if (and export-available? (export-action? req action))
          (let [initial-load? (empty? raw-signals)
                seeded-signals (when (and initial-load? initial-signals-fn)
                                 (initial-signals-fn req))
                effective-signals (if (map? seeded-signals)
                                    (merge seeded-signals raw-signals)
                                    raw-signals)
                current-signals (-> effective-signals
                                    (assoc :group-by (mapv keyword (:groupBy effective-signals))))
                column-order (column-state/next-state (:columnOrder current-signals) columns query-params)
                ordered-cols (column-state/reorder columns column-order)
                export-columns (derive-export-columns ordered-cols)
                export-query (-> (state/next-state current-signals query-params)
                                 (assoc :columns columns
                                        :project-columns export-columns
                                        :group-by []
                                        :page nil))
                labels-by-key (into {}
                                    (map (fn [{:keys [key label]}]
                                           [key (or label (name key))])
                                         ordered-cols))
                header-row (csv-line labels-by-key export-columns)
                export-filename (if export-filename-fn
                                  (export-filename-fn {:id id
                                                       :columns ordered-cols
                                                       :query export-query
                                                       :req req})
                                  (default-export-filename id))
                stream-context {:query export-query
                                :columns export-columns
                                :request req
                                :limits export-limits}]
            (->sse-response req
                            {on-open
                             (fn [sse]
                               (try
                                 (emit-export-script! sse
                                                      "pompDatatableExportBegin"
                                                      {:tableId id
                                                       :filename export-filename
                                                       :header header-row})
                                 (run-export-stream! {:id id
                                                      :sse sse
                                                      :export-filename export-filename
                                                      :export-columns export-columns
                                                      :export-stream-rows-fn export-stream-rows-fn
                                                      :stream-context stream-context
                                                      :export-limits export-limits})
                                 (catch Throwable ex
                                   (try
                                     (emit-export-script! sse
                                                          "pompDatatableExportFail"
                                                          {:tableId id
                                                           :message (or (ex-message ex) "Export failed")})
                                     (catch Throwable fail-ex
                                       (when-not (= :export-disconnected (:type (ex-data fail-ex)))
                                         (throw fail-ex)))))
                                 (finally
                                   (d*/close-sse! sse))))}))

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
                run-rows-fn (fn [query-signals request]
                              (let [project-columns (derive-project-columns columns ordered-cols columns-state query-signals)
                                    query-signals* (assoc query-signals
                                                          :columns columns
                                                          :project-columns project-columns)]
                                (if (and table-search-query
                                         (seq (:search-string query-signals*)))
                                  (table-search-query query-signals* request)
                                  (rows-fn query-signals* request))))
                count-signals (state/next-state current-signals query-params)
                count-task (future
                             (when count-fn
                               (state/query-count count-signals req count-fn)))
                {:keys [signals rows]} (state/query-rows current-signals query-params req run-rows-fn)
                group-by (:group-by signals)
                groups (when (seq group-by) (group-state/group-rows rows group-by))
                filters-patch (filter-state/compute-patch (:filters current-signals) (:filters signals))
                export-render-fn (or render-export export-button/render)
                toolbar-right-controls [:div.flex.items-center.gap-2
                                        (when export-available?
                                          (export-render-fn {:table-id id
                                                             :data-url data-url}))
                                        (columns-menu/render {:cols ordered-cols
                                                              :columns-state columns-state
                                                              :table-id id
                                                              :data-url data-url})]
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
                                                       :toolbar toolbar-right-controls})))]
                                 (d*/patch-elements! sse (render-table nil))
                                 (when count-fn
                                   (let [total-rows @count-task]
                                     (d*/patch-elements! sse (render-table total-rows)))))
                               (d*/close-sse! sse))})))))))

(defn make-handlers
  "Creates method-specific datatable handlers.

    Returns {:get fn :post fn}.
    - :get always executes the query/render flow.
    - :post executes save flow when query-param action=save, otherwise query/render flow.

   Identity requirement:
   - If :selectable? is true OR any column is :editable true,
     :columns must include an :id key.
   - The :id column may be hidden from UI via signals/column visibility."
  [opts]
  (validate-identity-column! opts)
  {:get (make-handler* opts {:save-action? (constantly false)
                             :export-action? (fn [_ action] (= action "export"))})
   :post (make-handler* opts {:save-action? (fn [_ action] (= action "save"))
                              :export-action? (fn [_ action] (= action "export"))})})
