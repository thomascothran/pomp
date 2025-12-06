(ns pomp.rad.datatable.column)

(defn default-order
  "Returns the default column order based on column definitions."
  [cols]
  (mapv #(name (:key %)) cols))

(defn move
  "Moves a column to a new position. Handles drag direction automatically:
   - Dragging left (source > target): inserts before target
   - Dragging right (source < target): inserts after target"
  [column-order move-col target-col]
  (let [source-idx (.indexOf column-order move-col)
        target-idx (.indexOf column-order target-col)]
    (if (or (neg? source-idx) (neg? target-idx) (= source-idx target-idx))
      column-order
      (let [dragging-right? (< source-idx target-idx)
            without-moved (vec (remove #(= % move-col) column-order))
            insert-idx (let [idx (.indexOf without-moved target-col)]
                         (if dragging-right?
                           (inc idx)
                           idx))]
        (into (conj (subvec without-moved 0 insert-idx) move-col)
              (subvec without-moved insert-idx))))))

(defn next-state
  "Computes the next column order state based on query params.
   Returns the new column order (vector of column key strings)."
  [current-order cols query-params]
  (let [order (or current-order (default-order cols))
        move-col (get query-params "moveCol")
        target-col (get query-params "beforeCol")]
    (if (and move-col target-col)
      (move order move-col target-col)
      order)))

(defn reorder
  "Reorders column definitions based on the given column order."
  [cols column-order]
  (if (seq column-order)
    (let [col-map (into {} (map (fn [c] [(name (:key c)) c]) cols))]
      (mapv #(get col-map %) column-order))
    cols))

(defn filter-visible
  "Filters columns to only include visible ones based on columns state.
   columns-state is a map like {:name {:visible true}, :century {:visible false}}
   Columns default to visible if not specified in state."
  [cols columns-state]
  (filter (fn [col]
            (get-in columns-state [(keyword (name (:key col))) :visible] true))
          cols))
