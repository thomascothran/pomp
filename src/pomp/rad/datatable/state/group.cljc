(ns pomp.rad.datatable.state.group)

(defn- row-id->set
  [rows]
  (set (map :id rows)))

(defn- grouped-rows
  [rows group-by-cols]
  (when (seq group-by-cols)
    (let [group-key (first group-by-cols)
          rest-group-by (rest group-by-cols)
          {:keys [order grouped]} (reduce (fn [{:keys [order grouped]} row]
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
               (if (seq rest-group-by)
                 (let [next-groups (grouped-rows group-rows rest-group-by)
                       row-ids (reduce (fn [acc child]
                                         (into acc (:row-ids child)))
                                       #{}
                                       next-groups)
                       count (reduce (fn [acc child]
                                      (+ acc (:count child)))
                                    0
                                    next-groups)]
                   {:group-key group-key
                    :group-value group-value
                    :rows next-groups
                    :row-ids row-ids
                    :count count})
                 {:group-key group-key
                  :group-value group-value
                  :rows group-rows
                  :row-ids (row-id->set group-rows)
                  :count (count group-rows)})))
           order))))

(defn next-state
  [group-by query-params]
  (let [clear-groups? (some? (get query-params "clearGroups"))
        ungroup? (some? (get query-params "ungroup"))
        new-group (get query-params "groupBy")]
    (cond
      clear-groups? []
      ungroup? (into [] (butlast (or group-by [])))
      new-group (conj (vec (or group-by [])) (keyword new-group))
      :else (or group-by []))))

(defn group-rows
  [rows group-by-cols]
  (when (seq group-by-cols)
    (grouped-rows rows group-by-cols)))
