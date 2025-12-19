(ns pomp.rad.datatable.query.in-memory
  (:require [clojure.string :as str]))

(defn- apply-text-filter [rows col-key filter-op filter-value]
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
      "is-not-empty" (remove #(str/blank? (str (get % col-key))) rows)
      "is-any-of" (let [values (set (map str/lower-case filter-value))]
                    (filter #(contains? values (get-cell-val %)) rows))
      rows)))

(defn apply-filters
  "Applies filters to rows. Filter structure: {:col-key [{:type \"text\" :op \"contains\" :value \"x\"} ...]}
   Multiple filters on the same column use AND logic.
   Multiple filters across columns also use AND logic."
  [rows filters]
  (reduce (fn [filtered-rows [col-key filter-specs]]
            ;; filter-specs is now a vector of filters for this column
            ;; Apply all filters for this column with AND logic
            (reduce (fn [rows filter-spec]
                      (case (:type filter-spec)
                        "text" (apply-text-filter rows col-key (:op filter-spec) (:value filter-spec))
                        rows))
                    filtered-rows
                    filter-specs))
          rows
          filters))

(defn sort-data [rows sort-spec]
  (if (empty? sort-spec)
    rows
    (let [{:keys [column direction]} (first sort-spec)
          col-key (keyword column)
          comparator (if (= direction "asc")
                       compare
                       #(compare %2 %1))]
      (sort-by #(get % col-key) comparator rows))))

(defn paginate-data [rows page]
  (let [{:keys [size current]} page
        total-rows (count rows)
        total-pgs (if (zero? total-rows) 1 (int (Math/ceil (/ total-rows size))))
        clamped-current (cond
                          (nil? current) (max 0 (dec total-pgs))
                          (>= current total-pgs) (max 0 (dec total-pgs))
                          :else current)]
    {:rows (->> rows
                (drop (* clamped-current size))
                (take size))
     :current clamped-current}))

(defn query-fn
  [rows]
  (fn [{:keys [filters sort page]} _request]
    (let [filtered (apply-filters rows filters)
          sorted (sort-data filtered sort)
          {:keys [rows current]} (paginate-data sorted page)]
      {:rows rows
       :total-rows (count filtered)
       :page {:size (:size page) :current current}})))
