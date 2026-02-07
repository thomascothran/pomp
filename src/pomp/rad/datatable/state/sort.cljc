(ns pomp.rad.datatable.state.sort)

(defn next-state
  [signals query-params group-by]
  (let [clicked-column (get query-params "clicked")
        sort-col (get query-params "sortCol")
        sort-dir (get query-params "sortDir")
        grouped-col-name (when (seq group-by) (name (first group-by)))
        sort-allowed? (fn [col-name]
                        (or (nil? grouped-col-name)
                            (= grouped-col-name col-name)))]
    (cond
      (and sort-col sort-dir)
      (if (sort-allowed? sort-col)
        [{:column sort-col :direction sort-dir}]
        signals)

      (nil? clicked-column)
      signals

      (not (sort-allowed? clicked-column))
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
