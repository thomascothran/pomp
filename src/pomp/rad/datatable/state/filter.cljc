(ns pomp.rad.datatable.state.filter
  (:require [clojure.string :as str]
            [clojure.set :as set]))

(defn next-state
  [signals query-params]
  (let [{:keys [filter-col filter-op filter-val clear-filters?]}
        {:filter-col (get query-params "filterCol")
         :filter-op (get query-params "filterOp")
         :filter-val (get query-params "filterVal")
         :clear-filters? (some? (get query-params "clearFilters"))}]
    (cond
      clear-filters? {}
      (nil? filter-col) signals
      (and (str/blank? filter-val) (not= filter-op "is-empty")) (dissoc signals (keyword filter-col))
      :else (assoc signals (keyword filter-col) {:type "text" :op (or filter-op "contains") :value filter-val}))))

(defn compute-patch
  [old-signals new-signals]
  (let [removed-keys (set/difference (set (keys old-signals)) (set (keys new-signals)))]
    (merge new-signals (into {} (map (fn [k] [k nil]) removed-keys)))))
