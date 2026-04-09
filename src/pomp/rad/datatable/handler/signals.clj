(ns pomp.rad.datatable.handler.signals
  (:require [clojure.data.json :as json]))

(defn malformed-filters-query-param?
  [req]
  (when-let [filters-json (get-in req [:query-params "filters"])]
    (try
      (not (map? (json/read-str filters-json {:key-fn keyword})))
      (catch Exception _
        true))))

(defn- seed-filters-from-query-param
  [req]
  (when-let [filters-json (get-in req [:query-params "filters"])]
    (try
      (let [filters (json/read-str filters-json {:key-fn keyword})]
        (when (map? filters)
          {:filters filters}))
      (catch Exception _
        nil))))

(defn effective-signals
  [{:keys [raw-signals initial-signals-fn req]}]
  (let [initial-load? (empty? raw-signals)
        seeded-signals (when initial-load?
                         (merge (when initial-signals-fn
                                  (initial-signals-fn req))
                                (seed-filters-from-query-param req)))]
    (if (map? seeded-signals)
      (merge seeded-signals raw-signals)
      raw-signals)))

(defn normalize-group-by
  [signals]
  (assoc signals :group-by (mapv keyword (:groupBy signals))))

(defn current-signals
  [opts]
  (-> (effective-signals opts)
      normalize-group-by))
