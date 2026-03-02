(ns pomp.rad.datatable.state.filter
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(defn- decode-value [value]
  (try
    #?(:clj (java.net.URLDecoder/decode value "UTF-8")
       :cljs (js/decodeURIComponent value))
    (catch #?(:clj IllegalArgumentException :cljs :default) _
      value)))

(defn- normalize-is-any-of-value [value]
  (cond
    (nil? value) []
    (coll? value) (vec value)
    (string? value) (->> (str/split (decode-value value) #",")
                         (map str/trim)
                         (remove str/blank?)
                         vec)
    :else value))

(defn- normalize-filter-spec [filter-spec]
  (if (= "is-any-of" (:op filter-spec))
    (update filter-spec :value normalize-is-any-of-value)
    filter-spec))

(defn normalize-filters
  [signals]
  (into {}
        (map (fn [[column filters]]
               [column (mapv normalize-filter-spec filters)]))
        signals))

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
    (normalize-filters signals)))

(defn compute-patch
  [old-signals new-signals]
  (let [removed-keys (set/difference (set (keys old-signals)) (set (keys new-signals)))]
    (merge new-signals (into {} (map (fn [k] [k nil]) removed-keys)))))
