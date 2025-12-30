(ns pomp.rad.datatable.state.filter
  (:require [clojure.set :as set]))

(defn next-state
  "Computes the next filter state from current signals and query params.

   Filter structure: {:col-key [{:type \"string\" :op \"contains\" :value \"x\"} ...]}
   Each column maps to a VECTOR of filter specs, enabling multiple filters per column.

   With the signal-based architecture, filter state is managed by the frontend.
   The frontend updates signals directly when applying/clearing filters.
   The backend simply passes through the filter state from signals.

   Query params:
   - clearFilters: when present, clears all filters (for global \"clear all\" button)"
  [signals query-params]
  (if (some? (get query-params "clearFilters"))
    {}
    signals))

(defn compute-patch
  [old-signals new-signals]
  (let [removed-keys (set/difference (set (keys old-signals)) (set (keys new-signals)))]
    (merge new-signals (into {} (map (fn [k] [k nil]) removed-keys)))))
