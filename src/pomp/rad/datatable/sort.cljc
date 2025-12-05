(ns pomp.rad.datatable.sort)

(defn next-state
  [signals query-params]
  (let [clicked-column (get query-params "clicked")
        sort-col (get query-params "sortCol")
        sort-dir (get query-params "sortDir")]
    (cond
      (and sort-col sort-dir)
      [{:column sort-col :direction sort-dir}]

      (nil? clicked-column)
      signals

      :else
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
