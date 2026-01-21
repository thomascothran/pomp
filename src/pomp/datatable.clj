(ns pomp.datatable
  "Main entry point for the datatable component.

   Use `make-handler` to create a Ring handler for your datatable.

   For more control, you can require the sub-namespaces directly:
   - `pomp.rad.datatable.state.*` for state transition functions
   - `pomp.rad.datatable.ui.*` for rendering functions
   - `pomp.rad.datatable.ui.row` for default row/cell render functions
   - `pomp.rad.datatable.query.*` for query implementations
  "
  (:require [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.adapter.ring :refer [->sse-response on-open]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
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
                                 signals-str (if (string? signals-raw)
                                               signals-raw
                                               (slurp signals-raw))]
                             (when (seq signals-str)
                               (json/read-str signals-str {:key-fn keyword}))))]
      (some-> signals-data
              :datatable
              (get (keyword id))))
    {}))

(defn extract-cell-edit
  "Extracts a cell edit from the signals.

   Uses the :editing state to determine which cell is being edited,
   then retrieves the value from the :cells map.

   The :editing signal format: {:rowId \"...\" :colKey \"...\"}
   The :cells signal format: {row-id-keyword {col-key-keyword \"value\"}}

   Returns {:row-id \"...\" :col-key :keyword :value \"...\"} or nil if no edit found."
  [signals]
  (when-let [editing (:editing signals)]
    (when-let [row-id (:rowId editing)]
      (when-let [col-key-str (:colKey editing)]
        (let [col-key (keyword col-key-str)
              ;; Row ID in cells is a keyword (from JSON parsing)
              row-id-kw (keyword row-id)
              value (get-in signals [:cells row-id-kw col-key])]
          {:row-id row-id
           :col-key col-key
           :value value})))))

(defn has-editable-columns?
  "Returns true if any column in the columns vector has :editable true."
  [columns]
  (boolean (some :editable columns)))

(defn- render-cell-display
  "Renders the display content for a cell based on column type.

   For boolean columns, renders a checkmark or X icon.
   Handles both boolean values (true/false) and string representations (\"true\"/\"false\")
   since Datastar may send checkbox values as strings.

   For other types, returns the value as-is."
  [value col-type]
  (case col-type
    :boolean (let [bool-value (cond
                                (boolean? value) value
                                (= "true" value) true
                                :else false)]
               (if bool-value
                 [:svg.w-4.h-4.text-success {:xmlns "http://www.w3.org/2000/svg"
                                             :fill "none"
                                             :viewBox "0 0 24 24"
                                             :stroke-width "2"
                                             :stroke "currentColor"}
                  [:path {:stroke-linecap "round"
                          :stroke-linejoin "round"
                          :d "m4.5 12.75 6 6 9-13.5"}]]
                 [:svg.w-4.h-4.text-base-content.opacity-30 {:xmlns "http://www.w3.org/2000/svg"
                                                             :fill "none"
                                                             :viewBox "0 0 24 24"
                                                             :stroke-width "2"
                                                             :stroke "currentColor"}
                  [:path {:stroke-linecap "round"
                          :stroke-linejoin "round"
                          :d "M6 18 18 6M6 6l12 12"}]]))
    ;; Default: return value as-is
    value))

(defn make-handler
  "Creates a Ring handler for a datatable.

   Required options:
   - :id            - Table element ID (string)
   - :columns       - Column definitions [{:key :name :label \"Name\" :type :string} ...]
   - :query-fn      - Query function (see `pomp.rad.datatable.core/query-fn`)
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
   - :save-fn       - Function to save cell edits. Called with {:row-id :col-key :value :req}.
                      See `pomp.rad.datatable.query.sql/save-fn` for SQL implementation.

   Returns a Ring handler function that handles datatable requests via SSE."
  [{:keys [id columns query-fn data-url render-html-fn
           page-sizes selectable? skeleton-rows render-row render-header render-cell
           filter-operations save-fn]
    :or {page-sizes [10 25 100]
         selectable? false
         skeleton-rows 10}}]
  (fn [req]
    (let [query-params (:query-params req)
          raw-signals (get-signals req id)
          action (get query-params "action")]
      ;; Handle save action
      (if (and (= action "save") save-fn)
        (let [cell-edit (extract-cell-edit raw-signals)]
          (when cell-edit
            (save-fn (assoc cell-edit :req req)))
          ;; Respond with SSE to update the cell display and clear edit state
          (->sse-response req
                          {on-open
                           (fn [sse]
                             ;; Update the cell's display with the new value
                             (when cell-edit
                               (let [{:keys [row-id col-key value]} cell-edit
                                     element-id (str "cell-" id "-" row-id "-" (name col-key))
                                     col (some #(when (= (:key %) col-key) %) columns)
                                     col-type (:type col)]
                                 (if (= col-type :boolean)
                                   ;; Boolean: patch the checkbox input with updated checked state
                                   (let [bool-value (cond
                                                      (boolean? value) value
                                                      (= "true" value) true
                                                      :else false)]
                                     (d*/patch-elements! sse (render-html-fn
                                                              [:input.toggle.toggle-xs.toggle-success
                                                               {:id element-id
                                                                :type "checkbox"
                                                                :checked bool-value
                                                                ;; Re-attach the change handler
                                                                :data-on:change (str "evt.stopPropagation(); "
                                                                                     "$datatable." id ".editing = {rowId: '" row-id "', colKey: '" (name col-key) "'}; "
                                                                                     "$datatable." id ".cells." row-id "." (name col-key) " = evt.target.checked; "
                                                                                     "@post('" data-url "?action=save')")}])))
                                   ;; Other types: patch the span with new value
                                   (let [display-content (render-cell-display value col-type)]
                                     (d*/patch-elements! sse (render-html-fn
                                                              [:span.flex-1 {:id element-id :data-value (str value)} display-content]))))))
                             (d*/close-sse! sse))}))

        ;; Normal query/render flow
        (let [current-signals (-> raw-signals
                                  (assoc :group-by (mapv keyword (:groupBy raw-signals))))
              columns-state (:columns current-signals)
              initial-load? (empty? raw-signals)
              column-order (column-state/next-state (:columnOrder current-signals) columns query-params)
              ordered-cols (column-state/reorder columns column-order)
              visible-cols (column-state/filter-visible ordered-cols columns-state)
              {:keys [signals rows total-rows]} (state/query current-signals query-params req query-fn)
              group-by (:group-by signals)
              groups (when (seq group-by) (group-state/group-rows rows group-by))
              filters-patch (filter-state/compute-patch (:filters current-signals) (:filters signals))]
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
                                                     {:datatable {(keyword id) {:sort (:sort signals)
                                                                                :page (:page signals)
                                                                                :filters filters-patch
                                                                                :groupBy (mapv name group-by)
                                                                                :openFilter ""
                                                                                :columnOrder column-order
                                                                                :dragging nil
                                                                                :dragOver nil}}}))
                             (d*/patch-elements! sse (render-html-fn (table/render {:id id
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
                                                                                    :toolbar (columns-menu/render {:cols ordered-cols
                                                                                                                   :columns-state columns-state
                                                                                                                   :table-id id
                                                                                                                   :data-url data-url})})))
                             (d*/close-sse! sse))}))))))
