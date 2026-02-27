(ns pomp.datatable.handler.signals)

(defn effective-signals
  [{:keys [raw-signals initial-signals-fn req]}]
  (let [initial-load? (empty? raw-signals)
        seeded-signals (when (and initial-load? initial-signals-fn)
                         (initial-signals-fn req))]
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
