(ns pomp.rad.datatable.state.column)

(defn default-order [cols]
  (mapv (comp name :key) cols))

(defn move [column-order move-col target-col]
  (if (or (nil? move-col) (nil? target-col) (= move-col target-col))
    column-order
    (let [move-idx (.indexOf column-order move-col)
          target-idx (.indexOf column-order target-col)]
      (if (or (neg? move-idx) (neg? target-idx))
        column-order
        (let [without-moved (vec (remove #(= % move-col) column-order))
              new-target-idx (.indexOf without-moved target-col)
              ;; If moving right (was before target), insert after target
              ;; If moving left (was after target), insert before target
              insert-idx (if (< move-idx target-idx)
                           (inc new-target-idx)
                           new-target-idx)]
          (vec (concat (subvec without-moved 0 insert-idx)
                       [move-col]
                       (subvec without-moved insert-idx))))))))

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
