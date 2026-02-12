(ns pomp.rad.datatable.query.in-memory-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.query.in-memory :as query]
            [pomp.rad.datatable.state.group :as group-state]))

;; =============================================================================
;; New filter structure: {:filters {:col-key [{:type "text" :op "contains" :value "x"} ...]}}
;; Each column maps to a VECTOR of filter specs, enabling multiple filters per column.
;; Multiple filters on the same column use AND logic.
;; =============================================================================

(def test-rows
  [{:id 1 :name "Alice" :age 30 :city "New York"}
   {:id 2 :name "Bob" :age 25 :city "Boston"}
   {:id 3 :name "Charlie" :age 35 :city "Chicago"}
   {:id 4 :name "Diana" :age 28 :city "Denver"}
   {:id 5 :name "Eve" :age 32 :city ""}])

;; =============================================================================
;; Filter Tests
;; =============================================================================

(deftest apply-filters-contains-test
  (testing "contains filter"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}]
           (query/apply-filters test-rows {:name [{:type "string" :op "contains" :value "lic"}]}))
        "filters rows where name contains 'lic'")

    (is (= test-rows
           (query/apply-filters test-rows {:name [{:type "string" :op "contains" :value ""}]}))
        "blank value returns all rows")))

(deftest apply-filters-not-contains-test
  (testing "not-contains filter"
    (is (= [{:id 2 :name "Bob" :age 25 :city "Boston"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 4 :name "Diana" :age 28 :city "Denver"}
            {:id 5 :name "Eve" :age 32 :city ""}]
           (query/apply-filters test-rows {:name [{:type "string" :op "not-contains" :value "lic"}]}))
        "filters rows where name does not contain 'lic'")

    (is (= test-rows
           (query/apply-filters test-rows {:name [{:type "string" :op "not-contains" :value ""}]}))
        "blank value returns all rows")))

(deftest apply-filters-equals-test
  (testing "equals filter"
    (is (= [{:id 2 :name "Bob" :age 25 :city "Boston"}]
           (query/apply-filters test-rows {:name [{:type "string" :op "equals" :value "bob"}]}))
        "filters rows where name equals 'bob' (case-insensitive)")

    (is (= []
           (query/apply-filters test-rows {:name [{:type "string" :op "equals" :value "bobby"}]}))
        "returns empty when no match")))

(deftest apply-filters-not-equals-test
  (testing "not-equals filter"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 4 :name "Diana" :age 28 :city "Denver"}
            {:id 5 :name "Eve" :age 32 :city ""}]
           (query/apply-filters test-rows {:name [{:type "string" :op "not-equals" :value "bob"}]}))
        "filters rows where name does not equal 'bob'")))

(deftest apply-filters-starts-with-test
  (testing "starts-with filter"
    (is (= [{:id 3 :name "Charlie" :age 35 :city "Chicago"}]
           (query/apply-filters test-rows {:name [{:type "string" :op "starts-with" :value "ch"}]}))
        "filters rows where name starts with 'ch'")))

(deftest apply-filters-ends-with-test
  (testing "ends-with filter"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 5 :name "Eve" :age 32 :city ""}]
           (query/apply-filters test-rows {:name [{:type "string" :op "ends-with" :value "e"}]}))
        "filters rows where name ends with 'e'")))

(deftest apply-filters-is-empty-test
  (testing "is-empty filter"
    (is (= [{:id 5 :name "Eve" :age 32 :city ""}]
           (query/apply-filters test-rows {:city [{:type "string" :op "is-empty" :value ""}]}))
        "filters rows where city is empty")))

(deftest apply-filters-is-not-empty-test
  (testing "is-not-empty filter"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 2 :name "Bob" :age 25 :city "Boston"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 4 :name "Diana" :age 28 :city "Denver"}]
           (query/apply-filters test-rows {:city [{:type "string" :op "is-not-empty" :value ""}]}))
        "filters rows where city is not empty")))

(deftest apply-filters-is-any-of-test
  (testing "is-any-of filter with multiple values"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 2 :name "Bob" :age 25 :city "Boston"}]
           (query/apply-filters test-rows {:name [{:type "string" :op "is-any-of" :value ["alice" "bob"]}]}))
        "filters rows where name is any of the specified values"))

  (testing "is-any-of filter with single value"
    (is (= [{:id 3 :name "Charlie" :age 35 :city "Chicago"}]
           (query/apply-filters test-rows {:name [{:type "string" :op "is-any-of" :value ["charlie"]}]}))
        "works with single value in array"))

  (testing "is-any-of filter with no matches"
    (is (= []
           (query/apply-filters test-rows {:name [{:type "string" :op "is-any-of" :value ["xyz" "abc"]}]}))
        "returns empty when no matches")))

(deftest apply-filters-multiple-columns-test
  (testing "multiple column filters (AND logic)"
    (is (= [{:id 3 :name "Charlie" :age 35 :city "Chicago"}]
           (query/apply-filters test-rows {:name [{:type "string" :op "contains" :value "ar"}]
                                           :city [{:type "string" :op "starts-with" :value "ch"}]}))
        "filters with multiple columns use AND logic")))

(deftest apply-filters-case-insensitive-test
  (testing "case insensitivity"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}]
           (query/apply-filters test-rows {:name [{:type "string" :op "contains" :value "ALICE"}]}))
        "filtering is case-insensitive")))

(deftest apply-filters-multiple-same-column-test
  (testing "multiple filters on same column (AND logic)"
    ;; name contains "a" matches: Alice, Charlie, Diana
    ;; name ends-with "e" matches: Alice, Charlie, Eve
    ;; AND logic: Alice, Charlie
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}]
           (query/apply-filters test-rows {:name [{:type "string" :op "contains" :value "a"}
                                                  {:type "string" :op "ends-with" :value "e"}]}))
        "multiple filters on same column use AND logic")))

;; =============================================================================
;; Sort Tests
;; =============================================================================

(deftest sort-data-test
  (testing "sort ascending"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 2 :name "Bob" :age 25 :city "Boston"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 4 :name "Diana" :age 28 :city "Denver"}
            {:id 5 :name "Eve" :age 32 :city ""}]
           (query/sort-data test-rows [{:column "name" :direction "asc"}]))
        "sorts by name ascending"))

  (testing "sort descending"
    (is (= [{:id 5 :name "Eve" :age 32 :city ""}
            {:id 4 :name "Diana" :age 28 :city "Denver"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 2 :name "Bob" :age 25 :city "Boston"}
            {:id 1 :name "Alice" :age 30 :city "New York"}]
           (query/sort-data test-rows [{:column "name" :direction "desc"}]))
        "sorts by name descending"))

  (testing "sort by numeric column"
    (is (= [{:id 2 :name "Bob" :age 25 :city "Boston"}
            {:id 4 :name "Diana" :age 28 :city "Denver"}
            {:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 5 :name "Eve" :age 32 :city ""}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}]
           (query/sort-data test-rows [{:column "age" :direction "asc"}]))
        "sorts by age ascending"))

  (testing "empty sort spec"
    (is (= test-rows
           (query/sort-data test-rows []))
        "returns rows unchanged when sort spec is empty")))

;; =============================================================================
;; Pagination Tests
;; =============================================================================

(deftest paginate-data-test
  (testing "first page"
    (is (= {:rows [{:id 1 :name "Alice" :age 30 :city "New York"}
                   {:id 2 :name "Bob" :age 25 :city "Boston"}]
            :current 0}
           (query/paginate-data test-rows {:size 2 :current 0}))
        "returns first page"))

  (testing "middle page"
    (is (= {:rows [{:id 3 :name "Charlie" :age 35 :city "Chicago"}
                   {:id 4 :name "Diana" :age 28 :city "Denver"}]
            :current 1}
           (query/paginate-data test-rows {:size 2 :current 1}))
        "returns middle page"))

  (testing "last page (partial)"
    (is (= {:rows [{:id 5 :name "Eve" :age 32 :city ""}]
            :current 2}
           (query/paginate-data test-rows {:size 2 :current 2}))
        "returns last page with fewer items"))

  (testing "page beyond range clamps to last page"
    (is (= {:rows [{:id 5 :name "Eve" :age 32 :city ""}]
            :current 2}
           (query/paginate-data test-rows {:size 2 :current 100}))
        "clamps to last page when current is too high"))

  (testing "nil current defaults to last page"
    (is (= {:rows [{:id 5 :name "Eve" :age 32 :city ""}]
            :current 2}
           (query/paginate-data test-rows {:size 2 :current nil}))
        "nil current goes to last page"))

  (testing "empty rows"
    (is (= {:rows [] :current 0}
           (query/paginate-data [] {:size 10 :current 0}))
        "handles empty rows")))

;; =============================================================================
;; Integration Tests (query-fn)
;; =============================================================================

(deftest query-fn-test
  (let [qfn (query/query-fn test-rows)]
    (testing "filter + sort + paginate"
      ;; city starts-with "d" matches only Denver (Diana)
      ;; total-rows = 1, so page 1 clamps to page 0
      (is (= {:rows [{:id 4 :name "Diana" :age 28 :city "Denver"}]
              :total-rows 1
              :page {:size 1 :current 0}}
             (qfn {:filters {:city [{:type "string" :op "starts-with" :value "d"}]}
                   :sort [{:column "name" :direction "asc"}]
                   :page {:size 1 :current 1}}
                  nil))
          "combines filter, sort, and pagination (clamps page when beyond range)"))

    (testing "no filters"
      (is (= {:rows test-rows
              :total-rows 5
              :page {:size 10 :current 0}}
             (qfn {:filters {}
                   :sort []
                   :page {:size 10 :current 0}}
                  nil))
          "returns all rows when no filters"))

    (testing "total-rows reflects filtered count"
      ;; name contains "e" matches: Alice, Charlie, Eve = 3
      (is (= 3
             (:total-rows (qfn {:filters {:name [{:type "string" :op "contains" :value "e"}]}
                                :sort []
                                :page {:size 10 :current 0}}
                               nil)))
          "total-rows is count after filtering, before pagination"))))

(def global-search-test-rows
  [{:id 1 :name "Socrates" :school "Academy" :city "Athens"}
   {:id 2 :name "Plato" :school "Stoa" :city "Athens"}
   {:id 3 :name "Stoa Keeper" :school "Garden" :city "Sparta"}
   {:id 4 :name "Zeno" :school "THE STOA" :city "Athens"}
   {:id 5 :name "Aristotle" :school "Lyceum" :city "Athens"}])

(def global-search-columns
  [{:key :name :global-search? true}
   {:key :school :global-search? true}
   {:key :city}])

(deftest query-fn-global-search-or-across-columns-test
  (testing "global search matches case-insensitively across configured columns with OR semantics"
    (let [qfn (query/query-fn global-search-test-rows)]
      (is (= {:rows [{:id 2 :name "Plato" :school "Stoa" :city "Athens"}
                     {:id 3 :name "Stoa Keeper" :school "Garden" :city "Sparta"}
                     {:id 4 :name "Zeno" :school "THE STOA" :city "Athens"}]
              :total-rows 3
              :page {:size 10 :current 0}}
             (qfn {:columns global-search-columns
                   :search-string "  sToA  "
                   :filters {}
                   :sort [{:column "id" :direction "asc"}]
                   :page {:size 10 :current 0}}
                  nil))))))

(deftest query-fn-global-search-short-input-test
  (testing "trimmed global search input shorter than 2 characters does not narrow results"
    (let [qfn (query/query-fn global-search-test-rows)
          without-search (qfn {:columns global-search-columns
                               :filters {}
                               :sort [{:column "id" :direction "asc"}]
                               :page {:size 10 :current 0}}
                              nil)
          short-search (qfn {:columns global-search-columns
                             :search-string " a "
                             :filters {}
                             :sort [{:column "id" :direction "asc"}]
                             :page {:size 10 :current 0}}
                            nil)]
      (is (= without-search short-search)))))

(deftest query-fn-global-search-composes-with-query-flow-test
  (testing "global search composes with filters, sorting, pagination, and filtered total count"
    (let [qfn (query/query-fn global-search-test-rows)]
      (is (= {:rows [{:id 2 :name "Plato" :school "Stoa" :city "Athens"}
                     {:id 4 :name "Zeno" :school "THE STOA" :city "Athens"}]
              :total-rows 2
              :page {:size 2 :current 0}}
             (qfn {:columns global-search-columns
                   :search-string "stoa"
                   :filters {:city [{:type "string" :op "equals" :value "athens"}]}
                   :sort [{:column "name" :direction "asc"}]
                   :page {:size 2 :current 0}}
                  nil))))))

;; =============================================================================
;; Boolean Filter Tests
;; =============================================================================

(def bool-test-rows
  [{:id 1 :name "Alice" :active true}
   {:id 2 :name "Bob" :active false}
   {:id 3 :name "Charlie" :active true}
   {:id 4 :name "Diana" :active nil}
   {:id 5 :name "Eve" :active false}])

(deftest apply-filters-boolean-is-test
  (testing "boolean is true"
    (is (= [{:id 1 :name "Alice" :active true}
            {:id 3 :name "Charlie" :active true}]
           (query/apply-filters bool-test-rows {:active [{:type "boolean" :op "is" :value "true"}]}))
        "filters rows where active is true"))

  (testing "boolean is false"
    (is (= [{:id 2 :name "Bob" :active false}
            {:id 5 :name "Eve" :active false}]
           (query/apply-filters bool-test-rows {:active [{:type "boolean" :op "is" :value "false"}]}))
        "filters rows where active is false")))

(deftest apply-filters-boolean-is-not-test
  (testing "boolean is-not true"
    (is (= [{:id 2 :name "Bob" :active false}
            {:id 4 :name "Diana" :active nil}
            {:id 5 :name "Eve" :active false}]
           (query/apply-filters bool-test-rows {:active [{:type "boolean" :op "is-not" :value "true"}]}))
        "filters rows where active is not true (includes nil)"))

  (testing "boolean is-not false"
    (is (= [{:id 1 :name "Alice" :active true}
            {:id 3 :name "Charlie" :active true}
            {:id 4 :name "Diana" :active nil}]
           (query/apply-filters bool-test-rows {:active [{:type "boolean" :op "is-not" :value "false"}]}))
        "filters rows where active is not false (includes nil)")))

(deftest apply-filters-boolean-is-empty-test
  (testing "boolean is-empty"
    (is (= [{:id 4 :name "Diana" :active nil}]
           (query/apply-filters bool-test-rows {:active [{:type "boolean" :op "is-empty" :value ""}]}))
        "filters rows where active is nil")))

(deftest apply-filters-boolean-is-not-empty-test
  (testing "boolean is-not-empty"
    (is (= [{:id 1 :name "Alice" :active true}
            {:id 2 :name "Bob" :active false}
            {:id 3 :name "Charlie" :active true}
            {:id 5 :name "Eve" :active false}]
           (query/apply-filters bool-test-rows {:active [{:type "boolean" :op "is-not-empty" :value ""}]}))
        "filters rows where active is not nil")))

;; =============================================================================
;; Date Filter Tests
;; =============================================================================

(def date-test-rows
  [{:id 1 :name "Alice" :created "2024-01-15"}
   {:id 2 :name "Bob" :created "2024-02-20"}
   {:id 3 :name "Charlie" :created "2024-01-15"}
   {:id 4 :name "Diana" :created "2024-03-10"}
   {:id 5 :name "Eve" :created nil}])

(deftest apply-filters-date-is-test
  (testing "date is exact match"
    (is (= [{:id 1 :name "Alice" :created "2024-01-15"}
            {:id 3 :name "Charlie" :created "2024-01-15"}]
           (query/apply-filters date-test-rows {:created [{:type "date" :op "is" :value "2024-01-15"}]}))
        "filters rows where created equals exact date")))

(deftest apply-filters-date-is-not-test
  (testing "date is-not"
    (is (= [{:id 2 :name "Bob" :created "2024-02-20"}
            {:id 4 :name "Diana" :created "2024-03-10"}
            {:id 5 :name "Eve" :created nil}]
           (query/apply-filters date-test-rows {:created [{:type "date" :op "is-not" :value "2024-01-15"}]}))
        "filters rows where created does not equal date")))

(deftest apply-filters-date-after-test
  (testing "date after"
    (is (= [{:id 2 :name "Bob" :created "2024-02-20"}
            {:id 4 :name "Diana" :created "2024-03-10"}]
           (query/apply-filters date-test-rows {:created [{:type "date" :op "after" :value "2024-01-15"}]}))
        "filters rows where created is after date (exclusive)")))

(deftest apply-filters-date-on-or-after-test
  (testing "date on-or-after"
    (is (= [{:id 1 :name "Alice" :created "2024-01-15"}
            {:id 2 :name "Bob" :created "2024-02-20"}
            {:id 3 :name "Charlie" :created "2024-01-15"}
            {:id 4 :name "Diana" :created "2024-03-10"}]
           (query/apply-filters date-test-rows {:created [{:type "date" :op "on-or-after" :value "2024-01-15"}]}))
        "filters rows where created is on or after date (inclusive)")))

(deftest apply-filters-date-before-test
  (testing "date before"
    (is (= [{:id 1 :name "Alice" :created "2024-01-15"}
            {:id 3 :name "Charlie" :created "2024-01-15"}]
           (query/apply-filters date-test-rows {:created [{:type "date" :op "before" :value "2024-02-20"}]}))
        "filters rows where created is before date (exclusive)")))

(deftest apply-filters-date-on-or-before-test
  (testing "date on-or-before"
    (is (= [{:id 1 :name "Alice" :created "2024-01-15"}
            {:id 2 :name "Bob" :created "2024-02-20"}
            {:id 3 :name "Charlie" :created "2024-01-15"}]
           (query/apply-filters date-test-rows {:created [{:type "date" :op "on-or-before" :value "2024-02-20"}]}))
        "filters rows where created is on or before date (inclusive)")))

(deftest apply-filters-date-is-empty-test
  (testing "date is-empty"
    (is (= [{:id 5 :name "Eve" :created nil}]
           (query/apply-filters date-test-rows {:created [{:type "date" :op "is-empty" :value ""}]}))
        "filters rows where created is nil")))

(deftest apply-filters-date-is-not-empty-test
  (testing "date is-not-empty"
    (is (= [{:id 1 :name "Alice" :created "2024-01-15"}
            {:id 2 :name "Bob" :created "2024-02-20"}
            {:id 3 :name "Charlie" :created "2024-01-15"}
            {:id 4 :name "Diana" :created "2024-03-10"}]
           (query/apply-filters date-test-rows {:created [{:type "date" :op "is-not-empty" :value ""}]}))
        "filters rows where created is not nil")))

;; =============================================================================
;; Enum Filter Tests
;; =============================================================================

(def enum-test-rows
  [{:id 1 :name "Alice" :status "active"}
   {:id 2 :name "Bob" :status "pending"}
   {:id 3 :name "Charlie" :status "active"}
   {:id 4 :name "Diana" :status "inactive"}
   {:id 5 :name "Eve" :status nil}])

(deftest apply-filters-enum-is-test
  (testing "enum is exact match"
    (is (= [{:id 1 :name "Alice" :status "active"}
            {:id 3 :name "Charlie" :status "active"}]
           (query/apply-filters enum-test-rows {:status [{:type "enum" :op "is" :value "active"}]}))
        "filters rows where status equals value")))

(deftest apply-filters-enum-is-not-test
  (testing "enum is-not"
    (is (= [{:id 2 :name "Bob" :status "pending"}
            {:id 4 :name "Diana" :status "inactive"}
            {:id 5 :name "Eve" :status nil}]
           (query/apply-filters enum-test-rows {:status [{:type "enum" :op "is-not" :value "active"}]}))
        "filters rows where status does not equal value")))

(deftest apply-filters-enum-is-any-of-test
  (testing "enum is-any-of"
    (is (= [{:id 1 :name "Alice" :status "active"}
            {:id 2 :name "Bob" :status "pending"}
            {:id 3 :name "Charlie" :status "active"}]
           (query/apply-filters enum-test-rows {:status [{:type "enum" :op "is-any-of" :value ["active" "pending"]}]}))
        "filters rows where status is any of the values")))

(deftest apply-filters-enum-is-empty-test
  (testing "enum is-empty"
    (is (= [{:id 5 :name "Eve" :status nil}]
           (query/apply-filters enum-test-rows {:status [{:type "enum" :op "is-empty" :value ""}]}))
        "filters rows where status is nil")))

(deftest apply-filters-enum-is-not-empty-test
  (testing "enum is-not-empty"
    (is (= [{:id 1 :name "Alice" :status "active"}
            {:id 2 :name "Bob" :status "pending"}
            {:id 3 :name "Charlie" :status "active"}
            {:id 4 :name "Diana" :status "inactive"}]
           (query/apply-filters enum-test-rows {:status [{:type "enum" :op "is-not-empty" :value ""}]}))
        "filters rows where status is not nil")))

;; =============================================================================
;; Number Filter Tests
;; =============================================================================

(def number-test-rows
  [{:id 1 :name "Alice" :age 30}
   {:id 2 :name "Bob" :age 25}
   {:id 3 :name "Charlie" :age 35}
   {:id 4 :name "Diana" :age 30}
   {:id 5 :name "Eve" :age nil}])

(deftest apply-filters-number-equals-test
  (testing "number equals"
    (is (= [{:id 1 :name "Alice" :age 30}
            {:id 4 :name "Diana" :age 30}]
           (query/apply-filters number-test-rows {:age [{:type "number" :op "equals" :value "30"}]}))
        "filters rows where age equals 30")))

(deftest apply-filters-number-not-equals-test
  (testing "number not-equals"
    (is (= [{:id 2 :name "Bob" :age 25}
            {:id 3 :name "Charlie" :age 35}
            {:id 5 :name "Eve" :age nil}]
           (query/apply-filters number-test-rows {:age [{:type "number" :op "not-equals" :value "30"}]}))
        "filters rows where age does not equal 30")))

(deftest apply-filters-number-greater-than-test
  (testing "number greater-than"
    (is (= [{:id 1 :name "Alice" :age 30}
            {:id 3 :name "Charlie" :age 35}
            {:id 4 :name "Diana" :age 30}]
           (query/apply-filters number-test-rows {:age [{:type "number" :op "greater-than" :value "25"}]}))
        "filters rows where age > 25")))

(deftest apply-filters-number-greater-than-or-equal-test
  (testing "number greater-than-or-equal"
    (is (= [{:id 1 :name "Alice" :age 30}
            {:id 3 :name "Charlie" :age 35}
            {:id 4 :name "Diana" :age 30}]
           (query/apply-filters number-test-rows {:age [{:type "number" :op "greater-than-or-equal" :value "30"}]}))
        "filters rows where age >= 30")))

(deftest apply-filters-number-less-than-test
  (testing "number less-than"
    (is (= [{:id 2 :name "Bob" :age 25}]
           (query/apply-filters number-test-rows {:age [{:type "number" :op "less-than" :value "30"}]}))
        "filters rows where age < 30")))

(deftest apply-filters-number-less-than-or-equal-test
  (testing "number less-than-or-equal"
    (is (= [{:id 1 :name "Alice" :age 30}
            {:id 2 :name "Bob" :age 25}
            {:id 4 :name "Diana" :age 30}]
           (query/apply-filters number-test-rows {:age [{:type "number" :op "less-than-or-equal" :value "30"}]}))
        "filters rows where age <= 30")))

(deftest apply-filters-number-is-empty-test
  (testing "number is-empty"
    (is (= [{:id 5 :name "Eve" :age nil}]
           (query/apply-filters number-test-rows {:age [{:type "number" :op "is-empty" :value ""}]}))
        "filters rows where age is nil")))

(deftest apply-filters-number-is-not-empty-test
  (testing "number is-not-empty"
    (is (= [{:id 1 :name "Alice" :age 30}
            {:id 2 :name "Bob" :age 25}
            {:id 3 :name "Charlie" :age 35}
            {:id 4 :name "Diana" :age 30}]
           (query/apply-filters number-test-rows {:age [{:type "number" :op "is-not-empty" :value ""}]}))
        "filters rows where age is not nil")))

(deftest apply-filters-number-with-negative-values-test
  (testing "number comparisons with negative values"
    (let [rows [{:id 1 :name "Socrates" :century -5}
                {:id 2 :name "Plato" :century -4}
                {:id 3 :name "Seneca" :century 1}
                {:id 4 :name "Kant" :century 18}]]
      (is (= [{:id 1 :name "Socrates" :century -5}
              {:id 2 :name "Plato" :century -4}]
             (query/apply-filters rows {:century [{:type "number" :op "less-than" :value "0"}]}))
          "filters BC centuries (negative values)")
      (is (= [{:id 3 :name "Seneca" :century 1}
              {:id 4 :name "Kant" :century 18}]
             (query/apply-filters rows {:century [{:type "number" :op "greater-than" :value "0"}]}))
          "filters AD centuries (positive values)"))))

;; =============================================================================
;; Grouped Query Tests
;; =============================================================================

(def sample-rows
  [{:id 1 :name "A1" :school "Academy" :century 2}
   {:id 2 :name "A2" :school "Academy" :century 4}
   {:id 3 :name "B1" :school "Lyceum" :century 1}
   {:id 4 :name "C1" :school "Stoa" :century 3}
   {:id 5 :name "C2" :school "Stoa" :century 5}])

(defn- expected-group-order
  [rows group-key]
  (->> rows (map group-key) distinct sort vec))

(defn- group-order
  [rows group-key]
  (->> rows (map group-key) distinct vec))

(deftest grouped-pagination-uses-group-count-test
  (testing "grouped pagination counts groups and keeps groups intact"
    (let [qfn (query/query-fn sample-rows)
          page-size 2
          result (qfn {:filters {}
                       :sort [{:column "school" :direction "asc"}]
                       :group-by [:school]
                       :page {:size page-size :current 0}}
                      nil)
          groups (group-state/group-rows (:rows result) [:school])
          expected-order (expected-group-order sample-rows :school)
          expected-groups (take page-size expected-order)
          original-counts (frequencies (map :school sample-rows))]
      (is (= (count expected-order) (:total-rows result))
          "Expected total-rows to reflect group count when grouped")
      (is (= page-size (count groups))
          "Expected page size to limit group count")
      (is (= (set expected-groups) (set (map :group-value groups)))
          "Expected page to include only the first group values")
      (doseq [{:keys [group-value count]} groups]
        (is (= (get original-counts group-value) count)
            "Expected full group row counts on the page")))))

(deftest grouped-sort-and-filter-test
  (testing "non-grouped sorting does not reorder groups"
    (let [qfn (query/query-fn sample-rows)
          result (qfn {:filters {}
                       :sort [{:column "century" :direction "asc"}]
                       :group-by [:school]
                       :page {:size 10 :current 0}}
                      nil)
          expected-order (expected-group-order sample-rows :school)]
      (is (= expected-order (group-order (:rows result) :school))
          "Expected group order to stay aligned with grouped column")))

  (testing "non-grouped sorting does not reorder rows within groups"
    (let [qfn (query/query-fn sample-rows)
          result (qfn {:filters {}
                       :sort [{:column "century" :direction "desc"}]
                       :group-by [:school]
                       :page {:size 10 :current 0}}
                      nil)
          academy-rows (->> (:rows result)
                            (filter #(= "Academy" (:school %)))
                            (map :name)
                            vec)]
      (is (= ["A1" "A2"] academy-rows)
          "Expected non-grouped sort to leave row order intact within groups")))

  (testing "filtering by grouped column counts groups, not rows"
    (let [qfn (query/query-fn sample-rows)
          result (qfn {:filters {:school [{:type "enum" :op "is" :value "Academy"}]}
                       :sort []
                       :group-by [:school]
                       :page {:size 10 :current 0}}
                      nil)]
      (is (= 1 (:total-rows result))
          "Expected total-rows to equal group count after filtering"))))
