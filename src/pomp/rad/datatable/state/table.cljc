(ns pomp.rad.datatable.state.table
  (:require [clojure.string :as str]
            [pomp.rad.datatable.state.filter :as filter-state]
            [pomp.rad.datatable.state.sort :as sort-state]
            [pomp.rad.datatable.state.group :as group-state]
            [pomp.rad.datatable.state.page :as page-state]))

(defn- normalize-search-string
  [search]
  (let [trimmed (some-> search str/trim)]
    (if (or (str/blank? trimmed)
            (< (count trimmed) 2))
      ""
      trimmed)))

(defn next-state
  [signals query-params]
  (let [new-group-by (group-state/next-state (:group-by signals) query-params)
        new-filters (or (filter-state/next-state (:filters signals) query-params) {})
        new-sort (or (sort-state/next-state (:sort signals) query-params new-group-by) [])
        new-page (page-state/next-state (:page signals) query-params)
        new-search-string (normalize-search-string (:globalTableSearch signals))]
    {:filters new-filters
     :sort new-sort
     :page new-page
     :group-by new-group-by
     :search-string new-search-string}))

(defn query-rows
  [signals query-params request rows-fn]
  (let [new-signals (next-state signals query-params)
        {:keys [rows page]} (rows-fn new-signals request)]
    {:signals (assoc new-signals :page page)
     :rows rows}))

(defn query-count
  [signals request count-fn]
  (when count-fn
    (let [result (count-fn signals request)]
      (if (map? result)
        (:total-rows result)
        result))))
