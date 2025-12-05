(ns pomp.rad.datatable.group)

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
      (->> rows
           (group-by #(get % group-key))
           (map (fn [[group-value group-rows]]
                  {:group-key group-key
                   :group-value group-value
                   :rows group-rows
                   :row-ids (set (map :id group-rows))
                   :count (count group-rows)}))
           (sort-by :group-value)))))
