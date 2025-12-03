(ns pomp.rad.datatable.util
  (:require [clojure.string :as str]))

(defn sort-data [rows sort-spec]
  (if (empty? sort-spec)
    rows
    (let [{:keys [column direction]} (first sort-spec)
          col-key (keyword column)
          comparator (if (= direction "asc")
                       compare
                       #(compare %2 %1))]
      (sort-by #(get % col-key) comparator rows))))

(defn apply-text-filter [rows col-key filter-op filter-value]
  (let [search-term (str/lower-case (or filter-value ""))
        get-cell-val #(str/lower-case (str (get % col-key)))]
    (case filter-op
      "contains" (if (str/blank? filter-value)
                   rows
                   (filter #(str/includes? (get-cell-val %) search-term) rows))
      "not-contains" (if (str/blank? filter-value)
                       rows
                       (remove #(str/includes? (get-cell-val %) search-term) rows))
      "equals" (filter #(= (get-cell-val %) search-term) rows)
      "not-equals" (remove #(= (get-cell-val %) search-term) rows)
      "starts-with" (filter #(str/starts-with? (get-cell-val %) search-term) rows)
      "ends-with" (filter #(str/ends-with? (get-cell-val %) search-term) rows)
      "is-empty" (filter #(str/blank? (str (get % col-key))) rows)
      rows)))

(defn apply-filters [rows filters]
  (reduce (fn [filtered-rows [col-key filter-spec]]
            (case (:type filter-spec)
              "text" (apply-text-filter filtered-rows col-key (:op filter-spec) (:value filter-spec))
              filtered-rows))
          rows
          filters))

(defn paginate-data [rows page-size page-current]
  (->> rows
       (drop (* page-current page-size))
       (take page-size)))

(defn total-pages [total-rows page-size]
  #?(:clj (int (Math/ceil (/ total-rows page-size)))
     :cljs (js/Math.ceil (/ total-rows page-size))))

(defn has-active-filters? [filters]
  (seq filters))
