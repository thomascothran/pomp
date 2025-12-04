(ns pomp.rad.datatable.sort)

(defn next-state
  [signals query-params]
  (let [clicked-column (get query-params "clicked")]
    (if (nil? clicked-column)
      signals
      (let [current (first signals)
            current-col (:column current)
            current-dir (:direction current)]
        (cond
          (not= current-col clicked-column)
          [{:column clicked-column :direction "asc"}]

          (= current-dir "asc")
          [{:column clicked-column :direction "desc"}]

          :else
          [])))))
