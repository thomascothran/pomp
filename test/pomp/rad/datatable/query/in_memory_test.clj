(ns pomp.rad.datatable.query.in-memory-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.query.in-memory :as query]))

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
           (query/apply-filters test-rows {:name [{:type "text" :op "contains" :value "lic"}]}))
        "filters rows where name contains 'lic'")

    (is (= test-rows
           (query/apply-filters test-rows {:name [{:type "text" :op "contains" :value ""}]}))
        "blank value returns all rows")))

(deftest apply-filters-not-contains-test
  (testing "not-contains filter"
    (is (= [{:id 2 :name "Bob" :age 25 :city "Boston"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 4 :name "Diana" :age 28 :city "Denver"}
            {:id 5 :name "Eve" :age 32 :city ""}]
           (query/apply-filters test-rows {:name [{:type "text" :op "not-contains" :value "lic"}]}))
        "filters rows where name does not contain 'lic'")

    (is (= test-rows
           (query/apply-filters test-rows {:name [{:type "text" :op "not-contains" :value ""}]}))
        "blank value returns all rows")))

(deftest apply-filters-equals-test
  (testing "equals filter"
    (is (= [{:id 2 :name "Bob" :age 25 :city "Boston"}]
           (query/apply-filters test-rows {:name [{:type "text" :op "equals" :value "bob"}]}))
        "filters rows where name equals 'bob' (case-insensitive)")

    (is (= []
           (query/apply-filters test-rows {:name [{:type "text" :op "equals" :value "bobby"}]}))
        "returns empty when no match")))

(deftest apply-filters-not-equals-test
  (testing "not-equals filter"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 4 :name "Diana" :age 28 :city "Denver"}
            {:id 5 :name "Eve" :age 32 :city ""}]
           (query/apply-filters test-rows {:name [{:type "text" :op "not-equals" :value "bob"}]}))
        "filters rows where name does not equal 'bob'")))

(deftest apply-filters-starts-with-test
  (testing "starts-with filter"
    (is (= [{:id 3 :name "Charlie" :age 35 :city "Chicago"}]
           (query/apply-filters test-rows {:name [{:type "text" :op "starts-with" :value "ch"}]}))
        "filters rows where name starts with 'ch'")))

(deftest apply-filters-ends-with-test
  (testing "ends-with filter"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 5 :name "Eve" :age 32 :city ""}]
           (query/apply-filters test-rows {:name [{:type "text" :op "ends-with" :value "e"}]}))
        "filters rows where name ends with 'e'")))

(deftest apply-filters-is-empty-test
  (testing "is-empty filter"
    (is (= [{:id 5 :name "Eve" :age 32 :city ""}]
           (query/apply-filters test-rows {:city [{:type "text" :op "is-empty" :value ""}]}))
        "filters rows where city is empty")))

(deftest apply-filters-is-not-empty-test
  (testing "is-not-empty filter"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 2 :name "Bob" :age 25 :city "Boston"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 4 :name "Diana" :age 28 :city "Denver"}]
           (query/apply-filters test-rows {:city [{:type "text" :op "is-not-empty" :value ""}]}))
        "filters rows where city is not empty")))

(deftest apply-filters-is-any-of-test
  (testing "is-any-of filter with multiple values"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 2 :name "Bob" :age 25 :city "Boston"}]
           (query/apply-filters test-rows {:name [{:type "text" :op "is-any-of" :value ["alice" "bob"]}]}))
        "filters rows where name is any of the specified values"))

  (testing "is-any-of filter with single value"
    (is (= [{:id 3 :name "Charlie" :age 35 :city "Chicago"}]
           (query/apply-filters test-rows {:name [{:type "text" :op "is-any-of" :value ["charlie"]}]}))
        "works with single value in array"))

  (testing "is-any-of filter with no matches"
    (is (= []
           (query/apply-filters test-rows {:name [{:type "text" :op "is-any-of" :value ["xyz" "abc"]}]}))
        "returns empty when no matches")))

(deftest apply-filters-multiple-columns-test
  (testing "multiple column filters (AND logic)"
    (is (= [{:id 3 :name "Charlie" :age 35 :city "Chicago"}]
           (query/apply-filters test-rows {:name [{:type "text" :op "contains" :value "ar"}]
                                           :city [{:type "text" :op "starts-with" :value "ch"}]}))
        "filters with multiple columns use AND logic")))

(deftest apply-filters-case-insensitive-test
  (testing "case insensitivity"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}]
           (query/apply-filters test-rows {:name [{:type "text" :op "contains" :value "ALICE"}]}))
        "filtering is case-insensitive")))

(deftest apply-filters-multiple-same-column-test
  (testing "multiple filters on same column (AND logic)"
    ;; name contains "a" matches: Alice, Charlie, Diana
    ;; name ends-with "e" matches: Alice, Charlie, Eve
    ;; AND logic: Alice, Charlie
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}]
           (query/apply-filters test-rows {:name [{:type "text" :op "contains" :value "a"}
                                                  {:type "text" :op "ends-with" :value "e"}]}))
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
             (qfn {:filters {:city [{:type "text" :op "starts-with" :value "d"}]}
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
             (:total-rows (qfn {:filters {:name [{:type "text" :op "contains" :value "e"}]}
                                :sort []
                                :page {:size 10 :current 0}}
                               nil)))
          "total-rows is count after filtering, before pagination"))))

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
