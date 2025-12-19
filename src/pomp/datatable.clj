(ns pomp.datatable
  "Main entry point for the datatable component.

   Use `make-handler` to create a Ring handler for your datatable."
  (:require [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.adapter.ring :refer [->sse-response on-open]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [pomp.rad.datatable.core :as dt]
            [pomp.rad.datatable.state.column :as column-state]
            [pomp.rad.datatable.state.group :as group-state]
            [pomp.rad.datatable.state.filter :as filter-state]
            [pomp.rad.datatable.ui.columns-menu :as columns-menu]))

(def ^:private cell-select-script
  "JavaScript for cell selection functionality, loaded from classpath."
  (slurp (io/resource "public/pomp/js/datatable.js")))

(defn get-signals
  "Extracts datatable signals from a Ring request for a specific table.

   Returns the signals map for the given table id, or an empty map if not present."
  [req id]
  (if (get-in req [:headers "datastar-request"])
    (some-> (d*/get-signals req)
            (json/read-str {:key-fn keyword})
            :datatable
            (get (keyword id)))
    {}))

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

   Returns a Ring handler function that handles datatable requests via SSE."
  [{:keys [id columns query-fn data-url render-html-fn
           page-sizes selectable? skeleton-rows render-row render-header render-cell
           filter-operations]
    :or {page-sizes [10 25 100]
         selectable? false
         skeleton-rows 10}}]
  (fn [req]
    (let [query-params (:query-params req)
          raw-signals (get-signals req id)
          current-signals (-> raw-signals
                              (assoc :group-by (mapv keyword (:groupBy raw-signals))))
          columns-state (:columns current-signals)
          initial-load? (empty? raw-signals)
          column-order (column-state/next-state (:columnOrder current-signals) columns query-params)
          ordered-cols (column-state/reorder columns column-order)
          visible-cols (column-state/filter-visible ordered-cols columns-state)
          {:keys [signals rows total-rows]} (dt/query current-signals query-params req query-fn)
          group-by (:group-by signals)
          groups (when (seq group-by) (group-state/group-rows rows group-by))
          filters-patch (filter-state/compute-patch (:filters current-signals) (:filters signals))
          ;; Initialize expanded state for each group (all collapsed by default)
          expanded-signals (when (seq groups)
                             (into {} (map (fn [idx] [(keyword (str idx)) false]) groups)))]
      (->sse-response req
                      {on-open
                       (fn [sse]
                         (when initial-load?
                           (d*/patch-elements! sse (render-html-fn (dt/render-skeleton {:id id
                                                                                        :cols visible-cols
                                                                                        :n skeleton-rows
                                                                                        :selectable? selectable?})))
                           (d*/execute-script! sse cell-select-script))
                         (d*/patch-signals! sse (json/write-str
                                                 {:datatable {(keyword id) (cond-> {:sort (:sort signals)
                                                                                    :page (:page signals)
                                                                                    :filters filters-patch
                                                                                    :groupBy (mapv name group-by)
                                                                                    :openFilter ""
                                                                                    :columnOrder column-order
                                                                                    :dragging nil
                                                                                    :dragOver nil}
                                                                             expanded-signals (assoc :expanded expanded-signals))}}))
                         (d*/patch-elements! sse (render-html-fn (dt/render {:id id
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
                         (d*/close-sse! sse))}))))
