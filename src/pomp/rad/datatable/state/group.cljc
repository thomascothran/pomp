(ns pomp.rad.datatable.state.group)

(defn next-state
  [group-by query-params]
  (let [new-group (get query-params "groupBy")
        ungroup? (some? (get query-params "ungroup"))]
    (cond
      ungroup? []
      new-group [(keyword new-group)]
      :else (or group-by []))))

(defn group-rows
  [rows group-by-cols]
  (if (empty? group-by-cols)
    nil
    (let [group-key (first group-by-cols)]
      (let [{:keys [order grouped]} (reduce (fn [{:keys [order grouped]} row]
                                              (let [group-value (get row group-key)]
                                                (if (contains? grouped group-value)
                                                  {:order order
                                                   :grouped (update grouped group-value conj row)}
                                                  {:order (conj order group-value)
                                                   :grouped (assoc grouped group-value [row])})))
                                            {:order [] :grouped {}}
                                            rows)]
        (map (fn [group-value]
               (let [group-rows (get grouped group-value)]
                 {:group-key group-key
                  :group-value group-value
                  :rows group-rows
                  :row-ids (set (map :id group-rows))
                  :count (count group-rows)}))
             order)))))
