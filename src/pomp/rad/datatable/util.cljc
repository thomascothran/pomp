(ns pomp.rad.datatable.util)

(defn total-pages [total-rows page-size]
  (when (some? total-rows)
    #?(:clj (int (Math/ceil (/ total-rows page-size)))
       :cljs (js/Math.ceil (/ total-rows page-size)))))

(defn has-active-filters? [filters]
  (seq filters))
