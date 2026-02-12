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
        grouping-changed? (not= new-group-by (:group-by signals))
        new-filters (if grouping-changed?
                      {}
                      (or (filter-state/next-state (:filters signals) query-params) {}))
        new-sort (or (sort-state/next-state (:sort signals) query-params new-group-by) [])
        new-page (page-state/next-state (:page signals) query-params)
        new-search-string (normalize-search-string (:globalTableSearch signals))]
    {:filters new-filters
     :sort new-sort
     :page new-page
     :group-by new-group-by
     :search-string new-search-string}))

(defn query
  [signals query-params request query-fn]
  (let [new-signals (next-state signals query-params)
        {:keys [rows total-rows page]} (query-fn new-signals request)]
    {:signals (assoc new-signals :page page)
     :rows rows
     :total-rows total-rows}))
