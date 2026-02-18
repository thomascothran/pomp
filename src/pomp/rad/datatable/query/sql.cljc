(ns pomp.rad.datatable.query.sql
  "SQL query generator for datatable.

   Generates ANSI-compliant SQL for filtering, sorting, and pagination.
   Designed for use with next.jdbc."
  (:require [clojure.string :as str]))

(defn col-name
  "Returns the SQL column name for a given column key.
   Uses column-map if provided, otherwise converts keyword to string."
  [config col-key]
  (or (get-in config [:column-map col-key])
      (name col-key)))

(defn- get-column-config
  "Returns the column config for a given column key from the columns vector."
  [config col-key]
  (some #(when (= (:key %) col-key) %) (:columns config)))

(defn- case-sensitive?
  "Determines if filtering should be case-sensitive for a column.
   Per-column setting overrides top-level setting.
   Default is false (case-insensitive)."
  [config col-key]
  (let [col-config (get-column-config config col-key)]
    (if (contains? col-config :case-sensitive?)
      (:case-sensitive? col-config)
      (:case-sensitive? config false))))

(defn- generate-string-filter
  "Generates SQL for string/text filter operations."
  [config col-key {:keys [op value]}]
  (let [col (col-name config col-key)
        case-sens? (case-sensitive? config col-key)
        col-expr (if case-sens? col (str "LOWER(" col ")"))
        val-expr (fn [v] (if case-sens? v (str/lower-case v)))]
    (case op
      ;; Pattern matching ops - require non-blank value
      "contains" (when-not (str/blank? value)
                   [col-expr "LIKE ?" (str "%" (val-expr value) "%")])
      "not-contains" (when-not (str/blank? value)
                       [col-expr "NOT LIKE ?" (str "%" (val-expr value) "%")])
      "starts-with" (when-not (str/blank? value)
                      [col-expr "LIKE ?" (str (val-expr value) "%")])
      "ends-with" (when-not (str/blank? value)
                    [col-expr "LIKE ?" (str "%" (val-expr value))])

      ;; Equality ops - require non-blank value
      "equals" (when-not (str/blank? value)
                 [col-expr "= ?" (val-expr value)])
      "not-equals" (when-not (str/blank? value)
                     [col-expr "<> ?" (val-expr value)])

      ;; Empty check ops - no value needed
      "is-empty" [(str "(" col " IS NULL OR " col " = '')")]
      "is-not-empty" [(str "(" col " IS NOT NULL AND " col " <> '')")]

      ;; Multi-value ops
      "is-any-of" (when (and (seq value) (every? some? value))
                    (let [placeholders (str/join ", " (repeat (count value) "?"))
                          vals (map val-expr value)]
                      (into [col-expr (str "IN (" placeholders ")")] vals)))

      nil)))

(defn- parse-number
  "Parses a string to a number. Returns nil if parsing fails."
  [s]
  (when (and s (not (str/blank? s)))
    (try
      #?(:clj (let [n (Double/parseDouble s)]
                ;; Return integer if it's a whole number
                (if (== n (long n))
                  (long n)
                  n))
         :cljs (let [n (js/parseFloat s)]
                 (when-not (js/isNaN n) n)))
      (catch #?(:clj Exception :cljs :default) _ nil))))

(defn- generate-number-filter
  "Generates SQL for number filter operations."
  [config col-key {:keys [op value]}]
  (let [col (col-name config col-key)]
    (case op
      ;; Empty checks - no value needed
      "is-empty" [(str col " IS NULL")]
      "is-not-empty" [(str col " IS NOT NULL")]

      ;; Comparison ops - require parsed number
      (when-let [num-val (parse-number value)]
        (case op
          "equals" [col "= ?" num-val]
          "not-equals" [col "<> ?" num-val]
          "greater-than" [col "> ?" num-val]
          "greater-than-or-equal" [col ">= ?" num-val]
          "less-than" [col "< ?" num-val]
          "less-than-or-equal" [col "<= ?" num-val]
          nil)))))

(defn- generate-boolean-filter
  "Generates SQL for boolean filter operations."
  [config col-key {:keys [op value]}]
  (let [col (col-name config col-key)
        bool-val (= value "true")]
    (case op
      "is" [col "= ?" bool-val]
      "is-not" [col "<> ?" bool-val]
      "is-empty" [(str col " IS NULL")]
      "is-not-empty" [(str col " IS NOT NULL")]
      nil)))

(defn- generate-date-filter
  "Generates SQL for date filter operations.
   Dates are compared as strings (ISO 8601 format: YYYY-MM-DD)."
  [config col-key {:keys [op value]}]
  (let [col (col-name config col-key)]
    (case op
      ;; Empty checks - no value needed
      "is-empty" [(str col " IS NULL")]
      "is-not-empty" [(str col " IS NOT NULL")]

      ;; Comparison ops - require non-blank value
      (when-not (str/blank? value)
        (case op
          "is" [col "= ?" value]
          "is-not" [col "<> ?" value]
          "after" [col "> ?" value]
          "on-or-after" [col ">= ?" value]
          "before" [col "< ?" value]
          "on-or-before" [col "<= ?" value]
          nil)))))

(defn- generate-enum-filter
  "Generates SQL for enum filter operations.
   Enum filtering is case-sensitive (unlike text filtering)."
  [config col-key {:keys [op value]}]
  (let [col (col-name config col-key)]
    (case op
      ;; Empty checks - no value needed
      "is-empty" [(str col " IS NULL")]
      "is-not-empty" [(str col " IS NOT NULL")]

      ;; Multi-value op
      "is-any-of" (when (seq value)
                    (let [placeholders (str/join ", " (repeat (count value) "?"))]
                      (into [col (str "IN (" placeholders ")")] value)))

      ;; Equality ops - require non-blank value
      (when-not (str/blank? value)
        (case op
          "is" [col "= ?" value]
          "is-not" [col "<> ?" value]
          nil)))))

(defn generate-filter-clause
  "Generates a WHERE clause fragment for a single filter.
   Returns [sql-string & params] or nil if filter should be skipped."
  [config col-key filter-spec]
  (let [{:keys [type]} filter-spec]
    (when-let [result (case type
                        ("string" "text") (generate-string-filter config col-key filter-spec)
                        "number" (generate-number-filter config col-key filter-spec)
                        "boolean" (generate-boolean-filter config col-key filter-spec)
                        "date" (generate-date-filter config col-key filter-spec)
                        "enum" (generate-enum-filter config col-key filter-spec)
                        nil)]
      (let [[col-expr op-sql & params] result]
        (if op-sql
          (into [(str col-expr " " op-sql)] params)
          ;; For ops like is-empty where col-expr is the complete clause
          [col-expr])))))

(defn generate-where-clause
  "Generates a complete WHERE clause from filters.
   Returns [sql-string & params] or nil if no filters."
  [config filters]
  (when (seq filters)
    (let [clauses (for [[col-key filter-specs] filters
                        filter-spec filter-specs
                        :let [clause (generate-filter-clause config col-key filter-spec)]
                        :when clause]
                    clause)]
      (when (seq clauses)
        (let [sql-parts (map first clauses)
              params (mapcat rest clauses)]
          (into [(str "WHERE " (str/join " AND " sql-parts))] params))))))

(defn- generate-global-search-clause
  [config columns search-string]
  (let [trimmed-search (some-> search-string str/trim)
        searchable-cols (->> (or columns (:columns config))
                             (filter :global-search?)
                             (map :key))]
    (when (and (not (str/blank? trimmed-search))
               (>= (count trimmed-search) 2)
               (seq searchable-cols))
      (let [search-term (str/lower-case trimmed-search)
            sql-parts (map #(str "LOWER(" (col-name config %) ") LIKE ?") searchable-cols)
            params (map (fn [_] (str "%" search-term "%")) searchable-cols)]
        (into [(str "(" (str/join " OR " sql-parts) ")")] params)))))

(defn- combine-where-clauses
  [filter-clause global-search-clause]
  (let [drop-where-prefix (fn [s] (subs s (count "WHERE ")))]
    (cond
      (and filter-clause global-search-clause)
      (into [(str "WHERE "
                  (drop-where-prefix (first filter-clause))
                  " AND "
                  (first global-search-clause))]
            (concat (rest filter-clause) (rest global-search-clause)))

      filter-clause filter-clause

      global-search-clause
      (into [(str "WHERE " (first global-search-clause))]
            (rest global-search-clause))

      :else nil)))

(defn generate-order-clause
  "Generates an ORDER BY clause from sort spec.
   Returns a string or nil if no sort."
  [config sort-spec]
  (when (seq sort-spec)
    (let [{:keys [column direction]} (first sort-spec)
          col (col-name config (keyword column))
          dir (str/upper-case (or direction "asc"))]
      (str "ORDER BY " col " " dir))))

(defn- group-sort-direction
  [group-key sort-spec]
  (let [{:keys [column direction]} (first sort-spec)]
    (when (= (keyword column) group-key)
      (or direction "asc"))))

(defn- first-group-order-sort
  [group-by sort-spec]
  (when (seq sort-spec)
    (let [group-key (first group-by)
          direction (or (group-sort-direction group-key sort-spec) "asc")]
      [{:column (name group-key) :direction direction}])))

(defn generate-limit-clause
  "Generates a LIMIT/OFFSET clause from page spec.
   Returns [sql-string limit offset] or nil if no pagination."
  [page-spec]
  (when-let [{:keys [size current]} page-spec]
    (when size
      (let [offset (* (or current 0) size)]
        ["LIMIT ? OFFSET ?" size offset]))))

(defn generate-group-count-sql
  "Generates a COUNT DISTINCT query for grouped total rows.
   Returns [sql-string & params] suitable for next.jdbc."
  [config {:keys [columns filters group-by search-string]}]
  (let [group-key (first group-by)
        table-name (:table-name config)
        col (col-name config group-key)
        base-sql (str "SELECT COUNT(DISTINCT " col ") AS total FROM " table-name)
        where-clause (combine-where-clauses
                      (generate-where-clause config filters)
                      (generate-global-search-clause config columns search-string))
        sql (if where-clause
              (str base-sql " " (first where-clause))
              base-sql)
        params (when where-clause (rest where-clause))]
    (into [sql] params)))

(defn generate-group-values-sql
  "Generates a DISTINCT group value query for grouped pagination.
   Returns [sql-string & params] suitable for next.jdbc."
  [config {:keys [columns filters group-by page search-string sort]}]
  (let [group-key (first group-by)
        table-name (:table-name config)
        col (col-name config group-key)
        dir (str/upper-case (or (group-sort-direction group-key sort) "asc"))
        base-sql (str "SELECT DISTINCT " col " FROM " table-name)
        where-clause (combine-where-clauses
                      (generate-where-clause config filters)
                      (generate-global-search-clause config columns search-string))
        order-clause (str "ORDER BY " col " " dir)
        limit-clause (generate-limit-clause page)
        sql-parts (cond-> [base-sql]
                    where-clause (conj (first where-clause))
                    order-clause (conj order-clause)
                    limit-clause (conj (first limit-clause)))
        sql (str/join " " sql-parts)
        params (concat (when where-clause (rest where-clause))
                       (when limit-clause (rest limit-clause)))]
    (into [sql] params)))

(defn- generate-grouped-where
  [config columns filters group-key group-values search-string]
  (let [filter-clause (combine-where-clauses
                       (generate-where-clause config filters)
                       (generate-global-search-clause config columns search-string))
        filter-sql (when filter-clause
                     (subs (first filter-clause) (count "WHERE ")))
        filter-params (when filter-clause (rest filter-clause))
        group-sql (when (seq group-values)
                    (str (col-name config group-key)
                         " IN ("
                         (str/join ", " (repeat (count group-values) "?"))
                         ")"))
        sql-parts (remove nil? [filter-sql group-sql])
        sql (when (seq sql-parts)
              (str "WHERE " (str/join " AND " sql-parts)))
        params (concat filter-params group-values)]
    [sql params]))

(defn generate-grouped-query-sql
  "Generates a SELECT query for grouped rows based on group values.
   Returns [sql-string & params] suitable for next.jdbc."
  [config {:keys [columns filters group-by group-values search-string sort]}]
  (let [group-key (first group-by)
        table-name (:table-name config)
        col (col-name config group-key)
        dir (str/upper-case (or (group-sort-direction group-key sort) "asc"))
        base-sql (str "SELECT * FROM " table-name)
        [where-sql where-params] (generate-grouped-where config columns filters group-key group-values search-string)
        order-clause (str "ORDER BY " col " " dir)
        sql-parts (cond-> [base-sql]
                    where-sql (conj where-sql)
                    order-clause (conj order-clause))
        sql (str/join " " sql-parts)]
    (into [sql] where-params)))

(defn generate-query-sql
  "Generates a complete SELECT query with filtering, sorting, and pagination.
   Returns [sql-string & params] suitable for next.jdbc."
  [config {:keys [columns filters page search-string sort]}]
  (let [table-name (:table-name config)
        base-sql (str "SELECT * FROM " table-name)
        where-clause (combine-where-clauses
                      (generate-where-clause config filters)
                      (generate-global-search-clause config columns search-string))
        order-clause (generate-order-clause config sort)
        limit-clause (generate-limit-clause page)
        ;; Build SQL string
        sql-parts (cond-> [base-sql]
                    where-clause (conj (first where-clause))
                    order-clause (conj order-clause)
                    limit-clause (conj (first limit-clause)))
        sql (str/join " " sql-parts)
        ;; Collect params: where params, then limit params
        params (concat (when where-clause (rest where-clause))
                       (when limit-clause (rest limit-clause)))]
    (into [sql] params)))

(defn generate-count-sql
  "Generates a COUNT query for total rows (after filtering).
   Returns [sql-string & params] suitable for next.jdbc."
  [config {:keys [columns filters search-string]}]
  (let [table-name (:table-name config)
        base-sql (str "SELECT COUNT(*) AS total FROM " table-name)
        where-clause (combine-where-clauses
                      (generate-where-clause config filters)
                      (generate-global-search-clause config columns search-string))
        sql (if where-clause
              (str base-sql " " (first where-clause))
              base-sql)
        params (when where-clause (rest where-clause))]
    (into [sql] params)))

(defn rows-fn
  [config execute!]
  (fn [{:keys [columns filters page group-by search-string] :as params} _request]
    (let [size (:size page 10)
          requested-current (:current page)]
      (if (seq group-by)
        (if (= 1 (count group-by))
          (let [sort-spec (:sort params)
                current (if (nil? requested-current)
                          (let [count-sql (generate-group-count-sql config {:columns columns
                                                                            :filters filters
                                                                            :group-by group-by
                                                                            :search-string search-string})
                                total-groups (-> (execute! count-sql) first :total)
                                total-pgs (if (zero? total-groups) 1 (int (Math/ceil (/ total-groups size))))]
                            (max 0 (dec total-pgs)))
                          requested-current)
                group-values-sql (generate-group-values-sql config {:columns columns
                                                                    :filters filters
                                                                    :group-by group-by
                                                                    :search-string search-string
                                                                    :sort sort-spec
                                                                    :page {:size size :current current}})
                group-key (first group-by)
                group-col-key (keyword (col-name config group-key))
                group-values (->> (execute! group-values-sql)
                                  (map group-col-key)
                                  vec)
                rows (if (seq group-values)
                       (let [grouped-query-sql (generate-grouped-query-sql config {:columns columns
                                                                                   :filters filters
                                                                                   :group-by group-by
                                                                                   :search-string search-string
                                                                                   :sort sort-spec
                                                                                   :group-values group-values})]
                         (execute! grouped-query-sql))
                       [])]
            {:rows rows
             :page {:size size :current current}})
          (let [group-sort (first-group-order-sort group-by (:sort params))
                count-sql (generate-count-sql config {:columns columns
                                                      :filters filters
                                                      :search-string search-string})
                total-rows (-> (execute! count-sql) first :total)
                total-pgs (if (zero? total-rows) 1 (int (Math/ceil (/ total-rows size))))
                current (if (nil? requested-current)
                          (max 0 (dec total-pgs))
                          requested-current)
                clamped-current (if (>= current total-pgs)
                                  (max 0 (dec total-pgs))
                                  current)
                query-sql (generate-query-sql config {:columns columns
                                                      :filters filters
                                                      :search-string search-string
                                                      :sort group-sort
                                                      :page {:size size :current clamped-current}})
                rows (execute! query-sql)]
            {:rows rows
             :page {:size size :current clamped-current}}))
        (let [current (if (nil? requested-current)
                        (let [count-sql (generate-count-sql config {:columns columns
                                                                    :filters filters
                                                                    :search-string search-string})
                              total-rows (-> (execute! count-sql) first :total)
                              total-pgs (if (zero? total-rows) 1 (int (Math/ceil (/ total-rows size))))]
                          (max 0 (dec total-pgs)))
                        requested-current)
              query-sql (generate-query-sql config {:columns columns
                                                    :filters filters
                                                    :search-string search-string
                                                    :sort (:sort params)
                                                    :page {:size size :current current}})
              rows (execute! query-sql)]
          {:rows rows
           :page {:size size :current current}})))))

(defn count-fn
  [config execute!]
  (fn [{:keys [columns filters group-by search-string]} _request]
    (if (= 1 (count group-by))
      (let [count-sql (generate-group-count-sql config {:columns columns
                                                        :filters filters
                                                        :group-by group-by
                                                        :search-string search-string})
            total-groups (-> (execute! count-sql) first :total)]
        {:total-rows total-groups})
      (let [count-sql (generate-count-sql config {:columns columns
                                                  :filters filters
                                                  :search-string search-string})
            total-rows (-> (execute! count-sql) first :total)]
        {:total-rows total-rows}))))

(defn query-fn
  "Creates a query function for SQL-backed datatable.

   config: {:table-name \"table\" :column-map {...} :case-sensitive? false :columns [...]}
   execute!: (fn [sqlvec] rows) - executes SQL and returns rows

   Returns a function with signature:
   (fn [{:keys [filters sort page]} request] {:rows [...] :total-rows n :page {...}})"
  [config execute!]
  (fn [{:keys [columns filters page group-by search-string] :as params} _request]
    (if (seq group-by)
      (if (= 1 (count group-by))
        (let [sort-spec (:sort params)
              count-sql (generate-group-count-sql config {:columns columns
                                                          :filters filters
                                                          :group-by group-by
                                                          :search-string search-string})
              total-groups (-> (execute! count-sql) first :total)
              size (:size page 10)
              total-pages (if (zero? total-groups) 1 (int (Math/ceil (/ total-groups size))))
              current (:current page)
              clamped-current (cond
                                (nil? current) (max 0 (dec total-pages))
                                (>= current total-pages) (max 0 (dec total-pages))
                                :else current)
              group-values-sql (generate-group-values-sql config {:columns columns
                                                                  :filters filters
                                                                  :group-by group-by
                                                                  :search-string search-string
                                                                  :sort sort-spec
                                                                  :page {:size size :current clamped-current}})
              group-key (first group-by)
              group-col-key (keyword (col-name config group-key))
              group-values (->> (execute! group-values-sql)
                                (map group-col-key)
                                vec)
              rows (if (seq group-values)
                     (let [grouped-query-sql (generate-grouped-query-sql config {:columns columns
                                                                                 :filters filters
                                                                                 :group-by group-by
                                                                                 :search-string search-string
                                                                                 :sort sort-spec
                                                                                 :group-values group-values})]
                       (execute! grouped-query-sql))
                     [])]
          {:rows rows
           :total-rows total-groups
           :page {:size size :current clamped-current}})
        (let [size (:size page 10)
              group-sort (first-group-order-sort group-by (:sort params))
              count-sql (generate-count-sql config {:columns columns
                                                    :filters filters
                                                    :search-string search-string})
              total-rows (-> (execute! count-sql) first :total)
              total-pages (if (zero? total-rows) 1 (int (Math/ceil (/ total-rows size))))
              requested-current (:current page)
              clamped-current (cond
                                (nil? requested-current) (max 0 (dec total-pages))
                                (>= requested-current total-pages) (max 0 (dec total-pages))
                                :else requested-current)
              query-sql (generate-query-sql config {:columns columns
                                                    :filters filters
                                                    :search-string search-string
                                                    :sort group-sort
                                                    :page {:size size :current clamped-current}})
              rows (execute! query-sql)]
          {:rows rows
           :total-rows total-rows
           :page {:size size :current clamped-current}}))
      (let [sort-spec (:sort params)
            ;; Get total count first
            count-sql (generate-count-sql config {:columns columns
                                                  :filters filters
                                                  :search-string search-string})
            total-rows (-> (execute! count-sql) first :total)
            ;; Calculate page clamping (same logic as in-memory)
            size (:size page 10)
            total-pages (if (zero? total-rows) 1 (int (Math/ceil (/ total-rows size))))
            current (:current page)
            clamped-current (cond
                              (nil? current) (max 0 (dec total-pages))
                              (>= current total-pages) (max 0 (dec total-pages))
                              :else current)
             ;; Generate query with clamped page
            query-sql (generate-query-sql config {:columns columns
                                                  :filters filters
                                                  :search-string search-string
                                                  :sort sort-spec
                                                  :page {:size size :current clamped-current}})
            rows (execute! query-sql)]
        {:rows rows
         :total-rows total-rows
         :page {:size size :current clamped-current}}))))

(defn save-fn
  "Creates a save function for cell edits.

   config:
   - :table      - Table name (string)
   - :id-column  - Column to use for WHERE clause (default :id)

   execute!: (fn [sqlvec] result) - executes SQL (same as query-fn)

   Returns a function with signature:
   (fn [{:keys [row-id col-key value]}] {:success true})

   Example:
     (def save! (save-fn {:table \"philosophers\"} execute!))
     (save! {:row-id 1 :col-key :name :value \"Socrates the Wise\"})"
  [{:keys [table id-column] :or {id-column :id}} execute!]
  (fn [{:keys [row-id col-key value]}]
    (let [sql (str "UPDATE " table " SET " (name col-key) " = ? WHERE " (name id-column) " = ?")]
      (execute! [sql value row-id]))
    {:success true}))
