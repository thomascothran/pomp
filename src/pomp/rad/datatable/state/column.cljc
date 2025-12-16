(ns pomp.rad.datatable.state.column)

(defn default-order [cols]
  (mapv (comp name :key) cols))

(defn move [column-order move-col target-col]
  (if (or (nil? move-col) (nil? target-col) (= move-col target-col))
    column-order
    (let [without-moved (vec (remove #(= % move-col) column-order))
          target-idx (.indexOf without-moved target-col)]
      (if (neg? target-idx)
        column-order
        (vec (concat (subvec without-moved 0 target-idx)
                     [move-col]
                     (subvec without-moved target-idx)))))))

(defn next-state [current-order cols query-params]
  (let [move-col (get query-params "moveCol")
        before-col (get query-params "beforeCol")
        order (or current-order (default-order cols))]
    (if (and move-col before-col)
      (move order move-col before-col)
      order)))

(defn reorder [cols column-order]
  (if (empty? column-order)
    cols
    (let [col-map (into {} (map (fn [c] [(name (:key c)) c]) cols))]
      (vec (keep #(get col-map %) column-order)))))

(defn filter-visible [cols columns-state]
  (filter (fn [{:keys [key]}]
            (get-in columns-state [key :visible] true))
          cols))
