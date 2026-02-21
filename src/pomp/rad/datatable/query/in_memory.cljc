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

(defn- apply-boolean-filter
  "Filters rows based on boolean column values.
   filter-value is a string: \"true\" or \"false\"."
  [rows col-key filter-op filter-value]
  (let [target-bool (= filter-value "true")]
    (case filter-op
      "is" (filter #(= (get % col-key) target-bool) rows)
      "is-not" (remove #(= (get % col-key) target-bool) rows)
      "is-empty" (filter #(nil? (get % col-key)) rows)
      "is-not-empty" (remove #(nil? (get % col-key)) rows)
      rows)))

(defn- apply-date-filter
  "Filters rows based on date column values.
   Dates are compared as strings (ISO 8601 format: YYYY-MM-DD)."
  [rows col-key filter-op filter-value]
  (case filter-op
    "is" (filter #(= (get % col-key) filter-value) rows)
    "is-not" (remove #(= (get % col-key) filter-value) rows)
    "after" (filter #(when-let [v (get % col-key)]
                       (pos? (compare v filter-value))) rows)
    "on-or-after" (filter #(when-let [v (get % col-key)]
                             (>= (compare v filter-value) 0)) rows)
    "before" (filter #(when-let [v (get % col-key)]
                        (neg? (compare v filter-value))) rows)
    "on-or-before" (filter #(when-let [v (get % col-key)]
                              (<= (compare v filter-value) 0)) rows)
    "is-empty" (filter #(nil? (get % col-key)) rows)
    "is-not-empty" (remove #(nil? (get % col-key)) rows)
    rows))

(defn- apply-enum-filter
  "Filters rows based on enum (string) column values.
   Enum filtering is case-sensitive (unlike text filtering)."
  [rows col-key filter-op filter-value]
  (case filter-op
    "is" (filter #(= (get % col-key) filter-value) rows)
    "is-not" (remove #(= (get % col-key) filter-value) rows)
    "is-any-of" (let [values (set filter-value)]
                  (filter #(contains? values (get % col-key)) rows))
    "is-empty" (filter #(nil? (get % col-key)) rows)
    "is-not-empty" (remove #(nil? (get % col-key)) rows)
    rows))

(defn- parse-number
  "Parses a string to a number. Returns nil if parsing fails."
  [s]
  (when (and s (not (str/blank? s)))
    (try
      #?(:clj (Double/parseDouble s)
         :cljs (let [n (js/parseFloat s)]
                 (when-not (js/isNaN n) n)))
      (catch #?(:clj Exception :cljs :default) _ nil))))

(defn- apply-number-filter
  "Filters rows based on numeric column values.
   filter-value is a string that gets parsed to a number."
  [rows col-key filter-op filter-value]
  (let [target-num (parse-number filter-value)]
    (case filter-op
      "equals" (filter #(when-let [v (get % col-key)]
                          (== v target-num)) rows)
      "not-equals" (remove #(when-let [v (get % col-key)]
                              (== v target-num)) rows)
      "greater-than" (filter #(when-let [v (get % col-key)]
                                (> v target-num)) rows)
      "greater-than-or-equal" (filter #(when-let [v (get % col-key)]
                                         (>= v target-num)) rows)
      "less-than" (filter #(when-let [v (get % col-key)]
                             (< v target-num)) rows)
      "less-than-or-equal" (filter #(when-let [v (get % col-key)]
                                      (<= v target-num)) rows)
      "is-empty" (filter #(nil? (get % col-key)) rows)
      "is-not-empty" (remove #(nil? (get % col-key)) rows)
      rows)))

(defn apply-filters
  "Applies filters to rows. Filter structure: {:col-key [{:type \"string\" :op \"contains\" :value \"x\"} ...]}
   Multiple filters on the same column use AND logic.
   Multiple filters across columns also use AND logic."
  [rows filters]
  (reduce (fn [filtered-rows [col-key filter-specs]]
            ;; filter-specs is now a vector of filters for this column
            ;; Apply all filters for this column with AND logic
            (reduce (fn [rows filter-spec]
                      (let [{:keys [type op value]} filter-spec]
                        (case type
                          ("string" "text") (apply-text-filter rows col-key op value)
                          "number" (apply-number-filter rows col-key op value)
                          "boolean" (apply-boolean-filter rows col-key op value)
                          "date" (apply-date-filter rows col-key op value)
                          "enum" (apply-enum-filter rows col-key op value)
                          rows)))
                    filtered-rows
                    filter-specs))
           rows
           filters))

(defn- apply-global-search
  [rows columns search-string]
  (let [trimmed-search (some-> search-string str/trim)]
    (if (or (str/blank? trimmed-search)
            (< (count trimmed-search) 2))
      rows
      (let [search-term (str/lower-case trimmed-search)
            searchable-cols (->> columns
                                 (filter :global-search?)
                                 (map :key))]
        (if (empty? searchable-cols)
          rows
          (filter (fn [row]
                    (some (fn [col-key]
                            (str/includes? (str/lower-case (str (or (get row col-key) "")))
                                           search-term))
                          searchable-cols))
                  rows))))))

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

(defn- group-sort-direction
  [group-key sort-spec]
  (let [{:keys [column direction]} (first sort-spec)]
    (when (= (keyword column) group-key)
      (or direction "asc"))))

(defn- sort-rows-by-first-group
  [rows group-by sort-spec]
  (let [group-key (first group-by)
        sort-direction (or (group-sort-direction group-key sort-spec) "asc")
        comparator (if (= sort-direction "desc")
                     #(compare %2 %1)
                     compare)]
    (sort-by #(get % group-key) comparator rows)))

(defn- grouped-page
  [rows {:keys [group-by page] :as params}]
  (let [size (:size page)
        sorted-rows (sort-rows-by-first-group rows group-by (:sort params))
        total-rows (count sorted-rows)
        total-pgs (if (zero? total-rows) 1 (int (Math/ceil (/ total-rows size))))
        current (:current page)
        clamped-current (cond
                         (nil? current) (max 0 (dec total-pgs))
                         (>= current total-pgs) (max 0 (dec total-pgs))
                         :else current)]
    {:rows (->> sorted-rows
                (drop (* clamped-current size))
                (take size)
                vec)
     :total-rows total-rows
     :page {:size size :current clamped-current}}))

(defn- filtered-rows
  [rows {:keys [columns filters search-string]}]
  (-> rows
      (apply-global-search columns search-string)
      (apply-filters filters)))

(defn rows-fn
  [rows]
  (fn [{:keys [group-by page] :as params} _request]
    (let [filtered (filtered-rows rows params)
          size (:size page 10)
          requested-current (:current page)]
      (if (seq group-by)
        (let [{:keys [rows page]} (grouped-page filtered params)]
          {:rows rows
           :page page})
        (let [total-rows (count filtered)
              total-pgs (if (zero? total-rows) 1 (int (Math/ceil (/ total-rows size))))
              current (if (nil? requested-current)
                        (max 0 (dec total-pgs))
                        requested-current)
              sorted (sort-data filtered (:sort params))]
          {:rows (->> sorted
                      (drop (* current size))
                       (take size)
                       vec)
           :page {:size size :current current}})))))

(defn stream-rows-fn
  [rows]
  (fn [{:keys [query]} on-row! on-complete!]
    (let [filtered (filtered-rows rows query)
          sorted (sort-data filtered (:sort query))]
      (doseq [row sorted]
        (on-row! row))
      (on-complete! {:row-count (count sorted)}))))

(defn count-fn
  [rows]
  (fn [params _request]
    (let [filtered (filtered-rows rows params)]
      {:total-rows (count filtered)})))

(defn query-fn
  [rows]
  (fn [{:keys [columns filters page group-by search-string] :as params} _request]
    (let [globally-filtered (apply-global-search rows columns search-string)
          filtered (apply-filters globally-filtered filters)]
      (if (seq group-by)
        (grouped-page filtered params)
        (let [sorted (sort-data filtered (:sort params))
              {:keys [rows current]} (paginate-data sorted page)]
          {:rows rows
           :total-rows (count filtered)
           :page {:size (:size page) :current current}})))))
