(ns pomp.rad.datatable.state.filter
  (:require [clojure.string :as str]
            [clojure.set :as set]))

(defn next-state
  "Computes the next filter state from current signals and query params.
   
   Filter structure: {:col-key [{:type \"text\" :op \"contains\" :value \"x\"} ...]}
   Each column maps to a VECTOR of filter specs, enabling multiple filters per column.
   
   Query params:
   - filterCol: column key to filter on
   - filterOp: filter operation (default: \"contains\")
   - filterVal: filter value
   - filterIdx: index of filter to modify/remove (for removeFilter)
   - removeFilter: when \"1\", removes filter at filterIdx
   - clearFilters: when present, clears all filters
   - clearColFilters: when \"1\" with filterCol, clears all filters for that column"
  [signals query-params]
  (let [{:keys [filter-col filter-op filter-val filter-idx remove-filter?
                clear-filters? clear-col-filters?]}
        {:filter-col (get query-params "filterCol")
         :filter-op (get query-params "filterOp")
         :filter-val (get query-params "filterVal")
         :filter-idx (some-> (get query-params "filterIdx") parse-long)
         :remove-filter? (= "1" (get query-params "removeFilter"))
         :clear-filters? (some? (get query-params "clearFilters"))
         :clear-col-filters? (= "1" (get query-params "clearColFilters"))}
        col-key (some-> filter-col keyword)]
    (cond
      ;; Clear all filters
      clear-filters? {}

      ;; No column specified - return unchanged
      (nil? col-key) signals

      ;; Clear all filters for a specific column
      clear-col-filters? (dissoc signals col-key)

      ;; Remove filter at specific index
      remove-filter?
      (let [current-filters (get signals col-key [])
            updated-filters (vec (concat (subvec current-filters 0 filter-idx)
                                         (subvec current-filters (inc filter-idx))))]
        (if (empty? updated-filters)
          (dissoc signals col-key)
          (assoc signals col-key updated-filters)))

      ;; Blank value (except for is-empty) - no new filter added
      (and (str/blank? filter-val) (not= filter-op "is-empty"))
      signals

      ;; Add new filter to the column's filter array
      :else
      (let [new-filter {:type "text" :op (or filter-op "contains") :value filter-val}
            current-filters (get signals col-key [])]
        (assoc signals col-key (conj current-filters new-filter))))))

(defn compute-patch
  [old-signals new-signals]
  (let [removed-keys (set/difference (set (keys old-signals)) (set (keys new-signals)))]
    (merge new-signals (into {} (map (fn [k] [k nil]) removed-keys)))))
