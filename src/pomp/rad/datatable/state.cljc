(ns pomp.rad.datatable.state
  (:require [clojure.string :as str]
            [pomp.rad.datatable.util :as util]))

(defn next-sort-state [current-sort clicked-column]
  (if (nil? clicked-column)
    current-sort
    (let [current (first current-sort)
          current-col (:column current)
          current-dir (:direction current)]
      (cond
        (not= current-col clicked-column)
        [{:column clicked-column :direction "asc"}]

        (= current-dir "asc")
        [{:column clicked-column :direction "desc"}]

        :else
        []))))

(defn next-page-state [current-page current-size total-rows page-action new-size]
  (let [size (if new-size
               #?(:clj (parse-long new-size)
                  :cljs (js/parseInt new-size 10))
               current-size)
        total-pgs (util/total-pages total-rows size)
        page (cond
               new-size 0
               (= page-action "first") 0
               (= page-action "prev") (max 0 (dec current-page))
               (= page-action "next") (min (dec total-pgs) (inc current-page))
               (= page-action "last") (dec total-pgs)
               :else (min current-page (max 0 (dec total-pgs))))]
    {:size size :current page}))

(defn update-filters [current-filters filter-col filter-op filter-val clear-filters?]
  (cond
    clear-filters? {}
    (nil? filter-col) current-filters
    (and (str/blank? filter-val) (not= filter-op "is-empty")) (dissoc current-filters (keyword filter-col))
    :else (assoc current-filters (keyword filter-col) {:type "text" :op (or filter-op "contains") :value filter-val})))
