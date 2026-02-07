(ns pomp.rad.datatable.query.sql-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [pomp.rad.datatable.query.sql :as sql]
            [pomp.rad.datatable.query.in-memory :as imq]))

;; =============================================================================
;; Column Name Resolution
;; =============================================================================

(deftest col-name-test
  (testing "returns keyword as string by default"
    (is (= "name" (sql/col-name {} :name)))
    (is (= "century" (sql/col-name {} :century))))

  (testing "uses column-map when provided"
    (is (= "user_name" (sql/col-name {:column-map {:name "user_name"}} :name)))
    (is (= "birth_century" (sql/col-name {:column-map {:century "birth_century"}} :century))))

  (testing "falls back to keyword string for unmapped keys"
    (is (= "age" (sql/col-name {:column-map {:name "user_name"}} :age)))))

;; =============================================================================
;; String Filter - Contains
;; =============================================================================

(deftest generate-filter-clause-string-contains-test
  (testing "contains filter with case-insensitive (default)"
    (is (= ["LOWER(name) LIKE ?" "%alice%"]
           (sql/generate-filter-clause {} :name {:type "string" :op "contains" :value "Alice"}))))

  (testing "contains filter with blank value returns nil (skip filter)"
    (is (nil? (sql/generate-filter-clause {} :name {:type "string" :op "contains" :value ""})))
    (is (nil? (sql/generate-filter-clause {} :name {:type "string" :op "contains" :value nil}))))

  (testing "case-sensitive when configured at top-level"
    (is (= ["name LIKE ?" "%Alice%"]
           (sql/generate-filter-clause {:case-sensitive? true} :name {:type "string" :op "contains" :value "Alice"}))))

  (testing "case-sensitive when configured per-column"
    (is (= ["name LIKE ?" "%Alice%"]
           (sql/generate-filter-clause {:columns [{:key :name :case-sensitive? true}]}
                                       :name {:type "string" :op "contains" :value "Alice"}))))

  (testing "per-column case-sensitive overrides top-level"
    (is (= ["name LIKE ?" "%Alice%"]
           (sql/generate-filter-clause {:case-sensitive? false
                                        :columns [{:key :name :case-sensitive? true}]}
                                       :name {:type "string" :op "contains" :value "Alice"})))
    (is (= ["LOWER(name) LIKE ?" "%alice%"]
           (sql/generate-filter-clause {:case-sensitive? true
                                        :columns [{:key :name :case-sensitive? false}]}
                                       :name {:type "string" :op "contains" :value "Alice"}))))

  (testing "uses column-map for column name"
    (is (= ["LOWER(user_name) LIKE ?" "%alice%"]
           (sql/generate-filter-clause {:column-map {:name "user_name"}}
                                       :name {:type "string" :op "contains" :value "Alice"})))))

;; =============================================================================
;; String Filter - Other Ops
;; =============================================================================

(deftest generate-filter-clause-string-not-contains-test
  (testing "not-contains filter"
    (is (= ["LOWER(name) NOT LIKE ?" "%alice%"]
           (sql/generate-filter-clause {} :name {:type "string" :op "not-contains" :value "Alice"}))))

  (testing "not-contains with blank value returns nil"
    (is (nil? (sql/generate-filter-clause {} :name {:type "string" :op "not-contains" :value ""})))))

(deftest generate-filter-clause-string-equals-test
  (testing "equals filter case-insensitive"
    (is (= ["LOWER(name) = ?" "alice"]
           (sql/generate-filter-clause {} :name {:type "string" :op "equals" :value "Alice"}))))

  (testing "equals filter case-sensitive"
    (is (= ["name = ?" "Alice"]
           (sql/generate-filter-clause {:case-sensitive? true} :name {:type "string" :op "equals" :value "Alice"})))))

(deftest generate-filter-clause-string-not-equals-test
  (testing "not-equals filter"
    (is (= ["LOWER(name) <> ?" "alice"]
           (sql/generate-filter-clause {} :name {:type "string" :op "not-equals" :value "Alice"})))))

(deftest generate-filter-clause-string-starts-with-test
  (testing "starts-with filter"
    (is (= ["LOWER(name) LIKE ?" "al%"]
           (sql/generate-filter-clause {} :name {:type "string" :op "starts-with" :value "Al"})))))

(deftest generate-filter-clause-string-ends-with-test
  (testing "ends-with filter"
    (is (= ["LOWER(name) LIKE ?" "%ce"]
           (sql/generate-filter-clause {} :name {:type "string" :op "ends-with" :value "ce"})))))

(deftest generate-filter-clause-string-is-empty-test
  (testing "is-empty filter (no params needed, ignores value)"
    (is (= ["(name IS NULL OR name = '')"]
           (sql/generate-filter-clause {} :name {:type "string" :op "is-empty" :value ""})))
    (is (= ["(name IS NULL OR name = '')"]
           (sql/generate-filter-clause {} :name {:type "string" :op "is-empty" :value "ignored"})))))

(deftest generate-filter-clause-string-is-not-empty-test
  (testing "is-not-empty filter"
    (is (= ["(name IS NOT NULL AND name <> '')"]
           (sql/generate-filter-clause {} :name {:type "string" :op "is-not-empty" :value ""})))))

(deftest generate-filter-clause-string-is-any-of-test
  (testing "is-any-of with multiple values (case-insensitive)"
    (is (= ["LOWER(name) IN (?, ?)" "alice" "bob"]
           (sql/generate-filter-clause {} :name {:type "string" :op "is-any-of" :value ["Alice" "Bob"]}))))

  (testing "is-any-of with single value"
    (is (= ["LOWER(name) IN (?)" "alice"]
           (sql/generate-filter-clause {} :name {:type "string" :op "is-any-of" :value ["Alice"]}))))

  (testing "is-any-of case-sensitive"
    (is (= ["name IN (?, ?)" "Alice" "Bob"]
           (sql/generate-filter-clause {:case-sensitive? true} :name {:type "string" :op "is-any-of" :value ["Alice" "Bob"]}))))

  (testing "is-any-of with empty array returns nil"
    (is (nil? (sql/generate-filter-clause {} :name {:type "string" :op "is-any-of" :value []})))))

;; =============================================================================
;; Number Filters
;; =============================================================================

(deftest generate-filter-clause-number-equals-test
  (testing "number equals"
    (is (= ["age = ?" 30]
           (sql/generate-filter-clause {} :age {:type "number" :op "equals" :value "30"}))))

  (testing "number equals with decimal"
    (is (= ["price = ?" 19.99]
           (sql/generate-filter-clause {} :price {:type "number" :op "equals" :value "19.99"}))))

  (testing "number equals with negative"
    (is (= ["century = ?" -5]
           (sql/generate-filter-clause {} :century {:type "number" :op "equals" :value "-5"})))))

(deftest generate-filter-clause-number-not-equals-test
  (testing "number not-equals"
    (is (= ["age <> ?" 30]
           (sql/generate-filter-clause {} :age {:type "number" :op "not-equals" :value "30"})))))

(deftest generate-filter-clause-number-greater-than-test
  (testing "number greater-than"
    (is (= ["age > ?" 25]
           (sql/generate-filter-clause {} :age {:type "number" :op "greater-than" :value "25"})))))

(deftest generate-filter-clause-number-greater-than-or-equal-test
  (testing "number greater-than-or-equal"
    (is (= ["age >= ?" 30]
           (sql/generate-filter-clause {} :age {:type "number" :op "greater-than-or-equal" :value "30"})))))

(deftest generate-filter-clause-number-less-than-test
  (testing "number less-than"
    (is (= ["age < ?" 30]
           (sql/generate-filter-clause {} :age {:type "number" :op "less-than" :value "30"})))))

(deftest generate-filter-clause-number-less-than-or-equal-test
  (testing "number less-than-or-equal"
    (is (= ["age <= ?" 30]
           (sql/generate-filter-clause {} :age {:type "number" :op "less-than-or-equal" :value "30"})))))

(deftest generate-filter-clause-number-is-empty-test
  (testing "number is-empty"
    (is (= ["age IS NULL"]
           (sql/generate-filter-clause {} :age {:type "number" :op "is-empty" :value ""})))))

(deftest generate-filter-clause-number-is-not-empty-test
  (testing "number is-not-empty"
    (is (= ["age IS NOT NULL"]
           (sql/generate-filter-clause {} :age {:type "number" :op "is-not-empty" :value ""})))))

(deftest generate-filter-clause-number-blank-value-test
  (testing "number filter with blank value returns nil"
    (is (nil? (sql/generate-filter-clause {} :age {:type "number" :op "equals" :value ""})))
    (is (nil? (sql/generate-filter-clause {} :age {:type "number" :op "equals" :value nil})))))

;; =============================================================================
;; Boolean Filters
;; =============================================================================

(deftest generate-filter-clause-boolean-is-test
  (testing "boolean is true"
    (is (= ["active = ?" true]
           (sql/generate-filter-clause {} :active {:type "boolean" :op "is" :value "true"}))))

  (testing "boolean is false"
    (is (= ["active = ?" false]
           (sql/generate-filter-clause {} :active {:type "boolean" :op "is" :value "false"})))))

(deftest generate-filter-clause-boolean-is-not-test
  (testing "boolean is-not true"
    (is (= ["active <> ?" true]
           (sql/generate-filter-clause {} :active {:type "boolean" :op "is-not" :value "true"}))))

  (testing "boolean is-not false"
    (is (= ["active <> ?" false]
           (sql/generate-filter-clause {} :active {:type "boolean" :op "is-not" :value "false"})))))

(deftest generate-filter-clause-boolean-is-empty-test
  (testing "boolean is-empty"
    (is (= ["active IS NULL"]
           (sql/generate-filter-clause {} :active {:type "boolean" :op "is-empty" :value ""})))))

(deftest generate-filter-clause-boolean-is-not-empty-test
  (testing "boolean is-not-empty"
    (is (= ["active IS NOT NULL"]
           (sql/generate-filter-clause {} :active {:type "boolean" :op "is-not-empty" :value ""})))))

;; =============================================================================
;; Date Filters
;; =============================================================================

(deftest generate-filter-clause-date-is-test
  (testing "date is exact match"
    (is (= ["created = ?" "2024-01-15"]
           (sql/generate-filter-clause {} :created {:type "date" :op "is" :value "2024-01-15"})))))

(deftest generate-filter-clause-date-is-not-test
  (testing "date is-not"
    (is (= ["created <> ?" "2024-01-15"]
           (sql/generate-filter-clause {} :created {:type "date" :op "is-not" :value "2024-01-15"})))))

(deftest generate-filter-clause-date-after-test
  (testing "date after"
    (is (= ["created > ?" "2024-01-15"]
           (sql/generate-filter-clause {} :created {:type "date" :op "after" :value "2024-01-15"})))))

(deftest generate-filter-clause-date-on-or-after-test
  (testing "date on-or-after"
    (is (= ["created >= ?" "2024-01-15"]
           (sql/generate-filter-clause {} :created {:type "date" :op "on-or-after" :value "2024-01-15"})))))

(deftest generate-filter-clause-date-before-test
  (testing "date before"
    (is (= ["created < ?" "2024-02-20"]
           (sql/generate-filter-clause {} :created {:type "date" :op "before" :value "2024-02-20"})))))

(deftest generate-filter-clause-date-on-or-before-test
  (testing "date on-or-before"
    (is (= ["created <= ?" "2024-02-20"]
           (sql/generate-filter-clause {} :created {:type "date" :op "on-or-before" :value "2024-02-20"})))))

(deftest generate-filter-clause-date-is-empty-test
  (testing "date is-empty"
    (is (= ["created IS NULL"]
           (sql/generate-filter-clause {} :created {:type "date" :op "is-empty" :value ""})))))

(deftest generate-filter-clause-date-is-not-empty-test
  (testing "date is-not-empty"
    (is (= ["created IS NOT NULL"]
           (sql/generate-filter-clause {} :created {:type "date" :op "is-not-empty" :value ""})))))

(deftest generate-filter-clause-date-blank-value-test
  (testing "date filter with blank value returns nil"
    (is (nil? (sql/generate-filter-clause {} :created {:type "date" :op "is" :value ""})))
    (is (nil? (sql/generate-filter-clause {} :created {:type "date" :op "after" :value nil})))))

;; =============================================================================
;; Enum Filters
;; =============================================================================

(deftest generate-filter-clause-enum-is-test
  (testing "enum is exact match (case-sensitive)"
    (is (= ["status = ?" "active"]
           (sql/generate-filter-clause {} :status {:type "enum" :op "is" :value "active"})))))

(deftest generate-filter-clause-enum-is-not-test
  (testing "enum is-not"
    (is (= ["status <> ?" "active"]
           (sql/generate-filter-clause {} :status {:type "enum" :op "is-not" :value "active"})))))

(deftest generate-filter-clause-enum-is-any-of-test
  (testing "enum is-any-of with multiple values"
    (is (= ["status IN (?, ?)" "active" "pending"]
           (sql/generate-filter-clause {} :status {:type "enum" :op "is-any-of" :value ["active" "pending"]}))))

  (testing "enum is-any-of with single value"
    (is (= ["status IN (?)" "active"]
           (sql/generate-filter-clause {} :status {:type "enum" :op "is-any-of" :value ["active"]}))))

  (testing "enum is-any-of with empty array returns nil"
    (is (nil? (sql/generate-filter-clause {} :status {:type "enum" :op "is-any-of" :value []})))))

(deftest generate-filter-clause-enum-is-empty-test
  (testing "enum is-empty"
    (is (= ["status IS NULL"]
           (sql/generate-filter-clause {} :status {:type "enum" :op "is-empty" :value ""})))))

(deftest generate-filter-clause-enum-is-not-empty-test
  (testing "enum is-not-empty"
    (is (= ["status IS NOT NULL"]
           (sql/generate-filter-clause {} :status {:type "enum" :op "is-not-empty" :value ""})))))

;; =============================================================================
;; Combining Filters
;; =============================================================================

(deftest generate-where-clause-single-filter-test
  (testing "single filter on single column"
    (is (= ["WHERE LOWER(name) LIKE ?" "%alice%"]
           (sql/generate-where-clause {} {:name [{:type "string" :op "contains" :value "Alice"}]})))))

(deftest generate-where-clause-multiple-filters-same-column-test
  (testing "multiple filters on same column use AND"
    (is (= ["WHERE LOWER(name) LIKE ? AND LOWER(name) LIKE ?" "%a%" "%e"]
           (sql/generate-where-clause {} {:name [{:type "string" :op "contains" :value "a"}
                                                 {:type "string" :op "ends-with" :value "e"}]})))))

(deftest generate-where-clause-multiple-columns-test
  (testing "filters across multiple columns use AND"
    (is (= ["WHERE LOWER(name) LIKE ? AND age > ?" "%alice%" 25]
           (sql/generate-where-clause {} {:name [{:type "string" :op "contains" :value "Alice"}]
                                          :age [{:type "number" :op "greater-than" :value "25"}]})))))

(deftest generate-where-clause-empty-filters-test
  (testing "empty filters returns nil"
    (is (nil? (sql/generate-where-clause {} {}))))

  (testing "nil filters returns nil"
    (is (nil? (sql/generate-where-clause {} nil)))))

(deftest generate-where-clause-skipped-filters-test
  (testing "filters with blank values are skipped"
    (is (= ["WHERE age > ?" 25]
           (sql/generate-where-clause {} {:name [{:type "string" :op "contains" :value ""}]
                                          :age [{:type "number" :op "greater-than" :value "25"}]}))))

  (testing "all filters skipped returns nil"
    (is (nil? (sql/generate-where-clause {} {:name [{:type "string" :op "contains" :value ""}]})))))

;; =============================================================================
;; Sort Clause
;; =============================================================================

(deftest generate-order-clause-ascending-test
  (testing "single column ascending"
    (is (= "ORDER BY name ASC"
           (sql/generate-order-clause {} [{:column "name" :direction "asc"}])))))

(deftest generate-order-clause-descending-test
  (testing "single column descending"
    (is (= "ORDER BY name DESC"
           (sql/generate-order-clause {} [{:column "name" :direction "desc"}])))))

(deftest generate-order-clause-empty-test
  (testing "empty sort spec returns nil"
    (is (nil? (sql/generate-order-clause {} []))))

  (testing "nil sort spec returns nil"
    (is (nil? (sql/generate-order-clause {} nil)))))

(deftest generate-order-clause-column-map-test
  (testing "uses column-map for column name"
    (is (= "ORDER BY user_name ASC"
           (sql/generate-order-clause {:column-map {:name "user_name"}} [{:column "name" :direction "asc"}])))))

;; =============================================================================
;; Pagination
;; =============================================================================

(deftest generate-limit-clause-first-page-test
  (testing "first page has offset 0"
    (is (= ["LIMIT ? OFFSET ?" 10 0]
           (sql/generate-limit-clause {:size 10 :current 0})))))

(deftest generate-limit-clause-middle-page-test
  (testing "middle page calculates offset correctly"
    (is (= ["LIMIT ? OFFSET ?" 10 20]
           (sql/generate-limit-clause {:size 10 :current 2})))))

(deftest generate-limit-clause-custom-size-test
  (testing "respects custom page size"
    (is (= ["LIMIT ? OFFSET ?" 25 50]
           (sql/generate-limit-clause {:size 25 :current 2})))))

(deftest generate-limit-clause-nil-page-test
  (testing "nil page spec returns nil"
    (is (nil? (sql/generate-limit-clause nil))))

  (testing "missing size returns nil"
    (is (nil? (sql/generate-limit-clause {:current 0})))))

;; =============================================================================
;; Full Query Generation
;; =============================================================================

(deftest generate-query-sql-basic-test
  (testing "basic query with table name only"
    (is (= ["SELECT * FROM philosophers"]
           (sql/generate-query-sql {:table-name "philosophers"} {})))))

(deftest generate-query-sql-with-filters-test
  (testing "query with filters"
    (is (= ["SELECT * FROM philosophers WHERE LOWER(name) LIKE ?" "%socrates%"]
           (sql/generate-query-sql {:table-name "philosophers"}
                                   {:filters {:name [{:type "string" :op "contains" :value "Socrates"}]}})))))

(deftest generate-query-sql-with-sort-test
  (testing "query with sort"
    (is (= ["SELECT * FROM philosophers ORDER BY name ASC"]
           (sql/generate-query-sql {:table-name "philosophers"}
                                   {:sort [{:column "name" :direction "asc"}]})))))

(deftest generate-query-sql-with-pagination-test
  (testing "query with pagination"
    (is (= ["SELECT * FROM philosophers LIMIT ? OFFSET ?" 10 0]
           (sql/generate-query-sql {:table-name "philosophers"}
                                   {:page {:size 10 :current 0}})))))

(deftest generate-query-sql-full-test
  (testing "query with all options"
    (is (= ["SELECT * FROM philosophers WHERE LOWER(name) LIKE ? ORDER BY century DESC LIMIT ? OFFSET ?"
            "%plato%" 10 20]
           (sql/generate-query-sql {:table-name "philosophers"}
                                   {:filters {:name [{:type "string" :op "contains" :value "Plato"}]}
                                    :sort [{:column "century" :direction "desc"}]
                                    :page {:size 10 :current 2}}))))

  (testing "parameter ordering is correct"
    (let [[sql & params] (sql/generate-query-sql
                          {:table-name "philosophers"}
                          {:filters {:name [{:type "string" :op "contains" :value "test"}]
                                     :century [{:type "number" :op "greater-than" :value "0"}]}
                           :sort [{:column "name" :direction "asc"}]
                           :page {:size 25 :current 1}})]
      ;; Filter params come before pagination params
      (is (= ["%test%" 0 25 25] params)))))

;; =============================================================================
;; Count Query
;; =============================================================================

(deftest generate-count-sql-basic-test
  (testing "count query without filters"
    (is (= ["SELECT COUNT(*) AS total FROM philosophers"]
           (sql/generate-count-sql {:table-name "philosophers"} {})))))

(deftest generate-count-sql-with-filters-test
  (testing "count query with filters"
    (is (= ["SELECT COUNT(*) AS total FROM philosophers WHERE LOWER(name) LIKE ?" "%socrates%"]
           (sql/generate-count-sql {:table-name "philosophers"}
                                   {:filters {:name [{:type "string" :op "contains" :value "Socrates"}]}}))))

  (testing "count query with multiple filters"
    (is (= ["SELECT COUNT(*) AS total FROM philosophers WHERE LOWER(name) LIKE ? AND century > ?" "%plato%" 0]
           (sql/generate-count-sql {:table-name "philosophers"}
                                   {:filters {:name [{:type "string" :op "contains" :value "Plato"}]
                                              :century [{:type "number" :op "greater-than" :value "0"}]}})))))

;; =============================================================================
;; Query Function Builder
;; =============================================================================

(def mock-philosophers
  [{:id 1 :name "Socrates" :century -5 :school "Classical Greek"}
   {:id 2 :name "Plato" :century -4 :school "Platonism"}
   {:id 3 :name "Aristotle" :century -4 :school "Peripatetic"}])

(deftest query-fn-basic-test
  (testing "query-fn returns correct structure"
    (let [execute-calls (atom [])
          execute! (fn [sqlvec]
                     (swap! execute-calls conj sqlvec)
                     (if (str/starts-with? (first sqlvec) "SELECT COUNT")
                       [{:total 3}]
                       mock-philosophers))
          qfn (sql/query-fn {:table-name "philosophers"} execute!)]
      (let [result (qfn {:filters {} :sort [] :page {:size 10 :current 0}} nil)]
        (is (= mock-philosophers (:rows result)))
        (is (= 3 (:total-rows result)))
        (is (= {:size 10 :current 0} (:page result)))))))

(deftest query-fn-page-clamping-test
  (testing "clamps page when beyond total"
    (let [execute! (fn [sqlvec]
                     (if (str/starts-with? (first sqlvec) "SELECT COUNT")
                       [{:total 3}]
                       mock-philosophers))
          qfn (sql/query-fn {:table-name "philosophers"} execute!)]
      ;; Request page 100, but only 1 page exists with size 10
      (let [result (qfn {:filters {} :sort [] :page {:size 10 :current 100}} nil)]
        (is (= 0 (get-in result [:page :current])))))))

(deftest query-fn-nil-page-to-last-test
  (testing "nil current defaults to last page"
    (let [execute! (fn [sqlvec]
                     (if (str/starts-with? (first sqlvec) "SELECT COUNT")
                       [{:total 25}] ;; 25 rows, size 10 = 3 pages (0, 1, 2)
                       mock-philosophers))
          qfn (sql/query-fn {:table-name "philosophers"} execute!)]
      (let [result (qfn {:filters {} :sort [] :page {:size 10 :current nil}} nil)]
        (is (= 2 (get-in result [:page :current])))))))

(deftest query-fn-executes-correct-sql-test
  (testing "executes count and query SQL"
    (let [execute-calls (atom [])
          execute! (fn [sqlvec]
                     (swap! execute-calls conj sqlvec)
                     (if (str/starts-with? (first sqlvec) "SELECT COUNT")
                       [{:total 3}]
                       mock-philosophers))
          qfn (sql/query-fn {:table-name "philosophers"} execute!)]
      (qfn {:filters {:name [{:type "string" :op "contains" :value "Plato"}]}
            :sort [{:column "century" :direction "desc"}]
            :page {:size 10 :current 0}}
           nil)
      ;; Should have called execute! twice: once for count, once for query
      (is (= 2 (count @execute-calls)))
      ;; First call is count
      (is (str/starts-with? (first (first @execute-calls)) "SELECT COUNT"))
      ;; Second call is query
      (is (str/starts-with? (first (second @execute-calls)) "SELECT *")))))

;; =============================================================================
;; Integration with H2
;; =============================================================================

(def h2-db {:dbtype "h2:mem" :dbname "test"})

(def h2-philosophers
  [{:id 1 :name "Socrates" :century -5 :school "Classical Greek" :region "Greece"}
   {:id 2 :name "Plato" :century -4 :school "Platonism" :region "Greece"}
   {:id 3 :name "Aristotle" :century -4 :school "Peripatetic" :region "Greece"}
   {:id 4 :name "Confucius" :century -5 :school "Confucianism" :region "China"}
   {:id 5 :name "Epicurus" :century -3 :school "Epicureanism" :region "Greece"}])

(def h2-grouped-philosophers
  [{:id 1 :name "A1" :century 2 :school "Academy" :region "Greece"}
   {:id 2 :name "A2" :century 4 :school "Academy" :region "Greece"}
   {:id 3 :name "B1" :century 1 :school "Lyceum" :region "Greece"}
   {:id 4 :name "C1" :century 3 :school "Stoa" :region "Greece"}
   {:id 5 :name "C2" :century 5 :school "Stoa" :region "Greece"}])

(defn setup-h2-db [ds]
  (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS philosophers (
                       id INT PRIMARY KEY,
                       name VARCHAR(100),
                       century INT,
                       school VARCHAR(100),
                       region VARCHAR(100))"])
  (jdbc/execute! ds ["DELETE FROM philosophers"])
  (doseq [p h2-philosophers]
    (jdbc/execute! ds ["INSERT INTO philosophers (id, name, century, school, region) VALUES (?, ?, ?, ?, ?)"
                       (:id p) (:name p) (:century p) (:school p) (:region p)])))

(defn seed-h2-data!
  [ds rows]
  (jdbc/execute! ds ["DELETE FROM philosophers"])
  (doseq [p rows]
    (jdbc/execute! ds ["INSERT INTO philosophers (id, name, century, school, region) VALUES (?, ?, ?, ?, ?)"
                       (:id p) (:name p) (:century p) (:school p) (:region p)])))

(deftest h2-integration-basic-test
  (testing "basic query against H2"
    (let [ds (jdbc/get-datasource h2-db)]
      (setup-h2-db ds)
      (let [execute! (fn [sqlvec] (jdbc/execute! ds sqlvec {:builder-fn rs/as-unqualified-lower-maps}))
            qfn (sql/query-fn {:table-name "philosophers"} execute!)
            result (qfn {:filters {} :sort [] :page {:size 10 :current 0}} nil)]
        (is (= 5 (:total-rows result)))
        (is (= 5 (count (:rows result))))))))

(deftest h2-integration-filter-test
  (testing "filtering against H2"
    (let [ds (jdbc/get-datasource h2-db)]
      (setup-h2-db ds)
      (let [execute! (fn [sqlvec] (jdbc/execute! ds sqlvec {:builder-fn rs/as-unqualified-lower-maps}))
            qfn (sql/query-fn {:table-name "philosophers"} execute!)
            result (qfn {:filters {:region [{:type "string" :op "equals" :value "Greece"}]}
                         :sort []
                         :page {:size 10 :current 0}}
                        nil)]
        (is (= 4 (:total-rows result)))
        (is (every? #(= "Greece" (:region %)) (:rows result)))))))

(deftest h2-integration-sort-test
  (testing "sorting against H2"
    (let [ds (jdbc/get-datasource h2-db)]
      (setup-h2-db ds)
      (let [execute! (fn [sqlvec] (jdbc/execute! ds sqlvec {:builder-fn rs/as-unqualified-lower-maps}))
            qfn (sql/query-fn {:table-name "philosophers"} execute!)
            result (qfn {:filters {} :sort [{:column "century" :direction "asc"}] :page {:size 10 :current 0}} nil)
            centuries (map :century (:rows result))]
        (is (= (sort centuries) centuries))))))

(deftest h2-integration-pagination-test
  (testing "pagination against H2"
    (let [ds (jdbc/get-datasource h2-db)]
      (setup-h2-db ds)
      (let [execute! (fn [sqlvec] (jdbc/execute! ds sqlvec {:builder-fn rs/as-unqualified-lower-maps}))
            qfn (sql/query-fn {:table-name "philosophers"} execute!)
            result (qfn {:filters {} :sort [{:column "id" :direction "asc"}] :page {:size 2 :current 0}} nil)]
        (is (= 5 (:total-rows result)))
        (is (= 2 (count (:rows result))))
        (is (= [1 2] (map :id (:rows result))))))))

(deftest h2-vs-in-memory-parity-test
  (testing "SQL query-fn produces same results as in-memory"
    (let [ds (jdbc/get-datasource h2-db)]
      (setup-h2-db ds)
      (let [execute! (fn [sqlvec] (jdbc/execute! ds sqlvec {:builder-fn rs/as-unqualified-lower-maps}))
            sql-qfn (sql/query-fn {:table-name "philosophers"} execute!)
            mem-qfn (imq/query-fn h2-philosophers)
            ;; Use id sort to get deterministic ordering
            query-params {:filters {:region [{:type "string" :op "equals" :value "Greece"}]}
                          :sort [{:column "id" :direction "asc"}]
                          :page {:size 2 :current 0}}
            sql-result (sql-qfn query-params nil)
            mem-result (mem-qfn query-params nil)]
        (is (= (:total-rows sql-result) (:total-rows mem-result)))
        (is (= (:page sql-result) (:page mem-result)))
        ;; Compare row contents (ids should match when sorted by id)
        (is (= (map :id (:rows sql-result)) (map :id (:rows mem-result))))))))

(deftest query-fn-grouped-pagination-test
  (testing "grouped pagination counts groups and keeps groups intact"
    (let [ds (jdbc/get-datasource h2-db)]
      (setup-h2-db ds)
      (seed-h2-data! ds h2-grouped-philosophers)
      (let [execute! (fn [sqlvec] (jdbc/execute! ds sqlvec {:builder-fn rs/as-unqualified-lower-maps}))
            qfn (sql/query-fn {:table-name "philosophers"} execute!)
            result (qfn {:filters {}
                         :sort [{:column "school" :direction "asc"}]
                         :group-by [:school]
                         :page {:size 2 :current 0}}
                        nil)
            expected-groups (->> h2-grouped-philosophers (map :school) distinct sort (take 2) vec)
            actual-groups (->> (:rows result) (map :school) distinct vec)]
        (is (= 3 (:total-rows result))
            "Expected total-rows to reflect group count when grouped")
        (is (= expected-groups actual-groups)
            "Expected page to include the first two groups")
        (is (= 3 (count (:rows result)))
            "Expected rows for all groups on the page")
        (is (= 2 (count (filter #(= "Academy" (:school %)) (:rows result))))
            "Expected full group rows for Academy")))))

(deftest query-fn-grouped-sort-and-filter-test
  (testing "non-grouped sorting does not reorder groups"
    (let [ds (jdbc/get-datasource h2-db)]
      (setup-h2-db ds)
      (seed-h2-data! ds h2-grouped-philosophers)
      (let [execute! (fn [sqlvec] (jdbc/execute! ds sqlvec {:builder-fn rs/as-unqualified-lower-maps}))
            qfn (sql/query-fn {:table-name "philosophers"} execute!)
            result (qfn {:filters {}
                         :sort [{:column "century" :direction "asc"}]
                         :group-by [:school]
                         :page {:size 10 :current 0}}
                        nil)
            expected-order (->> h2-grouped-philosophers (map :school) distinct sort vec)
            actual-order (->> (:rows result) (map :school) distinct vec)]
        (is (= expected-order actual-order)
            "Expected group order to stay aligned with grouped column"))))

  (testing "non-grouped sort direction is ignored when grouped"
    (let [ds (jdbc/get-datasource h2-db)]
      (setup-h2-db ds)
      (seed-h2-data! ds h2-grouped-philosophers)
      (let [execute! (fn [sqlvec] (jdbc/execute! ds sqlvec {:builder-fn rs/as-unqualified-lower-maps}))
            qfn (sql/query-fn {:table-name "philosophers"} execute!)
            result (qfn {:filters {}
                         :sort [{:column "century" :direction "desc"}]
                         :group-by [:school]
                         :page {:size 10 :current 0}}
                        nil)
            expected-order (->> h2-grouped-philosophers (map :school) distinct sort vec)
            actual-order (->> (:rows result) (map :school) distinct vec)]
        (is (= expected-order actual-order)
            "Expected grouped order to ignore non-grouped sort direction"))))

  (testing "filtering by grouped column counts groups, not rows"
    (let [ds (jdbc/get-datasource h2-db)]
      (setup-h2-db ds)
      (seed-h2-data! ds h2-grouped-philosophers)
      (let [execute! (fn [sqlvec] (jdbc/execute! ds sqlvec {:builder-fn rs/as-unqualified-lower-maps}))
            qfn (sql/query-fn {:table-name "philosophers"} execute!)
            result (qfn {:filters {:school [{:type "enum" :op "is" :value "Academy"}]}
                         :sort []
                         :group-by [:school]
                         :page {:size 10 :current 0}}
                        nil)]
        (is (= 1 (:total-rows result))
            "Expected total-rows to equal group count after filtering")))))

;; =============================================================================
;; Save Function
;; =============================================================================

(deftest save-fn-generates-correct-update-test
  (testing "generates correct UPDATE statement with default id-column"
    (let [ds (jdbc/get-datasource h2-db)
          execute! (fn [sqlvec] (jdbc/execute! ds sqlvec))]
      (setup-h2-db ds)
      ;; Verify initial value
      (is (= "Socrates" (-> (jdbc/execute! ds ["SELECT name FROM philosophers WHERE id = ?" 1]
                                           {:builder-fn rs/as-unqualified-lower-maps})
                            first :name)))
      ;; Execute save
      (let [save! (sql/save-fn {:table "philosophers"} execute!)]
        (save! {:row-id 1 :col-key :name :value "Socrates the Wise"}))
      ;; Verify updated value
      (is (= "Socrates the Wise" (-> (jdbc/execute! ds ["SELECT name FROM philosophers WHERE id = ?" 1]
                                                    {:builder-fn rs/as-unqualified-lower-maps})
                                     first :name)))))

  (testing "updates numeric columns"
    (let [ds (jdbc/get-datasource h2-db)
          execute! (fn [sqlvec] (jdbc/execute! ds sqlvec))]
      (setup-h2-db ds)
      (let [save! (sql/save-fn {:table "philosophers"} execute!)]
        (save! {:row-id 2 :col-key :century :value -3}))
      (is (= -3 (-> (jdbc/execute! ds ["SELECT century FROM philosophers WHERE id = ?" 2]
                                   {:builder-fn rs/as-unqualified-lower-maps})
                    first :century))))))

(deftest save-fn-custom-id-column-test
  (testing "uses custom id-column for WHERE clause"
    (let [ds (jdbc/get-datasource h2-db)
          execute! (fn [sqlvec] (jdbc/execute! ds sqlvec))]
      ;; Create a table with a different id column name
      (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS custom_table (
                           philosopher_id INT PRIMARY KEY,
                           name VARCHAR(100))"])
      (jdbc/execute! ds ["DELETE FROM custom_table"])
      (jdbc/execute! ds ["INSERT INTO custom_table (philosopher_id, name) VALUES (?, ?)" 1 "Test"])
      ;; Execute save with custom id column
      (let [save! (sql/save-fn {:table "custom_table"
                                :id-column :philosopher_id}
                               execute!)]
        (save! {:row-id 1 :col-key :name :value "Updated Test"}))
      ;; Verify update
      (is (= "Updated Test" (-> (jdbc/execute! ds ["SELECT name FROM custom_table WHERE philosopher_id = ?" 1]
                                               {:builder-fn rs/as-unqualified-lower-maps})
                                first :name)))
      ;; Cleanup
      (jdbc/execute! ds ["DROP TABLE custom_table"]))))

(deftest save-fn-returns-success-test
  (testing "returns success map on successful save"
    (let [ds (jdbc/get-datasource h2-db)
          execute! (fn [sqlvec] (jdbc/execute! ds sqlvec))]
      (setup-h2-db ds)
      (let [save! (sql/save-fn {:table "philosophers"} execute!)
            result (save! {:row-id 1 :col-key :name :value "New Name"})]
        (is (= {:success true} result))))))

(deftest save-fn-string-row-id-test
  (testing "handles string row-id"
    (let [ds (jdbc/get-datasource h2-db)
          execute! (fn [sqlvec] (jdbc/execute! ds sqlvec))]
      (setup-h2-db ds)
      ;; Row ID comes as string from signals
      (let [save! (sql/save-fn {:table "philosophers"} execute!)]
        (save! {:row-id "3" :col-key :school :value "Updated School"}))
      (is (= "Updated School" (-> (jdbc/execute! ds ["SELECT school FROM philosophers WHERE id = ?" 3]
                                                 {:builder-fn rs/as-unqualified-lower-maps})
                                  first :school))))))

(deftest save-fn-boolean-value-test
  (testing "handles boolean true value"
    (let [ds (jdbc/get-datasource h2-db)
          execute! (fn [sqlvec] (jdbc/execute! ds sqlvec))]
      ;; Create a table with a boolean column
      (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS bool_test (
                           id INT PRIMARY KEY,
                           verified BOOLEAN)"])
      (jdbc/execute! ds ["DELETE FROM bool_test"])
      (jdbc/execute! ds ["INSERT INTO bool_test (id, verified) VALUES (?, ?)" 1 false])
      ;; Save with boolean true
      (let [save! (sql/save-fn {:table "bool_test"} execute!)]
        (save! {:row-id 1 :col-key :verified :value true}))
      (is (= true (-> (jdbc/execute! ds ["SELECT verified FROM bool_test WHERE id = ?" 1]
                                     {:builder-fn rs/as-unqualified-lower-maps})
                      first :verified)))
      (jdbc/execute! ds ["DROP TABLE bool_test"])))

  (testing "handles boolean false value"
    (let [ds (jdbc/get-datasource h2-db)
          execute! (fn [sqlvec] (jdbc/execute! ds sqlvec))]
      (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS bool_test (
                           id INT PRIMARY KEY,
                           verified BOOLEAN)"])
      (jdbc/execute! ds ["DELETE FROM bool_test"])
      (jdbc/execute! ds ["INSERT INTO bool_test (id, verified) VALUES (?, ?)" 1 true])
      ;; Save with boolean false
      (let [save! (sql/save-fn {:table "bool_test"} execute!)]
        (save! {:row-id 1 :col-key :verified :value false}))
      (is (= false (-> (jdbc/execute! ds ["SELECT verified FROM bool_test WHERE id = ?" 1]
                                      {:builder-fn rs/as-unqualified-lower-maps})
                       first :verified)))
      (jdbc/execute! ds ["DROP TABLE bool_test"]))))
