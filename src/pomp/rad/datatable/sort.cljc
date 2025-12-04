(ns pomp.rad.datatable.sort)

(defn sort-data [rows signals]
  (if (empty? signals)
    rows
    (let [{:keys [column direction]} (first signals)
          col-key (keyword column)
          comparator (if (= direction "asc")
                       compare
                       #(compare %2 %1))]
      (sort-by #(get % col-key) comparator rows))))

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
