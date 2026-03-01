(ns pomp.rad.datatable.handler.query-render
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [pomp.rad.datatable.handler.signals :as handler-signals]
            [pomp.rad.datatable.state.column :as column-state]
            [pomp.rad.datatable.state.filter :as filter-state]
            [pomp.rad.datatable.state.group :as group-state]
            [pomp.rad.datatable.state.table :as state]
            [pomp.rad.datatable.ui.columns-menu :as columns-menu]
            [pomp.rad.datatable.ui.export-button :as export-button]
            [pomp.rad.datatable.ui.table :as table]
            [starfederation.datastar.clojure.adapter.ring :refer [->sse-response on-open]]
            [starfederation.datastar.clojure.api :as d*]))

(def ^:private datatable-helpers-script
  "JavaScript datatable helpers loaded from classpath."
  (slurp (io/resource "public/pomp/js/datatable-helpers.js")))

(def ^:private datatable-selection-script
  "JavaScript for datatable cell selection loaded from classpath."
  (slurp (io/resource "public/pomp/js/datatable-selection.js")))

(def ^:private datatable-export-script
  "JavaScript for datatable export loaded from classpath."
  (slurp (io/resource "public/pomp/js/datatable-export.js")))

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

(defn handle-query-render-action!
  [{:keys [req id columns rows-fn count-fn table-search-query data-url render-html-fn
           page-sizes selectable? skeleton-rows render-row render-header render-cell
           filter-operations render-table-search render-export raw-signals
           query-params initial-signals-fn export-available?]}]
  (let [initial-load? (empty? raw-signals)
        current-signals (handler-signals/current-signals {:raw-signals raw-signals
                                                          :initial-signals-fn initial-signals-fn
                                                          :req req})
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
                              (or (= (get query-params "action") "global-search")
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
                          (d*/execute-script! sse datatable-helpers-script)
                          (d*/execute-script! sse datatable-selection-script)
                          (when export-available?
                            (d*/execute-script! sse datatable-export-script)))
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
                        (d*/close-sse! sse))})))
