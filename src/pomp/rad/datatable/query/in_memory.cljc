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
